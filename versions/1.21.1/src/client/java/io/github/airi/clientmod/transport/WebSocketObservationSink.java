package io.github.airi.clientmod.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletionStage;

import io.github.airi.clientmod.AiriUserClientMod;
import io.github.airi.clientmod.core.trace.ObservationSample;

public final class WebSocketObservationSink {
	private static final String HUB_INGRESS_WS_URI_PROPERTY = "airi.hub.ingress.ws.uri";
	private static final String LEGACY_WS_URI_PROPERTY = "airi.transport.ws.uri";
	private static final String DEFAULT_WS_URI = "ws://127.0.0.1:8787/ws";
	// Keep a bounded backlog so reconnects and slow sends do not create avoidable trace gaps.
	private static final int MAX_PENDING_MESSAGES = 128;
	private static final long CONNECT_ATTEMPT_GUARD_MILLIS = 1000L;
	private static final long SESSION_START_SEQUENCE = 1L;

	public interface SessionReplaySupplier {
		SessionReplay getActiveSession();
	}

	public record SessionReplay(
		String sessionId,
		long startedAtMillis
	) {
	}

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final Deque<String> pendingMessages = new ArrayDeque<>();
	private final SessionReplaySupplier sessionReplaySupplier;
	private final TransportStatusStore statusStore;
	private final TransportTelemetry telemetry;
	private final URI endpointUri;

	private WebSocket webSocket;
	private boolean connectInFlight;
	private boolean sendInFlight;
	private String inFlightMessage;
	private long connectAttemptStartedAtMillis;
	private long lastConnectAttemptAtMillis;
	private boolean replayActiveSessionOnNextOpen;

	public WebSocketObservationSink(TransportStatusStore statusStore) {
		this(statusStore, TransportTelemetry.NOOP, () -> null);
	}

	public WebSocketObservationSink(TransportStatusStore statusStore, TransportTelemetry telemetry) {
		this(statusStore, telemetry, () -> null);
	}

	public WebSocketObservationSink(
		TransportStatusStore statusStore,
		TransportTelemetry telemetry,
		SessionReplaySupplier sessionReplaySupplier
	) {
		this.statusStore = statusStore;
		this.telemetry = telemetry;
		this.sessionReplaySupplier = sessionReplaySupplier == null ? () -> null : sessionReplaySupplier;
		this.endpointUri = resolveEndpointUri();
		this.statusStore.setEndpoint(endpointUri.toString());
	}

	public void start() {
		connectIfNeeded();
	}

	public void emitSessionStart(String sessionId, long sequence, long capturedAtMillis) {
		enqueueSerializedTrace(serializeSessionControlFrame("trace.session.start", sessionId, sequence, capturedAtMillis));
	}

	public void emitSessionEnd(String sessionId, long sequence, long capturedAtMillis) {
		enqueueSerializedTrace(serializeSessionControlFrame("trace.session.end", sessionId, sequence, capturedAtMillis));
	}

	public void emitObservationSample(String sessionId, ObservationSample sample) {
		enqueueSerializedTrace(serializeObservationSample(sessionId, sample));
	}

	private void enqueueSerializedTrace(String message) {
		synchronized (this) {
			enqueueMessageLocked(message);
		}

		connectIfNeeded();
		drainQueue();
	}

	private void drainQueue() {
		WebSocket socketToUse;
		String message;
		long sendStartedAtMillis = System.currentTimeMillis();

		synchronized (this) {
			if (webSocket == null || sendInFlight || pendingMessages.isEmpty()) {
				return;
			}

			socketToUse = webSocket;
			message = pendingMessages.removeFirst();
			inFlightMessage = message;
			sendInFlight = true;
		}

		try {
			socketToUse.sendText(message, true).whenComplete((ignored, error) -> {
				if (error != null) {
					handleSendFailure(socketToUse, error);
					return;
				}

				long latencyMillis = Math.max(0L, System.currentTimeMillis() - sendStartedAtMillis);
				synchronized (this) {
					sendInFlight = false;
					inFlightMessage = null;
				}
				statusStore.recordSendSuccess(latencyMillis);
				telemetry.onSendSucceeded(latencyMillis);
				drainQueue();
			});
		} catch (RuntimeException error) {
			handleSendFailure(socketToUse, error);
		}
	}

	private void enqueueMessageLocked(String message) {
		if (pendingMessages.size() >= MAX_PENDING_MESSAGES) {
			pendingMessages.removeFirst();
			statusStore.recordSendSkipped("hub ingress backlog full; dropped oldest queued sample");
		}

		pendingMessages.addLast(message);
	}

	private void enqueuePriorityMessageLocked(String message) {
		if (pendingMessages.size() >= MAX_PENDING_MESSAGES) {
			pendingMessages.removeFirst();
			statusStore.recordSendSkipped("hub ingress backlog full; dropped oldest queued sample");
		}

		pendingMessages.addFirst(message);
	}

	private boolean hasQueuedMessageLocked(String message) {
		return message.equals(inFlightMessage) || pendingMessages.contains(message);
	}

	private boolean restoreInFlightMessageLocked() {
		if (inFlightMessage == null) {
			return !pendingMessages.isEmpty();
		}

		if (pendingMessages.size() >= MAX_PENDING_MESSAGES) {
			pendingMessages.removeFirst();
			statusStore.recordSendSkipped("hub ingress backlog full; dropped oldest queued sample");
		}

		pendingMessages.addFirst(inFlightMessage);
		inFlightMessage = null;
		return true;
	}

	private void tryReconnectAndDrain() {
		connectIfNeeded();
		drainQueue();
	}

	private void handleSendFailure(WebSocket failingSocket, Throwable error) {
		boolean shouldHandle;
		boolean shouldAbort = false;
		boolean shouldReconnect;

		synchronized (this) {
			shouldHandle = sendInFlight || webSocket == failingSocket;
			if (!shouldHandle) {
				return;
			}

			sendInFlight = false;
			shouldReconnect = restoreInFlightMessageLocked();
			if (webSocket == failingSocket) {
				webSocket = null;
				replayActiveSessionOnNextOpen = true;
				shouldAbort = true;
				shouldReconnect = true;
			}
		}

		if (shouldAbort) {
			try {
				failingSocket.abort();
			} catch (RuntimeException ignored) {
			}
		}

		String message = summarize(error);
		statusStore.recordSendFailure("send failed: " + message);
		TransportStateTransition transition = statusStore.markError("send failed: " + message);
		telemetry.onStateChanged(transition);
		telemetry.onConnectionFailure("send", error, -1L);
		AiriUserClientMod.LOGGER.warn("Hub ingress websocket send failed: {}", message);

		if (shouldReconnect) {
			tryReconnectAndDrain();
		}
	}

	private void handleOpen(WebSocket socket) {
		long connectDurationMillis;
		boolean shouldReplayActiveSession;

		synchronized (this) {
			connectDurationMillis = finishConnectAttemptLocked();
			webSocket = socket;
			connectInFlight = false;
			sendInFlight = false;
			shouldReplayActiveSession = replayActiveSessionOnNextOpen;
			replayActiveSessionOnNextOpen = false;
		}

		if (shouldReplayActiveSession) {
			prependActiveSessionReplay(socket);
		}

		TransportStateTransition transition = statusStore.markOpen();
		telemetry.onStateChanged(transition);
		telemetry.onConnectionOpened(connectDurationMillis);
		AiriUserClientMod.LOGGER.info("Hub ingress websocket connected to {}", endpointUri);
		drainQueue();
	}

	private void handleClosed(WebSocket socket, int statusCode, String reason) {
		TransportStateTransition transition;
		String closeReason = summarizeClose(statusCode, reason);
		boolean wasSending;
		boolean shouldReconnect;

		synchronized (this) {
			if (webSocket != socket) {
				return;
			}

			wasSending = sendInFlight;
			webSocket = null;
			connectInFlight = false;
			sendInFlight = false;
			replayActiveSessionOnNextOpen = true;
			shouldReconnect = restoreInFlightMessageLocked();
			transition = statusCode == WebSocket.NORMAL_CLOSURE
				? statusStore.markDisconnected(closeReason)
				: statusStore.markError(closeReason);
		}

		if (wasSending) {
			statusStore.recordSendFailure("send failed: " + closeReason);
			telemetry.onConnectionFailure("send", null, -1L);
		}
		telemetry.onStateChanged(transition);
		telemetry.onConnectionClosed(statusCode);
		if (statusCode == WebSocket.NORMAL_CLOSURE) {
			AiriUserClientMod.LOGGER.info("Hub ingress websocket closed: {}", closeReason);
		} else {
			AiriUserClientMod.LOGGER.warn("Hub ingress websocket closed: {}", closeReason);
		}

		if (shouldReconnect) {
			tryReconnectAndDrain();
		}
	}

	private void handleSocketError(WebSocket socket, Throwable error) {
		TransportStateTransition transition;
		boolean wasSending;
		boolean shouldReconnect;

		synchronized (this) {
			if (webSocket != socket) {
				return;
			}

			wasSending = sendInFlight;
			webSocket = null;
			connectInFlight = false;
			sendInFlight = false;
			replayActiveSessionOnNextOpen = true;
			shouldReconnect = restoreInFlightMessageLocked();
			transition = statusStore.markError("socket error: " + summarize(error));
		}

		if (wasSending) {
			statusStore.recordSendFailure("send failed: " + summarize(error));
			telemetry.onConnectionFailure("send", error, -1L);
		}
		telemetry.onStateChanged(transition);
		AiriUserClientMod.LOGGER.warn("Hub ingress websocket error: {}", summarize(error));

		if (shouldReconnect) {
			tryReconnectAndDrain();
		}
	}

	private void connectIfNeeded() {
		long now = System.currentTimeMillis();
		boolean shouldConnect = false;

		synchronized (this) {
			if (webSocket != null || connectInFlight) {
				return;
			}
			if (lastConnectAttemptAtMillis != 0L && now - lastConnectAttemptAtMillis < CONNECT_ATTEMPT_GUARD_MILLIS) {
				return;
			}

			connectInFlight = true;
			connectAttemptStartedAtMillis = now;
			lastConnectAttemptAtMillis = now;
			shouldConnect = true;
		}

		if (!shouldConnect) {
			return;
		}

		TransportStateTransition transition = statusStore.markConnecting();
		telemetry.onConnectAttemptStarted();
		telemetry.onStateChanged(transition);

		httpClient.newWebSocketBuilder()
			.buildAsync(endpointUri, new Listener())
			.whenComplete((socket, error) -> {
				if (error != null) {
					handleConnectFailure(error);
				}
			});
	}

	private void handleConnectFailure(Throwable error) {
		long connectDurationMillis;

		synchronized (this) {
			connectDurationMillis = finishConnectAttemptLocked();
			connectInFlight = false;
		}

		String message = summarize(error);
		TransportStateTransition transition = statusStore.markError("connect failed: " + message);
		telemetry.onStateChanged(transition);
		telemetry.onConnectionFailure("connect", error, connectDurationMillis);
		AiriUserClientMod.LOGGER.warn("Hub ingress websocket connect failed: {}", message);
	}

	private static URI resolveEndpointUri() {
		URI configuredEndpoint = resolveConfiguredEndpoint(HUB_INGRESS_WS_URI_PROPERTY);
		if (configuredEndpoint != null) {
			return configuredEndpoint;
		}

		configuredEndpoint = resolveConfiguredEndpoint(LEGACY_WS_URI_PROPERTY);
		if (configuredEndpoint != null) {
			return configuredEndpoint;
		}

		return URI.create(DEFAULT_WS_URI);
	}

	private static URI resolveConfiguredEndpoint(String propertyName) {
		String configuredUri = System.getProperty(propertyName);
		if (configuredUri == null || configuredUri.isBlank()) {
			return null;
		}

		try {
			return URI.create(configuredUri.trim());
		} catch (IllegalArgumentException exception) {
			AiriUserClientMod.LOGGER.warn(
				"Invalid hub ingress websocket URI '{}' from -D{}; ignoring",
				configuredUri,
				propertyName
			);
			return null;
		}
	}

	private static String summarize(Throwable error) {
		if (error == null) {
			return "unknown";
		}

		String message = error.getMessage();
		if (message == null || message.isBlank()) {
			return error.getClass().getSimpleName();
		}
		return error.getClass().getSimpleName() + ": " + message;
	}

	private static String summarizeClose(int statusCode, String reason) {
		if (reason == null || reason.isBlank()) {
			return "closed (" + statusCode + ")";
		}
		return "closed (" + statusCode + "): " + reason;
	}

	private void prependActiveSessionReplay(WebSocket socket) {
		SessionReplay sessionReplay = sessionReplaySupplier.getActiveSession();
		if (sessionReplay == null) {
			return;
		}

		String replayMessage = serializeSessionControlFrame(
			"trace.session.start",
			sessionReplay.sessionId(),
			SESSION_START_SEQUENCE,
			sessionReplay.startedAtMillis()
		);
		boolean replayQueued = false;

		synchronized (this) {
			if (webSocket != socket || hasQueuedMessageLocked(replayMessage)) {
				return;
			}

			enqueuePriorityMessageLocked(replayMessage);
			replayQueued = true;
		}

		if (replayQueued) {
			AiriUserClientMod.LOGGER.info(
				"Replayed active world session start for websocket reconnect: {}",
				sessionReplay.sessionId()
			);
		}
	}

	private String serializeSessionControlFrame(String kind, String sessionId, long sequence, long capturedAtMillis) {
		StringBuilder json = new StringBuilder(128);
		json.append('{');
		json.append("\"v\":1,");
		json.append("\"kind\":\"").append(kind).append("\",");
		json.append("\"sessionId\":\"").append(escapeJson(sessionId)).append("\",");
		json.append("\"seq\":").append(sequence).append(',');
		json.append("\"capturedAtMillis\":").append(capturedAtMillis);
		json.append('}');
		return json.toString();
	}

	private String serializeObservationSample(String sessionId, ObservationSample sample) {
		StringBuilder json = new StringBuilder(320);
		json.append('{');
		json.append("\"v\":1,");
		json.append("\"kind\":\"observation.sample\",");
		json.append("\"sessionId\":\"").append(escapeJson(sessionId)).append("\",");
		json.append("\"seq\":").append(sample.sequence()).append(',');
		json.append("\"capturedAtMillis\":").append(sample.capturedAtMillis()).append(',');
		json.append("\"payload\":{");
		json.append("\"worldTick\":").append(sample.worldTick()).append(',');
		json.append("\"fps\":").append(sample.fps()).append(',');
		json.append("\"dimensionKey\":\"").append(escapeJson(sample.dimensionKey())).append("\",");
		json.append("\"x\":").append(sample.x()).append(',');
		json.append("\"y\":").append(sample.y()).append(',');
		json.append("\"z\":").append(sample.z()).append(',');
		json.append("\"vx\":").append(sample.vx()).append(',');
		json.append("\"vy\":").append(sample.vy()).append(',');
		json.append("\"vz\":").append(sample.vz()).append(',');
		json.append("\"targetDescription\":\"").append(escapeJson(sample.targetDescription())).append('"');
		json.append("}}");
		return json.toString();
	}

	private static String escapeJson(String value) {
		StringBuilder escaped = new StringBuilder(value.length() + 8);

		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '\\' -> escaped.append("\\\\");
				case '"' -> escaped.append("\\\"");
				case '\n' -> escaped.append("\\n");
				case '\r' -> escaped.append("\\r");
				case '\t' -> escaped.append("\\t");
				default -> escaped.append(character);
			}
		}

		return escaped.toString();
	}

	private long finishConnectAttemptLocked() {
		if (connectAttemptStartedAtMillis == 0L) {
			return -1L;
		}

		long durationMillis = Math.max(0L, System.currentTimeMillis() - connectAttemptStartedAtMillis);
		connectAttemptStartedAtMillis = 0L;
		return durationMillis;
	}

	private final class Listener implements WebSocket.Listener {
		@Override
		public void onOpen(WebSocket webSocket) {
			webSocket.request(1);
			handleOpen(webSocket);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			handleClosed(webSocket, statusCode, reason);
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			handleSocketError(webSocket, error);
		}
	}
}

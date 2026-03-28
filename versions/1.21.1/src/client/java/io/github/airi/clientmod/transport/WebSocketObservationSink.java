package io.github.airi.clientmod.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

import io.github.airi.clientmod.AiriUserClientMod;
import io.github.airi.clientmod.core.trace.ObservationEmitter;
import io.github.airi.clientmod.core.trace.TraceEvent;

public final class WebSocketObservationSink implements ObservationEmitter {
	private static final String HUB_INGRESS_WS_URI_PROPERTY = "airi.hub.ingress.ws.uri";
	private static final String LEGACY_WS_URI_PROPERTY = "airi.transport.ws.uri";
	private static final String DEFAULT_WS_URI = "ws://127.0.0.1:8787/ws";
	// Keep a bounded backlog so reconnects and slow sends do not create avoidable trace gaps.
	private static final int MAX_PENDING_FRAMES = 128;
	private static final long CONNECT_ATTEMPT_GUARD_MILLIS = 1000L;

	@FunctionalInterface
	public interface ActiveSessionSupplier {
		ActiveSessionDescriptor getActiveSession();
	}

	public record ActiveSessionDescriptor(
		String sessionId,
		long startedAtMillis
	) {
		public ActiveSessionDescriptor {
			sessionId = Objects.requireNonNull(sessionId, "sessionId");
		}
	}

	private enum PendingFramePriority {
		HIGH,
		NORMAL
	}

	private record PendingFrame(
		String encodedPayload,
		String dedupeKey,
		PendingFramePriority priority,
		boolean reconnectReannounceEligible
	) {
		private PendingFrame {
			encodedPayload = Objects.requireNonNull(encodedPayload, "encodedPayload");
			dedupeKey = Objects.requireNonNull(dedupeKey, "dedupeKey");
			priority = Objects.requireNonNull(priority, "priority");
		}

		private PendingFrame withPriority(PendingFramePriority nextPriority) {
			return new PendingFrame(encodedPayload, dedupeKey, nextPriority, reconnectReannounceEligible);
		}
	}

	private record FrozenSessionStartDescriptor(
		String sessionId,
		PendingFrame frame
	) {
		private FrozenSessionStartDescriptor {
			sessionId = Objects.requireNonNull(sessionId, "sessionId");
			frame = Objects.requireNonNull(frame, "frame");
		}
	}

	private record SocketDetachResult(
		boolean wasSending,
		boolean shouldReconnect
	) {
	}

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final Deque<PendingFrame> pendingFrames = new ArrayDeque<>();
	private final ActiveSessionSupplier activeSessionSupplier;
	private final SessionStartPayloadSupplier sessionStartPayloadSupplier;
	private final TraceFrameEncoder frameEncoder;
	private final TransportStatusStore statusStore;
	private final TransportTelemetry telemetry;
	private final URI endpointUri;

	private WebSocket webSocket;
	private boolean connectInFlight;
	private boolean sendInFlight;
	private PendingFrame inFlightFrame;
	private long connectAttemptStartedAtMillis;
	private long lastConnectAttemptAtMillis;
	private boolean needsSessionReannounceOnOpen;
	private FrozenSessionStartDescriptor activeSessionStartDescriptor;

	public WebSocketObservationSink(TransportStatusStore statusStore) {
		this(statusStore, TransportTelemetry.NOOP, () -> null);
	}

	public WebSocketObservationSink(TransportStatusStore statusStore, TransportTelemetry telemetry) {
		this(statusStore, telemetry, () -> null);
	}

	public WebSocketObservationSink(
		TransportStatusStore statusStore,
		TransportTelemetry telemetry,
		ActiveSessionSupplier activeSessionSupplier
	) {
		this(statusStore, telemetry, activeSessionSupplier, new DefaultSessionStartPayloadSupplier());
	}

	public WebSocketObservationSink(
		TransportStatusStore statusStore,
		TransportTelemetry telemetry,
		ActiveSessionSupplier activeSessionSupplier,
		SessionStartPayloadSupplier sessionStartPayloadSupplier
	) {
		this(statusStore, telemetry, activeSessionSupplier, sessionStartPayloadSupplier, new DefaultTraceFrameEncoder());
	}

	public WebSocketObservationSink(
		TransportStatusStore statusStore,
		TransportTelemetry telemetry,
		ActiveSessionSupplier activeSessionSupplier,
		SessionStartPayloadSupplier sessionStartPayloadSupplier,
		TraceFrameEncoder frameEncoder
	) {
		this.statusStore = Objects.requireNonNull(statusStore, "statusStore");
		this.telemetry = telemetry == null ? TransportTelemetry.NOOP : telemetry;
		this.activeSessionSupplier = activeSessionSupplier == null ? () -> null : activeSessionSupplier;
		this.sessionStartPayloadSupplier = sessionStartPayloadSupplier == null
			? new DefaultSessionStartPayloadSupplier()
			: sessionStartPayloadSupplier;
		this.frameEncoder = frameEncoder == null ? new DefaultTraceFrameEncoder() : frameEncoder;
		this.endpointUri = resolveEndpointUri();
		this.statusStore.setEndpoint(endpointUri.toString());
	}

	public void start() {
		connectIfNeeded();
	}

	@Override
	public void emit(TraceEvent event) {
		ActiveSessionDescriptor activeSession = activeSessionSupplier.getActiveSession();
		if (activeSession == null) {
			statusStore.recordSendSkipped("hub ingress trace skipped: no active world session");
			return;
		}

		TraceFrameEncoder.EncodedTraceFrame encodedFrame = frameEncoder.encodeTraceEvent(activeSession.sessionId(), event);
		enqueueFrame(
			new PendingFrame(
				encodedFrame.payload(),
				buildDedupeKey(encodedFrame.kind(), activeSession.sessionId(), event.sequence()),
				PendingFramePriority.NORMAL,
				false
			),
			false
		);
	}

	public void emitSessionStart(String sessionId, long sequence, long capturedAtMillis) {
		SessionStartPayload payload = Objects.requireNonNull(
			sessionStartPayloadSupplier.createPayload(),
			"sessionStartPayloadSupplier returned null"
		);
		TraceFrameEncoder.EncodedTraceFrame encodedFrame = frameEncoder.encodeSessionStart(
			sessionId,
			sequence,
			capturedAtMillis,
			payload
		);
		PendingFrame pendingFrame = new PendingFrame(
			encodedFrame.payload(),
			buildDedupeKey(encodedFrame.kind(), sessionId, sequence),
			PendingFramePriority.HIGH,
			true
		);

		synchronized (this) {
			activeSessionStartDescriptor = new FrozenSessionStartDescriptor(
				sessionId,
				pendingFrame
			);
		}

		enqueueFrame(pendingFrame, true);
	}

	public void emitSessionEnd(String sessionId, long sequence, long capturedAtMillis) {
		TraceFrameEncoder.EncodedTraceFrame encodedFrame = frameEncoder.encodeSessionEnd(sessionId, sequence, capturedAtMillis);
		PendingFrame pendingFrame = new PendingFrame(
			encodedFrame.payload(),
			buildDedupeKey(encodedFrame.kind(), sessionId, sequence),
			PendingFramePriority.NORMAL,
			false
		);

		synchronized (this) {
			if (activeSessionStartDescriptor != null && activeSessionStartDescriptor.sessionId().equals(sessionId)) {
				activeSessionStartDescriptor = null;
				needsSessionReannounceOnOpen = false;
			}
		}

		enqueueFrame(pendingFrame, false);
	}

	private void enqueueFrame(PendingFrame pendingFrame, boolean markSessionReannounceIfDisconnected) {
		synchronized (this) {
			if (markSessionReannounceIfDisconnected && webSocket == null) {
				needsSessionReannounceOnOpen = true;
			}
			enqueueFrameLocked(pendingFrame);
		}

		connectIfNeeded();
		drainQueue();
	}

	private void drainQueue() {
		WebSocket socketToUse;
		PendingFrame pendingFrame;
		long sendStartedAtMillis = System.currentTimeMillis();

		synchronized (this) {
			if (webSocket == null || sendInFlight || pendingFrames.isEmpty()) {
				return;
			}

			socketToUse = webSocket;
			pendingFrame = pendingFrames.removeFirst();
			inFlightFrame = pendingFrame;
			sendInFlight = true;
		}

		try {
			socketToUse.sendText(pendingFrame.encodedPayload(), true).whenComplete((ignored, error) -> {
				if (error != null) {
					handleSendFailure(socketToUse, error);
					return;
				}

				long latencyMillis = Math.max(0L, System.currentTimeMillis() - sendStartedAtMillis);
				synchronized (this) {
					sendInFlight = false;
					inFlightFrame = null;
				}
				statusStore.recordSendSuccess(latencyMillis);
				telemetry.onSendSucceeded(latencyMillis);
				drainQueue();
			});
		} catch (RuntimeException error) {
			handleSendFailure(socketToUse, error);
		}
	}

	private void enqueueFrameLocked(PendingFrame pendingFrame) {
		dropOldestQueuedFrameIfFullLocked();

		if (pendingFrame.priority() == PendingFramePriority.HIGH) {
			pendingFrames.addFirst(pendingFrame);
			return;
		}

		pendingFrames.addLast(pendingFrame);
	}

	private boolean hasQueuedFrameWithDedupeKeyLocked(String dedupeKey) {
		if (inFlightFrame != null && dedupeKey.equals(inFlightFrame.dedupeKey())) {
			return true;
		}

		for (PendingFrame pendingFrame : pendingFrames) {
			if (dedupeKey.equals(pendingFrame.dedupeKey())) {
				return true;
			}
		}

		return false;
	}

	private boolean restoreInFlightFrameLocked() {
		if (inFlightFrame == null) {
			return !pendingFrames.isEmpty();
		}

		dropOldestQueuedFrameIfFullLocked();

		pendingFrames.addFirst(inFlightFrame);
		inFlightFrame = null;
		return true;
	}

	private void dropOldestQueuedFrameIfFullLocked() {
		if (pendingFrames.size() < MAX_PENDING_FRAMES) {
			return;
		}

		pendingFrames.removeFirst();
		statusStore.recordSendSkipped("hub ingress backlog full; dropped oldest queued sample");
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
			shouldReconnect = restoreInFlightFrameLocked();
			if (webSocket == failingSocket) {
				webSocket = null;
				needsSessionReannounceOnOpen = true;
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
		boolean shouldReannounceActiveSession;

		synchronized (this) {
			connectDurationMillis = finishConnectAttemptLocked();
			webSocket = socket;
			connectInFlight = false;
			sendInFlight = false;
			shouldReannounceActiveSession = needsSessionReannounceOnOpen;
			needsSessionReannounceOnOpen = false;
		}

		if (shouldReannounceActiveSession) {
			prependSessionReannounce(socket);
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
			SocketDetachResult detachResult = detachSocketLocked(socket);
			if (detachResult == null) {
				return;
			}

			wasSending = detachResult.wasSending();
			shouldReconnect = detachResult.shouldReconnect();
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
			SocketDetachResult detachResult = detachSocketLocked(socket);
			if (detachResult == null) {
				return;
			}

			wasSending = detachResult.wasSending();
			shouldReconnect = detachResult.shouldReconnect();
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

	private SocketDetachResult detachSocketLocked(WebSocket socket) {
		if (webSocket != socket) {
			return null;
		}

		boolean wasSending = sendInFlight;
		webSocket = null;
		connectInFlight = false;
		sendInFlight = false;
		needsSessionReannounceOnOpen = true;
		boolean shouldReconnect = restoreInFlightFrameLocked();
		return new SocketDetachResult(wasSending, shouldReconnect);
	}

	private void prependSessionReannounce(WebSocket socket) {
		ActiveSessionDescriptor activeSession = activeSessionSupplier.getActiveSession();
		if (activeSession == null) {
			return;
		}

		boolean reannounceQueued = false;

		synchronized (this) {
			if (webSocket != socket || activeSessionStartDescriptor == null) {
				return;
			}
			if (!activeSessionStartDescriptor.sessionId().equals(activeSession.sessionId())) {
				return;
			}

			PendingFrame sessionStartFrame = activeSessionStartDescriptor.frame().withPriority(PendingFramePriority.HIGH);
			if (!sessionStartFrame.reconnectReannounceEligible()) {
				return;
			}
			if (hasQueuedFrameWithDedupeKeyLocked(sessionStartFrame.dedupeKey())) {
				return;
			}

			enqueueFrameLocked(sessionStartFrame);
			reannounceQueued = true;
		}

		if (reannounceQueued) {
			AiriUserClientMod.LOGGER.info(
				"Re-announced active world session start for websocket reconnect: {}",
				activeSession.sessionId()
			);
		}
	}

	private static String buildDedupeKey(String kind, String sessionId, long sequence) {
		return kind + '|' + sessionId + '|' + sequence;
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

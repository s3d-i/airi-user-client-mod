package io.github.airi.clientmod.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.github.airi.clientmod.AiriUserClientMod;
import io.github.airi.clientmod.observation.ObservationEmitter;
import io.github.airi.clientmod.observation.ObservationSample;

public final class WebSocketObservationSink implements ObservationEmitter {
	private static final String WS_URI_PROPERTY = "airi.transport.ws.uri";
	private static final String DEFAULT_WS_URI = "ws://127.0.0.1:8787/ws";
	private static final int MAX_QUEUE_DEPTH = 128;
	private static final long INITIAL_RECONNECT_BACKOFF_MILLIS = 1000L;
	private static final long MAX_RECONNECT_BACKOFF_MILLIS = 30000L;

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final ScheduledExecutorService reconnectExecutor =
		Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("airi-ws-transport").factory());
	private final Deque<String> pendingMessages = new ArrayDeque<>();
	private final TransportStatusStore statusStore;
	private final TransportTelemetry telemetry;
	private final URI endpointUri;
	private final String sessionId = Long.toUnsignedString(System.currentTimeMillis(), 36);

	private WebSocket webSocket;
	private boolean connectInFlight;
	private boolean reconnectScheduled;
	private boolean sendInFlight;
	private long nextReconnectBackoffMillis = INITIAL_RECONNECT_BACKOFF_MILLIS;

	public WebSocketObservationSink(TransportStatusStore statusStore) {
		this(statusStore, TransportTelemetry.NOOP);
	}

	public WebSocketObservationSink(TransportStatusStore statusStore, TransportTelemetry telemetry) {
		this.statusStore = statusStore;
		this.telemetry = telemetry;
		this.endpointUri = resolveEndpointUri();
		this.statusStore.setEndpoint(endpointUri.toString());
	}

	public void start() {
		connectIfNeeded();
	}

	@Override
	public void emit(ObservationSample sample) {
		synchronized (this) {
			enqueueMessageLocked(serializeObservationSample(sample));
		}

		connectIfNeeded();
		drainQueue();
	}

	private void connectIfNeeded() {
		boolean shouldConnect = false;

		synchronized (this) {
			if (webSocket == null && !connectInFlight && !reconnectScheduled) {
				connectInFlight = true;
				shouldConnect = true;
			}
		}

		if (!shouldConnect) {
			return;
		}

		statusStore.markConnecting();
		telemetry.onStateChanged(TransportConnectionState.CONNECTING);

		httpClient.newWebSocketBuilder()
			.buildAsync(endpointUri, new Listener())
			.whenComplete((socket, error) -> {
				if (error != null) {
					handleConnectionFailure(error, true);
				}
			});
	}

	private void drainQueue() {
		WebSocket socket;
		String nextMessage;
		long sendStartedAtMillis = System.currentTimeMillis();

		synchronized (this) {
			if (webSocket == null || sendInFlight || pendingMessages.isEmpty()) {
				return;
			}

			socket = webSocket;
			nextMessage = pendingMessages.peekFirst();
			sendInFlight = true;
		}

		socket.sendText(nextMessage, true).whenComplete((ignored, error) -> {
			if (error != null) {
				handleConnectionFailure(error, false);
				return;
			}

			long latencyMillis = Math.max(0L, System.currentTimeMillis() - sendStartedAtMillis);
			int queueDepth;
			long sentCount;

			synchronized (this) {
				pendingMessages.removeFirst();
				sendInFlight = false;
				queueDepth = pendingMessages.size();
			}

			sentCount = statusStore.recordSent(latencyMillis, queueDepth);
			telemetry.onMessageSent(sentCount, latencyMillis, queueDepth);
			telemetry.onQueueDepthChanged(queueDepth);
			drainQueue();
		});
	}

	private void enqueueMessageLocked(String message) {
		if (pendingMessages.size() >= MAX_QUEUE_DEPTH) {
			pendingMessages.removeFirst();
			long droppedCount = statusStore.recordDropped(
				pendingMessages.size(),
				"transport queue full; dropped oldest message"
			);
			telemetry.onMessageDropped(droppedCount, pendingMessages.size());
		}

		pendingMessages.addLast(message);
		statusStore.updateQueueDepth(pendingMessages.size());
		telemetry.onQueueDepthChanged(pendingMessages.size());
	}

	private void handleConnectionFailure(Throwable error, boolean fromConnectPhase) {
		String message = summarize(error);
		boolean shouldScheduleReconnect = false;

		synchronized (this) {
			connectInFlight = false;
			sendInFlight = false;

			if (webSocket != null) {
				try {
					webSocket.abort();
				} catch (RuntimeException ignored) {
				}
				webSocket = null;
			}

			if (!reconnectScheduled) {
				reconnectScheduled = true;
				shouldScheduleReconnect = true;
			}
		}

		statusStore.recordFailure(message);
		telemetry.onConnectionFailure(message);

		if (!shouldScheduleReconnect) {
			return;
		}

		long backoffMillis = reserveReconnectBackoff(message);
		if (fromConnectPhase) {
			AiriUserClientMod.LOGGER.warn("Observation websocket connect failed: {}", message);
		} else {
			AiriUserClientMod.LOGGER.warn("Observation websocket send failed: {}", message);
		}

		reconnectExecutor.schedule(() -> {
			synchronized (this) {
				reconnectScheduled = false;
			}
			connectIfNeeded();
		}, backoffMillis, TimeUnit.MILLISECONDS);
	}

	private long reserveReconnectBackoff(String message) {
		long backoffMillis;
		long reconnectCount;

		synchronized (this) {
			backoffMillis = nextReconnectBackoffMillis;
			nextReconnectBackoffMillis = Math.min(nextReconnectBackoffMillis * 2L, MAX_RECONNECT_BACKOFF_MILLIS);
		}

		reconnectCount = statusStore.markBackoff(backoffMillis, message);
		telemetry.onStateChanged(TransportConnectionState.BACKOFF);
		telemetry.onReconnectScheduled(reconnectCount, backoffMillis);
		return backoffMillis;
	}

	private void handleOpen(WebSocket socket) {
		synchronized (this) {
			webSocket = socket;
			connectInFlight = false;
			sendInFlight = false;
			nextReconnectBackoffMillis = INITIAL_RECONNECT_BACKOFF_MILLIS;
		}

		statusStore.markOpen();
		telemetry.onStateChanged(TransportConnectionState.OPEN);
		AiriUserClientMod.LOGGER.info("Observation websocket connected to {}", endpointUri);
		drainQueue();
	}

	private void handleClosed(int statusCode, String reason) {
		String closeReason = "closed (" + statusCode + "): " + reason;
		boolean shouldReconnect = false;

		synchronized (this) {
			webSocket = null;
			connectInFlight = false;
			sendInFlight = false;
			if (!reconnectScheduled) {
				reconnectScheduled = true;
				shouldReconnect = true;
			}
		}

		if (!shouldReconnect) {
			return;
		}

		long backoffMillis = reserveReconnectBackoff(closeReason);
		AiriUserClientMod.LOGGER.warn("Observation websocket disconnected: {}", closeReason);

		reconnectExecutor.schedule(() -> {
			synchronized (this) {
				reconnectScheduled = false;
			}
			connectIfNeeded();
		}, backoffMillis, TimeUnit.MILLISECONDS);
	}

	private static URI resolveEndpointUri() {
		String configuredUri = System.getProperty(WS_URI_PROPERTY, DEFAULT_WS_URI);
		try {
			return URI.create(configuredUri);
		} catch (IllegalArgumentException exception) {
			AiriUserClientMod.LOGGER.warn(
				"Invalid observation websocket URI '{}' from -D{}; falling back to {}",
				configuredUri,
				WS_URI_PROPERTY,
				DEFAULT_WS_URI
			);
			return URI.create(DEFAULT_WS_URI);
		}
	}

	private static String summarize(Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) {
			return error.getClass().getSimpleName();
		}
		return error.getClass().getSimpleName() + ": " + message;
	}

	private String serializeObservationSample(ObservationSample sample) {
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
			handleClosed(statusCode, reason);
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			handleConnectionFailure(error, false);
		}
	}
}

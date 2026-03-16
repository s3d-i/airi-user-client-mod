package io.github.airi.clientmod.telemetry;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.github.airi.clientmod.transport.TransportConnectionState;
import io.github.airi.clientmod.transport.TransportStateTransition;
import io.github.airi.clientmod.transport.TransportTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

public final class OtelTransportTelemetry implements TransportTelemetry {
	private static final String METRIC_PREFIX = "airi.transport.ws.";
	private static final AttributeKey<String> CONNECTION_STATE_ATTRIBUTE = AttributeKey.stringKey("connection.state");
	private static final AttributeKey<String> PREVIOUS_STATE_ATTRIBUTE = AttributeKey.stringKey("connection.previous_state");
	private static final AttributeKey<String> TRANSITION_FROM_ATTRIBUTE = AttributeKey.stringKey("transition.from");
	private static final AttributeKey<String> TRANSITION_TO_ATTRIBUTE = AttributeKey.stringKey("transition.to");
	private static final AttributeKey<String> CONNECTION_RESULT_ATTRIBUTE = AttributeKey.stringKey("connection.result");
	private static final AttributeKey<String> FAILURE_PHASE_ATTRIBUTE = AttributeKey.stringKey("failure.phase");
	private static final AttributeKey<String> FAILURE_TYPE_ATTRIBUTE = AttributeKey.stringKey("failure.type");
	private static final AttributeKey<Long> CLOSE_STATUS_CODE_ATTRIBUTE = AttributeKey.longKey("close.status_code");

	private final AtomicLong queueDepth = new AtomicLong();
	private final AtomicReference<TransportConnectionState> connectionState =
		new AtomicReference<>(TransportConnectionState.DISCONNECTED);
	private final AtomicReference<TransportConnectionState> previousConnectionState =
		new AtomicReference<>(TransportConnectionState.DISCONNECTED);
	private final AtomicLong stateChangedAtMillis = new AtomicLong(System.currentTimeMillis());
	private final LongCounter sentCounter;
	private final LongCounter droppedCounter;
	private final LongCounter connectAttemptCounter;
	private final LongCounter stateTransitionCounter;
	private final LongCounter connectionOpenCounter;
	private final LongCounter connectionCloseCounter;
	private final LongCounter reconnectCounter;
	private final LongCounter connectionFailureCounter;
	private final LongHistogram sendLatencyMillis;
	private final LongHistogram connectionAttemptDurationMillis;
	private final LongHistogram reconnectBackoffMillis;
	@SuppressWarnings("unused")
	private final ObservableLongGauge queueDepthGauge;
	@SuppressWarnings("unused")
	private final ObservableLongGauge connectionStateGauge;
	@SuppressWarnings("unused")
	private final ObservableLongGauge connectionStateDurationGauge;

	public OtelTransportTelemetry(Meter meter) {
		this.sentCounter = meter.counterBuilder(METRIC_PREFIX + "messages.sent")
			.setDescription("Number of websocket observation messages sent")
			.setUnit("{message}")
			.build();
		this.droppedCounter = meter.counterBuilder(METRIC_PREFIX + "messages.dropped")
			.setDescription("Number of websocket observation messages dropped before send")
			.setUnit("{message}")
			.build();
		this.connectAttemptCounter = meter.counterBuilder(METRIC_PREFIX + "connection.attempts")
			.setDescription("Number of websocket connection attempts started")
			.setUnit("{attempt}")
			.build();
		this.stateTransitionCounter = meter.counterBuilder(METRIC_PREFIX + "connection.transitions")
			.setDescription("Number of websocket connection state transitions")
			.setUnit("{transition}")
			.build();
		this.connectionOpenCounter = meter.counterBuilder(METRIC_PREFIX + "connection.opens")
			.setDescription("Number of websocket connections opened")
			.setUnit("{connection}")
			.build();
		this.connectionCloseCounter = meter.counterBuilder(METRIC_PREFIX + "connection.closes")
			.setDescription("Number of websocket connections closed")
			.setUnit("{connection}")
			.build();
		this.reconnectCounter = meter.counterBuilder(METRIC_PREFIX + "reconnects")
			.setDescription("Number of websocket reconnects scheduled")
			.setUnit("{reconnect}")
			.build();
		this.connectionFailureCounter = meter.counterBuilder(METRIC_PREFIX + "connection.failures")
			.setDescription("Number of websocket connection or send failures")
			.setUnit("{failure}")
			.build();
		this.sendLatencyMillis = meter.histogramBuilder(METRIC_PREFIX + "send.latency")
			.ofLongs()
			.setDescription("Websocket observation send latency")
			.setUnit("ms")
			.build();
		this.connectionAttemptDurationMillis = meter.histogramBuilder(METRIC_PREFIX + "connection.attempt.duration")
			.ofLongs()
			.setDescription("Websocket connection attempt duration")
			.setUnit("ms")
			.build();
		this.reconnectBackoffMillis = meter.histogramBuilder(METRIC_PREFIX + "reconnect.backoff")
			.ofLongs()
			.setDescription("Websocket reconnect backoff duration")
			.setUnit("ms")
			.build();
		this.queueDepthGauge = meter.gaugeBuilder(METRIC_PREFIX + "queue.depth")
			.ofLongs()
			.setDescription("Current websocket transport queue depth")
			.setUnit("{message}")
			.buildWithCallback(measurement -> measurement.record(queueDepth.get()));
		this.connectionStateGauge = meter.gaugeBuilder(METRIC_PREFIX + "connection.state")
			.ofLongs()
			.setDescription("Current websocket transport connection state")
			.setUnit("{state}")
			.buildWithCallback(measurement -> {
				TransportConnectionState state = connectionState.get();
				measurement.record(
					stateToLong(state),
					Attributes.of(
						CONNECTION_STATE_ATTRIBUTE,
						state.name(),
						PREVIOUS_STATE_ATTRIBUTE,
						previousConnectionState.get().name()
					)
				);
			});
		this.connectionStateDurationGauge = meter.gaugeBuilder(METRIC_PREFIX + "connection.state.duration")
			.ofLongs()
			.setDescription("Time spent in the current websocket connection state")
			.setUnit("ms")
			.buildWithCallback(measurement -> {
				TransportConnectionState state = connectionState.get();
				measurement.record(
					Math.max(0L, System.currentTimeMillis() - stateChangedAtMillis.get()),
					Attributes.of(
						CONNECTION_STATE_ATTRIBUTE,
						state.name(),
						PREVIOUS_STATE_ATTRIBUTE,
						previousConnectionState.get().name()
					)
				);
			});
	}

	@Override
	public void onConnectAttemptStarted() {
		connectAttemptCounter.add(1L);
	}

	@Override
	public void onStateChanged(TransportStateTransition transition) {
		previousConnectionState.set(transition.previousState());
		connectionState.set(transition.currentState());
		stateChangedAtMillis.set(transition.enteredAtMillis());

		if (!transition.changed()) {
			return;
		}

		stateTransitionCounter.add(1L, Attributes.of(
			TRANSITION_FROM_ATTRIBUTE,
			transition.previousState().name(),
			TRANSITION_TO_ATTRIBUTE,
			transition.currentState().name()
		));
	}

	@Override
	public void onQueueDepthChanged(int queueDepth) {
		this.queueDepth.set(queueDepth);
	}

	@Override
	public void onMessageDropped(long droppedCount, int queueDepth) {
		this.queueDepth.set(queueDepth);
		droppedCounter.add(1L);
	}

	@Override
	public void onMessageSent(long sentCount, long latencyMillis, int queueDepth) {
		this.queueDepth.set(queueDepth);
		sentCounter.add(1L);
		sendLatencyMillis.record(latencyMillis);
	}

	@Override
	public void onConnectionOpened(long connectDurationMillis) {
		connectionOpenCounter.add(1L);
		if (connectDurationMillis >= 0L) {
			connectionAttemptDurationMillis.record(
				connectDurationMillis,
				Attributes.of(CONNECTION_RESULT_ATTRIBUTE, "success")
			);
		}
	}

	@Override
	public void onConnectionClosed(int statusCode) {
		connectionCloseCounter.add(1L, Attributes.of(CLOSE_STATUS_CODE_ATTRIBUTE, (long) statusCode));
	}

	@Override
	public void onReconnectScheduled(long backoffMillis) {
		reconnectCounter.add(1L);
		reconnectBackoffMillis.record(backoffMillis);
	}

	@Override
	public void onConnectionFailure(String phase, Throwable error, long connectDurationMillis) {
		connectionFailureCounter.add(1L, Attributes.of(
			FAILURE_PHASE_ATTRIBUTE,
			phase,
			FAILURE_TYPE_ATTRIBUTE,
			classifyFailure(error)
		));
		if (connectDurationMillis >= 0L) {
			connectionAttemptDurationMillis.record(
				connectDurationMillis,
				Attributes.of(CONNECTION_RESULT_ATTRIBUTE, "failure")
			);
		}
	}

	private static long stateToLong(TransportConnectionState state) {
		return switch (state) {
			case DISCONNECTED -> 0L;
			case CONNECTING -> 1L;
			case OPEN -> 2L;
			case BACKOFF -> 3L;
		};
	}

	private static String classifyFailure(Throwable error) {
		if (error == null) {
			return "unknown";
		}

		String simpleName = error.getClass().getSimpleName();
		return simpleName == null || simpleName.isBlank() ? error.getClass().getName() : simpleName;
	}
}

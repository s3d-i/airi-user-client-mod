package io.github.airi.clientmod.transport;

public interface TransportTelemetry {
	TransportTelemetry NOOP = new TransportTelemetry() {
	};

	default void onConnectAttemptStarted() {
	}

	default void onStateChanged(TransportStateTransition transition) {
	}

	default void onQueueDepthChanged(int queueDepth) {
	}

	default void onMessageDropped(long droppedCount, int queueDepth) {
	}

	default void onMessageSent(long sentCount, long latencyMillis, int queueDepth) {
	}

	default void onConnectionOpened(long connectDurationMillis) {
	}

	default void onConnectionClosed(int statusCode) {
	}

	default void onReconnectScheduled(long backoffMillis) {
	}

	default void onConnectionFailure(String phase, Throwable error, long connectDurationMillis) {
	}
}

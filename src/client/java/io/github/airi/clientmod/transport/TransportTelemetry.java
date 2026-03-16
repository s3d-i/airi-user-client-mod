package io.github.airi.clientmod.transport;

public interface TransportTelemetry {
	TransportTelemetry NOOP = new TransportTelemetry() {
	};

	default void onStateChanged(TransportConnectionState state) {
	}

	default void onQueueDepthChanged(int queueDepth) {
	}

	default void onMessageDropped(long droppedCount, int queueDepth) {
	}

	default void onMessageSent(long sentCount, long latencyMillis, int queueDepth) {
	}

	default void onReconnectScheduled(long reconnectCount, long backoffMillis) {
	}

	default void onConnectionFailure(String message) {
	}
}

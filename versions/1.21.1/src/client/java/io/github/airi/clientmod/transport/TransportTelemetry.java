package io.github.airi.clientmod.transport;

public interface TransportTelemetry {
	TransportTelemetry NOOP = new TransportTelemetry() {
	};

	default void onConnectAttemptStarted() {
	}

	default void onStateChanged(TransportStateTransition transition) {
	}

	default void onSendSucceeded(long latencyMillis) {
	}

	default void onConnectionOpened(long connectDurationMillis) {
	}

	default void onConnectionClosed(int statusCode) {
	}

	default void onConnectionFailure(String phase, Throwable error, long connectDurationMillis) {
	}
}

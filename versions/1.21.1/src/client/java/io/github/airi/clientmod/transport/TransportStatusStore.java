package io.github.airi.clientmod.transport;

public final class TransportStatusStore {
	private static final String DEFAULT_ENDPOINT = "ws://127.0.0.1:8787/ws";

	private TransportConnectionState state = TransportConnectionState.DISCONNECTED;
	private TransportConnectionState previousState = TransportConnectionState.DISCONNECTED;
	private long stateChangedAtMillis = System.currentTimeMillis();
	private boolean hasStateTransition;
	private String endpoint = DEFAULT_ENDPOINT;
	private SendOutcome lastSendOutcome = SendOutcome.NONE;
	private long lastSendOutcomeAtMillis;
	private long lastSendLatencyMillis = -1L;
	private long lastSentAtMillis;
	private String lastError = "none";

	public synchronized void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public synchronized TransportStateTransition markConnecting() {
		return transitionTo(TransportConnectionState.CONNECTING);
	}

	public synchronized TransportStateTransition markOpen() {
		lastError = "none";
		return transitionTo(TransportConnectionState.OPEN);
	}

	public synchronized TransportStateTransition markDisconnected(String reason) {
		updateLastError(reason);
		return transitionTo(TransportConnectionState.DISCONNECTED);
	}

	public synchronized TransportStateTransition markError(String errorMessage) {
		updateLastError(errorMessage);
		return transitionTo(TransportConnectionState.ERROR);
	}

	public synchronized void recordSendSuccess(long latencyMillis) {
		long now = System.currentTimeMillis();
		lastSendOutcome = SendOutcome.SUCCESS;
		lastSendOutcomeAtMillis = now;
		lastSendLatencyMillis = latencyMillis;
		lastSentAtMillis = now;
		lastError = "none";
	}

	public synchronized void recordSendSkipped(String reason) {
		lastSendOutcome = SendOutcome.SKIPPED;
		lastSendOutcomeAtMillis = System.currentTimeMillis();
		updateLastError(reason);
	}

	public synchronized void recordSendFailure(String errorMessage) {
		lastSendOutcome = SendOutcome.FAILURE;
		lastSendOutcomeAtMillis = System.currentTimeMillis();
		updateLastError(errorMessage);
	}

	public synchronized Snapshot snapshot() {
		return snapshotLocked();
	}

	private TransportStateTransition transitionTo(TransportConnectionState nextState) {
		if (state == nextState) {
			return new TransportStateTransition(previousState, state, stateChangedAtMillis, false);
		}

		long now = System.currentTimeMillis();
		TransportConnectionState priorState = state;
		previousState = priorState;
		state = nextState;
		stateChangedAtMillis = now;
		hasStateTransition = true;
		return new TransportStateTransition(priorState, state, now, true);
	}

	private Snapshot snapshotLocked() {
		return new Snapshot(
			state,
			previousState,
			stateChangedAtMillis,
			hasStateTransition,
			endpoint,
			lastSendOutcome,
			lastSendOutcomeAtMillis,
			lastSendLatencyMillis,
			lastSentAtMillis,
			lastError
		);
	}

	private void updateLastError(String errorMessage) {
		if (errorMessage == null || errorMessage.isBlank()) {
			return;
		}

		if (errorMessage.length() > 80) {
			lastError = errorMessage.substring(0, 77) + "...";
			return;
		}

		lastError = errorMessage;
	}

	public record Snapshot(
		TransportConnectionState state,
		TransportConnectionState previousState,
		long stateChangedAtMillis,
		boolean hasStateTransition,
		String endpoint,
		SendOutcome lastSendOutcome,
		long lastSendOutcomeAtMillis,
		long lastSendLatencyMillis,
		long lastSentAtMillis,
		String lastError
	) {
	}

	public enum SendOutcome {
		NONE("none"),
		SUCCESS("success"),
		SKIPPED("skipped"),
		FAILURE("failure");

		private final String label;

		SendOutcome(String label) {
			this.label = label;
		}

		String label() {
			return label;
		}
	}

	public enum TransportConnectionState {
		DISCONNECTED,
		CONNECTING,
		OPEN,
		ERROR
	}

	public record TransportStateTransition(
		TransportConnectionState previousState,
		TransportConnectionState currentState,
		long enteredAtMillis,
		boolean changed
	) {
	}
}

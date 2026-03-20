package io.github.airi.clientmod.transport;

import java.util.ArrayList;
import java.util.List;

public final class TransportStatusStore {
	private TransportConnectionState state = TransportConnectionState.DISCONNECTED;
	private TransportConnectionState previousState = TransportConnectionState.DISCONNECTED;
	private long stateChangedAtMillis = System.currentTimeMillis();
	private boolean hasStateTransition;
	private String endpoint = "ws://127.0.0.1:8787/ws";
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

	public synchronized List<String> buildPanelLines() {
		long now = System.currentTimeMillis();
		long stateAgeMillis = Math.max(0L, now - stateChangedAtMillis);
		List<String> lines = new ArrayList<>();
		lines.add("[AIRI] hub ingress");
		lines.add("WS: " + state.name() + " (" + formatDuration(stateAgeMillis) + ")");

		if (hasStateTransition) {
			lines.add("Transition: " + previousState.name() + " -> " + state.name());
		}

		lines.add("Endpoint: " + endpoint);
		lines.add("Last outcome: " + describeLastOutcome(now));

		if (lastSentAtMillis == 0L) {
			lines.add("Last send success: never");
		} else {
			long ageMillis = Math.max(0L, now - lastSentAtMillis);
			lines.add(
				"Last send success: " + formatDuration(ageMillis) + " ago (" + lastSendLatencyMillis + " ms)"
			);
		}

		lines.add("Last error: " + lastError);
		return lines;
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

	private String describeLastOutcome(long now) {
		if (lastSendOutcome == SendOutcome.NONE || lastSendOutcomeAtMillis == 0L) {
			return "none yet";
		}

		long ageMillis = Math.max(0L, now - lastSendOutcomeAtMillis);
		return lastSendOutcome.label + " (" + formatDuration(ageMillis) + " ago)";
	}

	private static String formatDuration(long millis) {
		if (millis < 1000L) {
			return millis + " ms";
		}

		long totalSeconds = millis / 1000L;
		if (totalSeconds < 60L) {
			return totalSeconds + "s";
		}

		long minutes = totalSeconds / 60L;
		long seconds = totalSeconds % 60L;
		if (minutes < 60L) {
			return minutes + "m " + seconds + "s";
		}

		long hours = minutes / 60L;
		long remainingMinutes = minutes % 60L;
		return hours + "h " + remainingMinutes + "m";
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

	private enum SendOutcome {
		NONE("none"),
		SUCCESS("success"),
		SKIPPED("skipped"),
		FAILURE("failure");

		private final String label;

		SendOutcome(String label) {
			this.label = label;
		}
	}
}

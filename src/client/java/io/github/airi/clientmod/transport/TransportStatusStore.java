package io.github.airi.clientmod.transport;

import java.util.ArrayList;
import java.util.List;

public final class TransportStatusStore {
	private static final int DEFAULT_QUEUE_CAPACITY = 128;

	private final int queueCapacity;
	private TransportConnectionState state = TransportConnectionState.DISCONNECTED;
	private TransportConnectionState previousState = TransportConnectionState.DISCONNECTED;
	private long stateChangedAtMillis = System.currentTimeMillis();
	private boolean hasStateTransition;
	private String endpoint = "ws://127.0.0.1:8787/ws";
	private int queueDepth;
	private long droppedCount;
	private long reconnectCount;
	private long failureCount;
	private long sentCount;
	private long lastSendLatencyMillis = -1L;
	private long lastSentAtMillis;
	private long retryDelayMillis;
	private String lastError = "none";

	public TransportStatusStore() {
		this(DEFAULT_QUEUE_CAPACITY);
	}

	public TransportStatusStore(int queueCapacity) {
		this.queueCapacity = Math.max(1, queueCapacity);
	}

	public synchronized void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public synchronized TransportStateTransition markConnecting() {
		retryDelayMillis = 0L;
		return transitionTo(TransportConnectionState.CONNECTING);
	}

	public synchronized TransportStateTransition markOpen() {
		retryDelayMillis = 0L;
		lastError = "none";
		return transitionTo(TransportConnectionState.OPEN);
	}

	public synchronized TransportStateTransition markBackoff(long delayMillis, String errorMessage) {
		reconnectCount++;
		retryDelayMillis = delayMillis;
		updateLastError(errorMessage);
		return transitionTo(TransportConnectionState.BACKOFF);
	}

	public synchronized TransportStateTransition markDisconnected(String errorMessage) {
		retryDelayMillis = 0L;
		updateLastError(errorMessage);
		return transitionTo(TransportConnectionState.DISCONNECTED);
	}

	public synchronized void updateQueueDepth(int queueDepth) {
		this.queueDepth = queueDepth;
	}

	public synchronized long recordDropped(int queueDepth, String errorMessage) {
		droppedCount++;
		this.queueDepth = queueDepth;
		updateLastError(errorMessage);
		return droppedCount;
	}

	public synchronized long recordSent(long latencyMillis, int queueDepth) {
		sentCount++;
		lastSendLatencyMillis = latencyMillis;
		lastSentAtMillis = System.currentTimeMillis();
		this.queueDepth = queueDepth;
		return sentCount;
	}

	public synchronized long recordFailure(String errorMessage) {
		failureCount++;
		updateLastError(errorMessage);
		return failureCount;
	}

	public synchronized List<String> buildPanelLines() {
		long now = System.currentTimeMillis();
		long stateAgeMillis = Math.max(0L, now - stateChangedAtMillis);
		List<String> lines = new ArrayList<>();
		lines.add("[AIRI] transport");
		lines.add("WS: " + state.name() + " (" + formatDuration(stateAgeMillis) + ")");

		if (hasStateTransition) {
			lines.add("Transition: " + previousState.name() + " -> " + state.name());
		}

		String queueLine = "Queue: " + queueDepth + "/" + queueCapacity;
		if (queueDepth >= queueCapacity) {
			queueLine += " FULL";
		}
		lines.add(queueLine);
		lines.add("Dropped: " + droppedCount + " | Reconnects: " + reconnectCount);
		lines.add("Failures: " + failureCount + " | Sent: " + sentCount);

		if (lastSentAtMillis == 0L) {
			lines.add("Last send success: never");
		} else {
			long ageMillis = Math.max(0L, now - lastSentAtMillis);
			lines.add(
				"Last send success: " + formatDuration(ageMillis) + " ago (" + lastSendLatencyMillis + " ms)"
			);
		}

		if (state == TransportConnectionState.BACKOFF) {
			lines.add("Retry in: " + formatDuration(retryDelayMillis));
		}

		lines.add("Endpoint: " + endpoint);
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
}

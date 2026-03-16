package io.github.airi.clientmod.transport;

import java.util.ArrayList;
import java.util.List;

public final class TransportStatusStore {
	private TransportConnectionState state = TransportConnectionState.DISCONNECTED;
	private String endpoint = "ws://127.0.0.1:8787/ws";
	private int queueDepth;
	private long droppedCount;
	private long reconnectCount;
	private long sentCount;
	private long lastSendLatencyMillis = -1L;
	private long lastSentAtMillis;
	private long retryDelayMillis;
	private String lastError = "none";

	public synchronized void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public synchronized void markConnecting() {
		state = TransportConnectionState.CONNECTING;
		retryDelayMillis = 0L;
	}

	public synchronized void markOpen() {
		state = TransportConnectionState.OPEN;
		retryDelayMillis = 0L;
		lastError = "none";
	}

	public synchronized long markBackoff(long delayMillis, String errorMessage) {
		reconnectCount++;
		state = TransportConnectionState.BACKOFF;
		retryDelayMillis = delayMillis;
		updateLastError(errorMessage);
		return reconnectCount;
	}

	public synchronized void markDisconnected(String errorMessage) {
		state = TransportConnectionState.DISCONNECTED;
		retryDelayMillis = 0L;
		updateLastError(errorMessage);
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

	public synchronized void recordFailure(String errorMessage) {
		updateLastError(errorMessage);
	}

	public synchronized List<String> buildPanelLines() {
		List<String> lines = new ArrayList<>();
		lines.add("[AIRI] transport");
		lines.add("ws endpoint: " + endpoint);
		lines.add("ws state: " + state.name());
		lines.add("queue: " + queueDepth + " | sent: " + sentCount);
		lines.add("drops: " + droppedCount + " | reconnects: " + reconnectCount);

		if (lastSentAtMillis == 0L) {
			lines.add("last send: never");
		} else {
			long ageMillis = Math.max(0L, System.currentTimeMillis() - lastSentAtMillis);
			lines.add("last send: " + ageMillis + " ms ago | latency: " + lastSendLatencyMillis + " ms");
		}

		if (state == TransportConnectionState.BACKOFF) {
			lines.add("retry in: " + retryDelayMillis + " ms");
		}

		lines.add("last error: " + lastError);
		return lines;
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

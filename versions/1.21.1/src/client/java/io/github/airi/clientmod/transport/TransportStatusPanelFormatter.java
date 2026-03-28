package io.github.airi.clientmod.transport;

import java.util.ArrayList;
import java.util.List;

public final class TransportStatusPanelFormatter {
	public List<String> buildPanelLines(TransportStatusStore.Snapshot snapshot, long nowMillis) {
		long stateAgeMillis = Math.max(0L, nowMillis - snapshot.stateChangedAtMillis());
		List<String> lines = new ArrayList<>();
		lines.add("[AIRI] hub ingress");
		lines.add("WS: " + snapshot.state().name() + " (" + formatDuration(stateAgeMillis) + ")");

		if (snapshot.hasStateTransition()) {
			lines.add("Transition: " + snapshot.previousState().name() + " -> " + snapshot.state().name());
		}

		lines.add("Endpoint: " + snapshot.endpoint());
		lines.add("Last outcome: " + describeLastOutcome(snapshot, nowMillis));

		if (snapshot.lastSentAtMillis() == 0L) {
			lines.add("Last send success: never");
		} else {
			long ageMillis = Math.max(0L, nowMillis - snapshot.lastSentAtMillis());
			lines.add(
				"Last send success: " + formatDuration(ageMillis) + " ago (" + snapshot.lastSendLatencyMillis() + " ms)"
			);
		}

		lines.add("Last error: " + snapshot.lastError());
		return lines;
	}

	private String describeLastOutcome(TransportStatusStore.Snapshot snapshot, long nowMillis) {
		if (snapshot.lastSendOutcome() == TransportStatusStore.SendOutcome.NONE || snapshot.lastSendOutcomeAtMillis() == 0L) {
			return "none yet";
		}

		long ageMillis = Math.max(0L, nowMillis - snapshot.lastSendOutcomeAtMillis());
		return snapshot.lastSendOutcome().label() + " (" + formatDuration(ageMillis) + " ago)";
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
}

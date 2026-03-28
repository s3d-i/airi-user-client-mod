package io.github.airi.clientmod.observation;

import java.util.Objects;

import io.github.airi.clientmod.core.trace.TraceEvent;

public final class HandStateChangeDetector {
	private TraceEvent.ItemStackSnapshot lastMainHand;
	private TraceEvent.ItemStackSnapshot lastOffHand;

	public HandStateChangeDraft detect(ClientSnapshot snapshot) {
		TraceEvent.ItemStackSnapshot mainHand = snapshot.mainHand();
		TraceEvent.ItemStackSnapshot offHand = snapshot.offHand();
		if (lastMainHand == null || lastOffHand == null) {
			lastMainHand = mainHand;
			lastOffHand = offHand;
			return null;
		}

		if (Objects.equals(lastMainHand, mainHand) && Objects.equals(lastOffHand, offHand)) {
			return null;
		}

		return new HandStateChangeDraft(snapshot.selectedSlot(), mainHand, offHand);
	}

	public void commit(HandStateChangeDraft draft) {
		lastMainHand = draft.mainHand();
		lastOffHand = draft.offHand();
	}

	public void reset() {
		lastMainHand = null;
		lastOffHand = null;
	}

	public record HandStateChangeDraft(
		int selectedSlot,
		TraceEvent.ItemStackSnapshot mainHand,
		TraceEvent.ItemStackSnapshot offHand
	) {
	}
}

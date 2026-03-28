package io.github.airi.clientmod.observation;

import io.github.airi.clientmod.core.trace.TraceEvent;

public final class SelectedSlotChangeDetector {
	private Integer lastSelectedSlot;

	public SelectedSlotChangeDraft detect(ClientSnapshot snapshot) {
		int selectedSlot = snapshot.selectedSlot();
		if (lastSelectedSlot == null) {
			lastSelectedSlot = selectedSlot;
			return null;
		}

		if (lastSelectedSlot == selectedSlot) {
			return null;
		}

		return new SelectedSlotChangeDraft(
			lastSelectedSlot,
			selectedSlot,
			snapshot.mainHand(),
			snapshot.offHand()
		);
	}

	public void commit(SelectedSlotChangeDraft draft) {
		lastSelectedSlot = draft.selectedSlot();
	}

	public void reset() {
		lastSelectedSlot = null;
	}

	public record SelectedSlotChangeDraft(
		int previousSelectedSlot,
		int selectedSlot,
		TraceEvent.ItemStackSnapshot mainHand,
		TraceEvent.ItemStackSnapshot offHand
	) {
	}
}

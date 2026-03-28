package io.github.airi.clientmod.observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.airi.clientmod.core.trace.TraceEvent;

public final class InventoryDeltaDetector {
	private static final int MAX_INVENTORY_SLOT_DELTAS = 24;

	private List<TraceEvent.ItemStackSnapshot> lastInventorySnapshot = List.of();

	public InventoryDeltaDraft detect(ClientSnapshot snapshot) {
		List<TraceEvent.ItemStackSnapshot> currentInventorySnapshot = snapshot.inventorySnapshot();
		if (lastInventorySnapshot.isEmpty()) {
			lastInventorySnapshot = currentInventorySnapshot;
			return null;
		}

		List<TraceEvent.InventorySlotDelta> changedSlots = buildInventorySlotDeltas(
			lastInventorySnapshot,
			currentInventorySnapshot
		);
		if (changedSlots.isEmpty()) {
			lastInventorySnapshot = currentInventorySnapshot;
			return null;
		}

		return new InventoryDeltaDraft(changedSlots, currentInventorySnapshot);
	}

	public void commit(InventoryDeltaDraft draft) {
		lastInventorySnapshot = draft.currentSnapshot();
	}

	public void reset() {
		lastInventorySnapshot = List.of();
	}

	private static List<TraceEvent.InventorySlotDelta> buildInventorySlotDeltas(
		List<TraceEvent.ItemStackSnapshot> previousSnapshot,
		List<TraceEvent.ItemStackSnapshot> currentSnapshot
	) {
		int size = Math.min(previousSnapshot.size(), currentSnapshot.size());
		List<TraceEvent.InventorySlotDelta> changedSlots = new ArrayList<>();
		for (int slot = 0; slot < size; slot++) {
			TraceEvent.ItemStackSnapshot previous = previousSnapshot.get(slot);
			TraceEvent.ItemStackSnapshot current = currentSnapshot.get(slot);
			if (Objects.equals(previous, current)) {
				continue;
			}
			changedSlots.add(new TraceEvent.InventorySlotDelta(slot, previous, current));
			if (changedSlots.size() >= MAX_INVENTORY_SLOT_DELTAS) {
				break;
			}
		}
		return changedSlots;
	}

	public record InventoryDeltaDraft(
		List<TraceEvent.InventorySlotDelta> changedSlots,
		List<TraceEvent.ItemStackSnapshot> currentSnapshot
	) {
		public InventoryDeltaDraft {
			changedSlots = List.copyOf(changedSlots);
			currentSnapshot = List.copyOf(currentSnapshot);
		}
	}
}

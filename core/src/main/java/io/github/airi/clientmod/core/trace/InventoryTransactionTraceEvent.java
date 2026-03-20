package io.github.airi.clientmod.core.trace;

import java.util.List;

public record InventoryTransactionTraceEvent(
	long sequence,
	long capturedAtMillis,
	long worldTick,
	String dimensionKey,
	String containerKind,
	String source,
	List<TraceEvent.InventorySlotDelta> changedSlots
) implements TraceEvent {
	public InventoryTransactionTraceEvent {
		changedSlots = List.copyOf(changedSlots);
	}
}

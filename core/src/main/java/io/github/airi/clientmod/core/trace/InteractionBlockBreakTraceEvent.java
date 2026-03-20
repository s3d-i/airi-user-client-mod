package io.github.airi.clientmod.core.trace;

public record InteractionBlockBreakTraceEvent(
	long sequence,
	long capturedAtMillis,
	long worldTick,
	String dimensionKey,
	TraceEvent.BlockReference block,
	String hand,
	int selectedSlot,
	TraceEvent.ItemStackSnapshot heldItem
) implements TraceEvent {
}

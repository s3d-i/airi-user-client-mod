package io.github.airi.clientmod.core.trace;

public record InteractionItemUseAttemptTraceEvent(
	long sequence,
	long capturedAtMillis,
	long worldTick,
	String dimensionKey,
	String hand,
	int selectedSlot,
	TraceEvent.ItemStackSnapshot heldItem
) implements TraceEvent {
}

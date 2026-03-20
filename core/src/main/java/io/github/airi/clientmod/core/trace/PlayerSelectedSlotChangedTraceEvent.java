package io.github.airi.clientmod.core.trace;

public record PlayerSelectedSlotChangedTraceEvent(
	long sequence,
	long capturedAtMillis,
	long worldTick,
	String dimensionKey,
	int previousSelectedSlot,
	int selectedSlot,
	TraceEvent.ItemStackSnapshot mainHand,
	TraceEvent.ItemStackSnapshot offHand
) implements TraceEvent {
}

package io.github.airi.clientmod.core.trace;

public record PlayerHandStateChangedTraceEvent(
	long sequence,
	long capturedAtMillis,
	long worldTick,
	String dimensionKey,
	int selectedSlot,
	TraceEvent.ItemStackSnapshot mainHand,
	TraceEvent.ItemStackSnapshot offHand
) implements TraceEvent {
}

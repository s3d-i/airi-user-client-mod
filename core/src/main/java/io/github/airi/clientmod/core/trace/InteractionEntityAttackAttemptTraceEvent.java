package io.github.airi.clientmod.core.trace;

public record InteractionEntityAttackAttemptTraceEvent(
	long sequence,
	long capturedAtMillis,
	long worldTick,
	String dimensionKey,
	TraceEvent.LookTargetEntity entity,
	String hand,
	int selectedSlot,
	TraceEvent.ItemStackSnapshot heldItem
) implements TraceEvent {
}

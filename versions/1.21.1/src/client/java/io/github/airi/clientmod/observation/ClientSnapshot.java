package io.github.airi.clientmod.observation;

import java.util.List;

import io.github.airi.clientmod.core.trace.TraceEvent;

public record ClientSnapshot(
	long worldTick,
	String dimensionKey,
	int fps,
	Position position,
	Velocity velocity,
	TraceEvent.LookTarget lookTarget,
	String targetDescription,
	int selectedSlot,
	TraceEvent.ItemStackSnapshot mainHand,
	TraceEvent.ItemStackSnapshot offHand,
	List<TraceEvent.ItemStackSnapshot> inventorySnapshot
) {
	public ClientSnapshot {
		inventorySnapshot = List.copyOf(inventorySnapshot);
	}

	public record Position(double x, double y, double z) {
	}

	public record Velocity(double x, double y, double z) {
	}
}

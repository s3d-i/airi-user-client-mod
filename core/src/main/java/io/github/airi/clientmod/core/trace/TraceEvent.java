package io.github.airi.clientmod.core.trace;

public sealed interface TraceEvent permits ObservationSample, PlayerLookTargetChangedTraceEvent, PlayerSelectedSlotChangedTraceEvent, PlayerHandStateChangedTraceEvent, InteractionBlockBreakTraceEvent, InventoryTransactionTraceEvent {
	long sequence();

	long capturedAtMillis();

	long worldTick();

	String dimensionKey();

	record BlockPosition(int x, int y, int z) {
	}

	record ItemStackSnapshot(String itemId, int count, int damage, int maxDamage) {
	}

	record BlockReference(String blockId, BlockPosition position, String hitFace) {
	}

	record LookTargetEntity(String entityTypeId, Integer entityId) {
	}

	record LookTarget(String kind, String targetDescription, BlockReference block, LookTargetEntity entity) {
	}

	record InventorySlotDelta(int slot, ItemStackSnapshot previous, ItemStackSnapshot current) {
	}
}

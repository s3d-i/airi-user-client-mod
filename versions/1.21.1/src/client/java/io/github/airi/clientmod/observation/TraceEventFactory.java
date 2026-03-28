package io.github.airi.clientmod.observation;

import io.github.airi.clientmod.core.trace.InteractionBlockAttackAttemptTraceEvent;
import io.github.airi.clientmod.core.trace.InteractionBlockUseAttemptTraceEvent;
import io.github.airi.clientmod.core.trace.InteractionBlockBreakSuccessTraceEvent;
import io.github.airi.clientmod.core.trace.InteractionEntityAttackAttemptTraceEvent;
import io.github.airi.clientmod.core.trace.InteractionEntityUseAttemptTraceEvent;
import io.github.airi.clientmod.core.trace.InteractionItemUseAttemptTraceEvent;
import io.github.airi.clientmod.core.trace.InventoryTransactionTraceEvent;
import io.github.airi.clientmod.core.trace.PlayerHandStateChangedTraceEvent;
import io.github.airi.clientmod.core.trace.PlayerLookTargetChangedTraceEvent;
import io.github.airi.clientmod.core.trace.PlayerMotionSampleTraceEvent;
import io.github.airi.clientmod.core.trace.PlayerSelectedSlotChangedTraceEvent;
import io.github.airi.clientmod.core.trace.TraceEvent;
import io.github.airi.clientmod.session.WorldSessionTracker;
import net.minecraft.util.math.BlockPos;

public final class TraceEventFactory {
	private static final String INVENTORY_CONTAINER_KIND = "player_inventory";
	private static final String INVENTORY_SOURCE = "player_inventory.scan";

	public PlayerMotionSampleTraceEvent createPlayerMotionSample(
		WorldSessionTracker.SampleTraceContext traceContext,
		ClientSnapshot snapshot
	) {
		return new PlayerMotionSampleTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			snapshot.worldTick(),
			snapshot.dimensionKey(),
			snapshot.position().x(),
			snapshot.position().y(),
			snapshot.position().z(),
			snapshot.velocity().x(),
			snapshot.velocity().y(),
			snapshot.velocity().z()
		);
	}

	public PlayerLookTargetChangedTraceEvent createLookTargetChanged(
		WorldSessionTracker.SampleTraceContext traceContext,
		ClientSnapshot snapshot,
		LookTargetChangeDetector.LookTargetChangeDraft draft
	) {
		return new PlayerLookTargetChangedTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			snapshot.worldTick(),
			snapshot.dimensionKey(),
			draft.target()
		);
	}

	public PlayerSelectedSlotChangedTraceEvent createSelectedSlotChanged(
		WorldSessionTracker.SampleTraceContext traceContext,
		ClientSnapshot snapshot,
		SelectedSlotChangeDetector.SelectedSlotChangeDraft draft
	) {
		return new PlayerSelectedSlotChangedTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			snapshot.worldTick(),
			snapshot.dimensionKey(),
			draft.previousSelectedSlot(),
			draft.selectedSlot(),
			draft.mainHand(),
			draft.offHand()
		);
	}

	public PlayerHandStateChangedTraceEvent createHandStateChanged(
		WorldSessionTracker.SampleTraceContext traceContext,
		ClientSnapshot snapshot,
		HandStateChangeDetector.HandStateChangeDraft draft
	) {
		return new PlayerHandStateChangedTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			snapshot.worldTick(),
			snapshot.dimensionKey(),
			draft.selectedSlot(),
			draft.mainHand(),
			draft.offHand()
		);
	}

	public InventoryTransactionTraceEvent createInventoryTransaction(
		WorldSessionTracker.SampleTraceContext traceContext,
		ClientSnapshot snapshot,
		InventoryDeltaDetector.InventoryDeltaDraft draft
	) {
		return new InventoryTransactionTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			snapshot.worldTick(),
			snapshot.dimensionKey(),
			INVENTORY_CONTAINER_KIND,
			INVENTORY_SOURCE,
			draft.changedSlots()
		);
	}

	public InteractionBlockAttackAttemptTraceEvent createBlockAttackAttempt(
		WorldSessionTracker.SampleTraceContext traceContext,
		long worldTick,
		String dimensionKey,
		String blockId,
		BlockPos pos,
		String hitFace,
		String hand,
		int selectedSlot,
		TraceEvent.ItemStackSnapshot heldItem
	) {
		return new InteractionBlockAttackAttemptTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			worldTick,
			dimensionKey,
			createBlockReference(blockId, pos, hitFace),
			hand,
			selectedSlot,
			heldItem
		);
	}

	public InteractionItemUseAttemptTraceEvent createItemUseAttempt(
		WorldSessionTracker.SampleTraceContext traceContext,
		long worldTick,
		String dimensionKey,
		String hand,
		int selectedSlot,
		TraceEvent.ItemStackSnapshot heldItem
	) {
		return new InteractionItemUseAttemptTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			worldTick,
			dimensionKey,
			hand,
			selectedSlot,
			heldItem
		);
	}

	public InteractionBlockUseAttemptTraceEvent createBlockUseAttempt(
		WorldSessionTracker.SampleTraceContext traceContext,
		long worldTick,
		String dimensionKey,
		String blockId,
		BlockPos pos,
		String hitFace,
		String hand,
		int selectedSlot,
		TraceEvent.ItemStackSnapshot heldItem
	) {
		return new InteractionBlockUseAttemptTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			worldTick,
			dimensionKey,
			createBlockReference(blockId, pos, hitFace),
			hand,
			selectedSlot,
			heldItem
		);
	}

	public InteractionEntityUseAttemptTraceEvent createEntityUseAttempt(
		WorldSessionTracker.SampleTraceContext traceContext,
		long worldTick,
		String dimensionKey,
		TraceEvent.LookTargetEntity entity,
		String hand,
		int selectedSlot,
		TraceEvent.ItemStackSnapshot heldItem
	) {
		return new InteractionEntityUseAttemptTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			worldTick,
			dimensionKey,
			entity,
			hand,
			selectedSlot,
			heldItem
		);
	}

	public InteractionEntityAttackAttemptTraceEvent createEntityAttackAttempt(
		WorldSessionTracker.SampleTraceContext traceContext,
		long worldTick,
		String dimensionKey,
		TraceEvent.LookTargetEntity entity,
		String hand,
		int selectedSlot,
		TraceEvent.ItemStackSnapshot heldItem
	) {
		return new InteractionEntityAttackAttemptTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			worldTick,
			dimensionKey,
			entity,
			hand,
			selectedSlot,
			heldItem
		);
	}

	public InteractionBlockBreakSuccessTraceEvent createBlockBreakSuccess(
		WorldSessionTracker.SampleTraceContext traceContext,
		long worldTick,
		String dimensionKey,
		String blockId,
		BlockPos pos,
		String hitFace,
		String hand,
		int selectedSlot,
		TraceEvent.ItemStackSnapshot heldItem
	) {
		return new InteractionBlockBreakSuccessTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			worldTick,
			dimensionKey,
			createBlockReference(blockId, pos, hitFace),
			hand,
			selectedSlot,
			heldItem
		);
	}

	private static TraceEvent.BlockReference createBlockReference(String blockId, BlockPos pos, String hitFace) {
		return new TraceEvent.BlockReference(
			blockId,
			new TraceEvent.BlockPosition(pos.getX(), pos.getY(), pos.getZ()),
			hitFace
		);
	}
}

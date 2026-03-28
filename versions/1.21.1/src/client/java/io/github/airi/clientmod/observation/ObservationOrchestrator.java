package io.github.airi.clientmod.observation;

import io.github.airi.clientmod.core.trace.ObservationEmitter;
import io.github.airi.clientmod.session.WorldSessionTracker;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class ObservationOrchestrator {
	private final ObservationEmitter emitter;
	private final WorldSessionTracker worldSessionTracker;
	private final ClientSnapshotReader snapshotReader;
	private final PeriodicMotionSampler periodicMotionSampler;
	private final LookTargetChangeDetector lookTargetChangeDetector;
	private final SelectedSlotChangeDetector selectedSlotChangeDetector;
	private final HandStateChangeDetector handStateChangeDetector;
	private final InventoryDeltaDetector inventoryDeltaDetector;
	private final BlockBreakInteractionMatcher blockBreakInteractionMatcher;
	private final TraceEventFactory traceEventFactory;

	public ObservationOrchestrator(ObservationEmitter emitter, WorldSessionTracker worldSessionTracker) {
		this(
			emitter,
			worldSessionTracker,
			new ClientSnapshotReader(),
			new PeriodicMotionSampler(),
			new LookTargetChangeDetector(),
			new SelectedSlotChangeDetector(),
			new HandStateChangeDetector(),
			new InventoryDeltaDetector(),
			new BlockBreakInteractionMatcher(),
			new TraceEventFactory()
		);
	}

	ObservationOrchestrator(
		ObservationEmitter emitter,
		WorldSessionTracker worldSessionTracker,
		ClientSnapshotReader snapshotReader,
		PeriodicMotionSampler periodicMotionSampler,
		LookTargetChangeDetector lookTargetChangeDetector,
		SelectedSlotChangeDetector selectedSlotChangeDetector,
		HandStateChangeDetector handStateChangeDetector,
		InventoryDeltaDetector inventoryDeltaDetector,
		BlockBreakInteractionMatcher blockBreakInteractionMatcher,
		TraceEventFactory traceEventFactory
	) {
		this.emitter = emitter;
		this.worldSessionTracker = worldSessionTracker;
		this.snapshotReader = snapshotReader;
		this.periodicMotionSampler = periodicMotionSampler;
		this.lookTargetChangeDetector = lookTargetChangeDetector;
		this.selectedSlotChangeDetector = selectedSlotChangeDetector;
		this.handStateChangeDetector = handStateChangeDetector;
		this.inventoryDeltaDetector = inventoryDeltaDetector;
		this.blockBreakInteractionMatcher = blockBreakInteractionMatcher;
		this.traceEventFactory = traceEventFactory;
	}

	public void onEndClientTick(MinecraftClient client) {
		if (client.world == null || client.player == null || !worldSessionTracker.hasActiveSession()) {
			resetTransientState();
			return;
		}

		ClientSnapshot snapshot = snapshotReader.read(client);
		emitLookTargetChangeIfNeeded(snapshot);
		emitSelectedSlotChangeIfNeeded(snapshot);
		emitHandStateChangeIfNeeded(snapshot);
		emitInventoryDeltaIfNeeded(snapshot);

		if (!periodicMotionSampler.shouldEmitThisTick()) {
			return;
		}

		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		emitter.emit(traceEventFactory.createPlayerMotionSample(traceContext, snapshot));
	}

	public void onAttackBlock(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
		if (!world.isClient() || !worldSessionTracker.hasActiveSession()) {
			return;
		}

		String dimensionKey = world.getRegistryKey().getValue().toString();
		String hitFace = direction == null ? null : direction.asString();
		String handKey = hand == Hand.OFF_HAND ? "off_hand" : "main_hand";
		long worldTick = world.getTime();
		BlockPos immutablePos = pos.toImmutable();

		blockBreakInteractionMatcher.registerAttempt(
			new BlockBreakInteractionMatcher.BlockBreakAttempt(
				dimensionKey,
				worldTick,
				immutablePos,
				hitFace,
				handKey
			)
		);

		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		String blockId = Registries.BLOCK.getId(world.getBlockState(immutablePos).getBlock()).toString();
		emitter.emit(traceEventFactory.createBlockAttackAttempt(
			traceContext,
			worldTick,
			dimensionKey,
			blockId,
			immutablePos,
			hitFace,
			handKey,
			player.getInventory().selectedSlot,
			snapshotReader.captureItemStack(player.getMainHandStack())
		));
	}

	public void onAfterClientBlockBreak(ClientWorld world, ClientPlayerEntity player, BlockPos pos, BlockState state) {
		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		String dimensionKey = world.getRegistryKey().getValue().toString();
		long worldTick = world.getTime();
		BlockBreakInteractionMatcher.BlockBreakMatchResult match = blockBreakInteractionMatcher.matchSuccess(
			new BlockBreakInteractionMatcher.BlockBreakSuccessCandidate(dimensionKey, worldTick, pos)
		);

		String hitFace = match == null ? null : match.hitFace();
		String hand = match == null ? "main_hand" : match.hand();
		emitter.emit(traceEventFactory.createBlockBreakSuccess(
			traceContext,
			worldTick,
			dimensionKey,
			Registries.BLOCK.getId(state.getBlock()).toString(),
			pos,
			hitFace,
			hand,
			player.getInventory().selectedSlot,
			snapshotReader.captureItemStack(player.getMainHandStack())
		));
	}

	private void emitLookTargetChangeIfNeeded(ClientSnapshot snapshot) {
		LookTargetChangeDetector.LookTargetChangeDraft draft = lookTargetChangeDetector.detect(snapshot);
		if (draft == null) {
			return;
		}

		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		lookTargetChangeDetector.commit(draft);
		emitter.emit(traceEventFactory.createLookTargetChanged(traceContext, snapshot, draft));
	}

	private void emitSelectedSlotChangeIfNeeded(ClientSnapshot snapshot) {
		SelectedSlotChangeDetector.SelectedSlotChangeDraft draft = selectedSlotChangeDetector.detect(snapshot);
		if (draft == null) {
			return;
		}

		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		emitter.emit(traceEventFactory.createSelectedSlotChanged(traceContext, snapshot, draft));
		selectedSlotChangeDetector.commit(draft);
	}

	private void emitHandStateChangeIfNeeded(ClientSnapshot snapshot) {
		HandStateChangeDetector.HandStateChangeDraft draft = handStateChangeDetector.detect(snapshot);
		if (draft == null) {
			return;
		}

		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		emitter.emit(traceEventFactory.createHandStateChanged(traceContext, snapshot, draft));
		handStateChangeDetector.commit(draft);
	}

	private void emitInventoryDeltaIfNeeded(ClientSnapshot snapshot) {
		InventoryDeltaDetector.InventoryDeltaDraft draft = inventoryDeltaDetector.detect(snapshot);
		if (draft == null) {
			return;
		}

		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		inventoryDeltaDetector.commit(draft);
		emitter.emit(traceEventFactory.createInventoryTransaction(traceContext, snapshot, draft));
	}

	private void resetTransientState() {
		periodicMotionSampler.reset();
		lookTargetChangeDetector.reset();
		selectedSlotChangeDetector.reset();
		handStateChangeDetector.reset();
		inventoryDeltaDetector.reset();
		blockBreakInteractionMatcher.reset();
	}
}

package io.github.airi.clientmod.observation;

import io.github.airi.clientmod.core.trace.ObservationEmitter;
import io.github.airi.clientmod.core.trace.TraceEvent;
import io.github.airi.clientmod.session.WorldSessionTracker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class ObservationOrchestrator {
	// TODO: Rename this class/file to CaptureCoordinator once external references are updated.
	private final ObservationEmitter emitter;
	private final WorldSessionTracker worldSessionTracker;
	private final ClientSnapshotReader snapshotReader;
	private final List<CaptureStage> captureStages;
	private final InputInteractionCaptureStage inputInteractionCaptureStage;
	private final BlockBreakCaptureStage blockBreakCaptureStage;

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
		TraceEventFactory traceEventFactory
	) {
		this.emitter = emitter;
		this.worldSessionTracker = worldSessionTracker;
		this.snapshotReader = snapshotReader;
		this.inputInteractionCaptureStage = new InputInteractionCaptureStage(snapshotReader, traceEventFactory);
		this.blockBreakCaptureStage = new BlockBreakCaptureStage(snapshotReader, traceEventFactory);
		this.captureStages = List.of(
			new ChangedStateCaptureStage(
				lookTargetChangeDetector,
				selectedSlotChangeDetector,
				handStateChangeDetector,
				traceEventFactory
			),
			inputInteractionCaptureStage,
			blockBreakCaptureStage,
			new InventoryDeltaCaptureStage(inventoryDeltaDetector, traceEventFactory),
			new PeriodicSampleCaptureStage(periodicMotionSampler, traceEventFactory)
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
		BlockBreakInteractionMatcher ignoredBlockBreakInteractionMatcher,
		TraceEventFactory traceEventFactory
	) {
		this(
			emitter,
			worldSessionTracker,
			snapshotReader,
			periodicMotionSampler,
			lookTargetChangeDetector,
			selectedSlotChangeDetector,
			handStateChangeDetector,
			inventoryDeltaDetector,
			traceEventFactory
		);
	}

	public void onEndClientTick(MinecraftClient client) {
		if (client.world == null || client.player == null || !worldSessionTracker.hasActiveSession()) {
			resetTransientState();
			return;
		}

		ClientSnapshot snapshot = snapshotReader.read(client);
		DraftCollector draftCollector = new DraftCollector();
		for (CaptureStage stage : captureStages) {
			stage.collect(snapshot, draftCollector);
		}

		List<DraftEvent> drafts = draftCollector.sorted();
		if (drafts.isEmpty()) {
			return;
		}

		List<WorldSessionTracker.SampleTraceContext> traceContexts = new ArrayList<>(drafts.size());
		for (int i = 0; i < drafts.size(); i++) {
			WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
			if (traceContext == null) {
				return;
			}
			traceContexts.add(traceContext);
		}

		for (int i = 0; i < drafts.size(); i++) {
			emitter.emit(drafts.get(i).materialize(traceContexts.get(i)));
		}
		draftCollector.commit(drafts);
	}

	public void onAttackBlock(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
		if (
			player == null ||
			world == null ||
			pos == null ||
			!world.isClient() ||
			!worldSessionTracker.hasActiveSession()
		) {
			return;
		}

		// Callback handlers only record raw interaction signals.
		// Emission is centralized in onEndClientTick for deterministic ordering and context allocation.
		blockBreakCaptureStage.recordAttempt(player, world, hand, pos, direction);
	}

	public void onUseItem(PlayerEntity player, World world, Hand hand) {
		if (player == null || world == null || !world.isClient() || !worldSessionTracker.hasActiveSession()) {
			return;
		}

		inputInteractionCaptureStage.recordItemUseAttempt(player, world, hand);
	}

	public void onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
		if (
			player == null ||
			world == null ||
			hitResult == null ||
			!world.isClient() ||
			!worldSessionTracker.hasActiveSession()
		) {
			return;
		}

		inputInteractionCaptureStage.recordBlockUseAttempt(player, world, hand, hitResult);
	}

	public void onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
		if (
			player == null ||
			world == null ||
			entity == null ||
			!world.isClient() ||
			!worldSessionTracker.hasActiveSession()
		) {
			return;
		}

		inputInteractionCaptureStage.recordEntityUseAttempt(player, world, hand, entity, hitResult);
	}

	public void onAttackEntity(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
		if (
			player == null ||
			world == null ||
			entity == null ||
			!world.isClient() ||
			!worldSessionTracker.hasActiveSession()
		) {
			return;
		}

		inputInteractionCaptureStage.recordEntityAttackAttempt(player, world, hand, entity, hitResult);
	}

	public void onAfterClientBlockBreak(ClientWorld world, ClientPlayerEntity player, BlockPos pos, BlockState state) {
		if (world == null || player == null || pos == null || state == null || !worldSessionTracker.hasActiveSession()) {
			return;
		}

		// Block break signals are buffered so attempt/outcome matching and emission are resolved during tick flush.
		// This keeps ordering deterministic across all capture stages and avoids callback-time context allocation.
		blockBreakCaptureStage.recordOutcome(world, player, pos, state);
	}

	private void resetTransientState() {
		for (CaptureStage stage : captureStages) {
			stage.reset();
		}
	}

	private interface CaptureStage {
		void collect(ClientSnapshot snapshot, DraftCollector collector);

		void reset();
	}

	private enum StageOrder {
		// Fixed ordering is intentional to keep raw evidence replay deterministic:
		// changed state transitions -> input attempts -> block break interactions -> inventory delta -> periodic samples.
		CHANGED_STATE,
		INPUT_INTERACTION,
		BLOCK_BREAK,
		INVENTORY_DELTA,
		PERIODIC_SAMPLE
	}

	private interface DraftMaterializer {
		TraceEvent materialize(WorldSessionTracker.SampleTraceContext traceContext);
	}

	private record DraftEvent(
		StageOrder stageOrder,
		long insertionOrder,
		DraftMaterializer materializer,
		Runnable commitAction
	) {
		TraceEvent materialize(WorldSessionTracker.SampleTraceContext traceContext) {
			return materializer.materialize(traceContext);
		}

		void commit() {
			commitAction.run();
		}
	}

	private static final class DraftCollector {
		private static final Runnable NOOP = () -> {
		};

		private final List<DraftEvent> drafts = new ArrayList<>();
		private final List<Runnable> additionalCommits = new ArrayList<>();
		private long nextInsertionOrder;

		void add(StageOrder stageOrder, DraftMaterializer materializer) {
			add(stageOrder, materializer, NOOP);
		}

		void add(StageOrder stageOrder, DraftMaterializer materializer, Runnable commitAction) {
			drafts.add(new DraftEvent(stageOrder, nextInsertionOrder++, materializer, commitAction));
		}

		void addCommit(Runnable commitAction) {
			additionalCommits.add(commitAction);
		}

		List<DraftEvent> sorted() {
			List<DraftEvent> sorted = new ArrayList<>(drafts);
			sorted.sort(
				Comparator
					.comparing((DraftEvent draft) -> draft.stageOrder().ordinal())
					.thenComparing(DraftEvent::insertionOrder)
			);
			return sorted;
		}

		void commit(List<DraftEvent> orderedDrafts) {
			for (DraftEvent draft : orderedDrafts) {
				draft.commit();
			}
			for (Runnable additionalCommit : additionalCommits) {
				additionalCommit.run();
			}
		}
	}

	private static final class ChangedStateCaptureStage implements CaptureStage {
		private final LookTargetChangeDetector lookTargetChangeDetector;
		private final SelectedSlotChangeDetector selectedSlotChangeDetector;
		private final HandStateChangeDetector handStateChangeDetector;
		private final TraceEventFactory traceEventFactory;

		ChangedStateCaptureStage(
			LookTargetChangeDetector lookTargetChangeDetector,
			SelectedSlotChangeDetector selectedSlotChangeDetector,
			HandStateChangeDetector handStateChangeDetector,
			TraceEventFactory traceEventFactory
		) {
			this.lookTargetChangeDetector = lookTargetChangeDetector;
			this.selectedSlotChangeDetector = selectedSlotChangeDetector;
			this.handStateChangeDetector = handStateChangeDetector;
			this.traceEventFactory = traceEventFactory;
		}

		@Override
		public void collect(ClientSnapshot snapshot, DraftCollector collector) {
			LookTargetChangeDetector.LookTargetChangeDraft lookTargetDraft = lookTargetChangeDetector.detect(snapshot);
			if (lookTargetDraft != null) {
				collector.add(
					StageOrder.CHANGED_STATE,
					traceContext -> traceEventFactory.createLookTargetChanged(traceContext, snapshot, lookTargetDraft),
					() -> lookTargetChangeDetector.commit(lookTargetDraft)
				);
			}

			SelectedSlotChangeDetector.SelectedSlotChangeDraft selectedSlotDraft = selectedSlotChangeDetector.detect(snapshot);
			if (selectedSlotDraft != null) {
				collector.add(
					StageOrder.CHANGED_STATE,
					traceContext -> traceEventFactory.createSelectedSlotChanged(traceContext, snapshot, selectedSlotDraft),
					() -> selectedSlotChangeDetector.commit(selectedSlotDraft)
				);
			}

			HandStateChangeDetector.HandStateChangeDraft handStateDraft = handStateChangeDetector.detect(snapshot);
			if (handStateDraft != null) {
				collector.add(
					StageOrder.CHANGED_STATE,
					traceContext -> traceEventFactory.createHandStateChanged(traceContext, snapshot, handStateDraft),
					() -> handStateChangeDetector.commit(handStateDraft)
				);
			}
		}

		@Override
		public void reset() {
			lookTargetChangeDetector.reset();
			selectedSlotChangeDetector.reset();
			handStateChangeDetector.reset();
		}
	}

	private static final class InputInteractionCaptureStage implements CaptureStage {
		private static final String MAIN_HAND_KEY = "main_hand";
		private static final String OFF_HAND_KEY = "off_hand";

		private final ClientSnapshotReader snapshotReader;
		private final TraceEventFactory traceEventFactory;
		private final List<InputInteractionSignal> pendingSignals = new ArrayList<>();
		private long nextSignalOrder = 1L;

		InputInteractionCaptureStage(ClientSnapshotReader snapshotReader, TraceEventFactory traceEventFactory) {
			this.snapshotReader = snapshotReader;
			this.traceEventFactory = traceEventFactory;
		}

		void recordItemUseAttempt(PlayerEntity player, World world, Hand hand) {
			String handKey = toHandKey(hand);
			pendingSignals.add(
				new ItemUseAttemptSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					handKey,
					player.getInventory().selectedSlot,
					captureHeldItem(player, hand)
				)
			);
		}

		void recordBlockUseAttempt(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
			BlockPos immutablePos = hitResult.getBlockPos().toImmutable();
			String handKey = toHandKey(hand);
			pendingSignals.add(
				new BlockUseAttemptSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					Registries.BLOCK.getId(world.getBlockState(immutablePos).getBlock()).toString(),
					immutablePos,
					hitResult.getSide() == null ? null : hitResult.getSide().asString(),
					handKey,
					player.getInventory().selectedSlot,
					captureHeldItem(player, hand)
				)
			);
		}

		void recordEntityUseAttempt(
			PlayerEntity player,
			World world,
			Hand hand,
			Entity entity,
			EntityHitResult ignoredHitResult
		) {
			String handKey = toHandKey(hand);
			pendingSignals.add(
				new EntityUseAttemptSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					captureEntityReference(entity),
					handKey,
					player.getInventory().selectedSlot,
					captureHeldItem(player, hand)
				)
			);
		}

		void recordEntityAttackAttempt(
			PlayerEntity player,
			World world,
			Hand hand,
			Entity entity,
			EntityHitResult ignoredHitResult
		) {
			String handKey = toHandKey(hand);
			pendingSignals.add(
				new EntityAttackAttemptSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					captureEntityReference(entity),
					handKey,
					player.getInventory().selectedSlot,
					captureHeldItem(player, hand)
				)
			);
		}

		@Override
		public void collect(ClientSnapshot snapshot, DraftCollector collector) {
			if (pendingSignals.isEmpty()) {
				return;
			}

			List<InputInteractionSignal> orderedSignals = new ArrayList<>(pendingSignals);
			orderedSignals.sort(Comparator.comparingLong(InputInteractionSignal::signalOrder));

			for (InputInteractionSignal signal : orderedSignals) {
				if (signal instanceof ItemUseAttemptSignal itemUseAttemptSignal) {
					collector.add(
						StageOrder.INPUT_INTERACTION,
						traceContext -> traceEventFactory.createItemUseAttempt(
							traceContext,
							itemUseAttemptSignal.worldTick(),
							itemUseAttemptSignal.dimensionKey(),
							itemUseAttemptSignal.hand(),
							itemUseAttemptSignal.selectedSlot(),
							itemUseAttemptSignal.heldItem()
						)
					);
					continue;
				}

				if (signal instanceof BlockUseAttemptSignal blockUseAttemptSignal) {
					collector.add(
						StageOrder.INPUT_INTERACTION,
						traceContext -> traceEventFactory.createBlockUseAttempt(
							traceContext,
							blockUseAttemptSignal.worldTick(),
							blockUseAttemptSignal.dimensionKey(),
							blockUseAttemptSignal.blockId(),
							blockUseAttemptSignal.pos(),
							blockUseAttemptSignal.hitFace(),
							blockUseAttemptSignal.hand(),
							blockUseAttemptSignal.selectedSlot(),
							blockUseAttemptSignal.heldItem()
						)
					);
					continue;
				}

				if (signal instanceof EntityUseAttemptSignal entityUseAttemptSignal) {
					collector.add(
						StageOrder.INPUT_INTERACTION,
						traceContext -> traceEventFactory.createEntityUseAttempt(
							traceContext,
							entityUseAttemptSignal.worldTick(),
							entityUseAttemptSignal.dimensionKey(),
							entityUseAttemptSignal.entity(),
							entityUseAttemptSignal.hand(),
							entityUseAttemptSignal.selectedSlot(),
							entityUseAttemptSignal.heldItem()
						)
					);
					continue;
				}

				EntityAttackAttemptSignal entityAttackAttemptSignal = (EntityAttackAttemptSignal) signal;
				collector.add(
					StageOrder.INPUT_INTERACTION,
					traceContext -> traceEventFactory.createEntityAttackAttempt(
						traceContext,
						entityAttackAttemptSignal.worldTick(),
						entityAttackAttemptSignal.dimensionKey(),
						entityAttackAttemptSignal.entity(),
						entityAttackAttemptSignal.hand(),
						entityAttackAttemptSignal.selectedSlot(),
						entityAttackAttemptSignal.heldItem()
					)
				);
			}

			collector.addCommit(() -> pendingSignals.clear());
		}

		@Override
		public void reset() {
			pendingSignals.clear();
			nextSignalOrder = 1L;
		}

		private TraceEvent.ItemStackSnapshot captureHeldItem(PlayerEntity player, Hand hand) {
			return hand == Hand.OFF_HAND
				? snapshotReader.captureItemStack(player.getOffHandStack())
				: snapshotReader.captureItemStack(player.getMainHandStack());
		}

		private static TraceEvent.LookTargetEntity captureEntityReference(Entity entity) {
			return new TraceEvent.LookTargetEntity(
				Registries.ENTITY_TYPE.getId(entity.getType()).toString(),
				entity.getId()
			);
		}

		private static String toHandKey(Hand hand) {
			return hand == Hand.OFF_HAND ? OFF_HAND_KEY : MAIN_HAND_KEY;
		}

		private interface InputInteractionSignal {
			long signalOrder();
		}

		private record ItemUseAttemptSignal(
			long signalOrder,
			String dimensionKey,
			long worldTick,
			String hand,
			int selectedSlot,
			TraceEvent.ItemStackSnapshot heldItem
		) implements InputInteractionSignal {
		}

		private record BlockUseAttemptSignal(
			long signalOrder,
			String dimensionKey,
			long worldTick,
			String blockId,
			BlockPos pos,
			String hitFace,
			String hand,
			int selectedSlot,
			TraceEvent.ItemStackSnapshot heldItem
		) implements InputInteractionSignal {
			private BlockUseAttemptSignal {
				pos = pos.toImmutable();
			}
		}

		private record EntityUseAttemptSignal(
			long signalOrder,
			String dimensionKey,
			long worldTick,
			TraceEvent.LookTargetEntity entity,
			String hand,
			int selectedSlot,
			TraceEvent.ItemStackSnapshot heldItem
		) implements InputInteractionSignal {
		}

		private record EntityAttackAttemptSignal(
			long signalOrder,
			String dimensionKey,
			long worldTick,
			TraceEvent.LookTargetEntity entity,
			String hand,
			int selectedSlot,
			TraceEvent.ItemStackSnapshot heldItem
		) implements InputInteractionSignal {
		}
	}

	private static final class InventoryDeltaCaptureStage implements CaptureStage {
		private final InventoryDeltaDetector inventoryDeltaDetector;
		private final TraceEventFactory traceEventFactory;

		InventoryDeltaCaptureStage(InventoryDeltaDetector inventoryDeltaDetector, TraceEventFactory traceEventFactory) {
			this.inventoryDeltaDetector = inventoryDeltaDetector;
			this.traceEventFactory = traceEventFactory;
		}

		@Override
		public void collect(ClientSnapshot snapshot, DraftCollector collector) {
			InventoryDeltaDetector.InventoryDeltaDraft inventoryDeltaDraft = inventoryDeltaDetector.detect(snapshot);
			if (inventoryDeltaDraft == null) {
				return;
			}

			collector.add(
				StageOrder.INVENTORY_DELTA,
				traceContext -> traceEventFactory.createInventoryTransaction(traceContext, snapshot, inventoryDeltaDraft),
				() -> inventoryDeltaDetector.commit(inventoryDeltaDraft)
			);
		}

		@Override
		public void reset() {
			inventoryDeltaDetector.reset();
		}
	}

	private static final class PeriodicSampleCaptureStage implements CaptureStage {
		private final PeriodicMotionSampler periodicMotionSampler;
		private final TraceEventFactory traceEventFactory;

		PeriodicSampleCaptureStage(PeriodicMotionSampler periodicMotionSampler, TraceEventFactory traceEventFactory) {
			this.periodicMotionSampler = periodicMotionSampler;
			this.traceEventFactory = traceEventFactory;
		}

		@Override
		public void collect(ClientSnapshot snapshot, DraftCollector collector) {
			if (!periodicMotionSampler.shouldEmitThisTick()) {
				return;
			}

			collector.add(
				StageOrder.PERIODIC_SAMPLE,
				traceContext -> traceEventFactory.createPlayerMotionSample(traceContext, snapshot)
			);
		}

		@Override
		public void reset() {
			periodicMotionSampler.reset();
		}
	}

	private static final class BlockBreakCaptureStage implements CaptureStage {
		private static final String MAIN_HAND_KEY = "main_hand";

		private final ClientSnapshotReader snapshotReader;
		private final TraceEventFactory traceEventFactory;
		private final List<BlockBreakSignal> pendingSignals = new ArrayList<>();

		private BlockBreakPendingAttempt pendingAttempt;
		private long nextSignalOrder = 1L;

		BlockBreakCaptureStage(ClientSnapshotReader snapshotReader, TraceEventFactory traceEventFactory) {
			this.snapshotReader = snapshotReader;
			this.traceEventFactory = traceEventFactory;
		}

		void recordAttempt(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
			BlockPos immutablePos = pos.toImmutable();
			pendingSignals.add(
				new BlockBreakAttemptSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					immutablePos,
					Registries.BLOCK.getId(world.getBlockState(immutablePos).getBlock()).toString(),
					direction == null ? null : direction.asString(),
					hand == Hand.OFF_HAND ? "off_hand" : MAIN_HAND_KEY,
					player.getInventory().selectedSlot,
					snapshotReader.captureItemStack(player.getMainHandStack())
				)
			);
		}

		void recordOutcome(ClientWorld world, ClientPlayerEntity player, BlockPos pos, BlockState state) {
			pendingSignals.add(
				new BlockBreakOutcomeSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					pos.toImmutable(),
					Registries.BLOCK.getId(state.getBlock()).toString(),
					player.getInventory().selectedSlot,
					snapshotReader.captureItemStack(player.getMainHandStack())
				)
			);
		}

		@Override
		public void collect(ClientSnapshot snapshot, DraftCollector collector) {
			if (pendingSignals.isEmpty()) {
				return;
			}

			List<BlockBreakSignal> orderedSignals = new ArrayList<>(pendingSignals);
			orderedSignals.sort(Comparator.comparingLong(BlockBreakSignal::signalOrder));

			BlockBreakPendingAttempt nextPendingAttempt = pendingAttempt;
			for (BlockBreakSignal signal : orderedSignals) {
				if (signal instanceof BlockBreakAttemptSignal attemptSignal) {
					nextPendingAttempt = new BlockBreakPendingAttempt(
						attemptSignal.dimensionKey(),
						attemptSignal.worldTick(),
						attemptSignal.pos(),
						attemptSignal.hitFace(),
						attemptSignal.hand()
					);
					collector.add(
						StageOrder.BLOCK_BREAK,
						traceContext -> traceEventFactory.createBlockAttackAttempt(
							traceContext,
							attemptSignal.worldTick(),
							attemptSignal.dimensionKey(),
							attemptSignal.blockId(),
							attemptSignal.pos(),
							attemptSignal.hitFace(),
							attemptSignal.hand(),
							attemptSignal.selectedSlot(),
							attemptSignal.heldItem()
						)
					);
					continue;
				}

				BlockBreakOutcomeSignal outcomeSignal = (BlockBreakOutcomeSignal) signal;
				BlockBreakOutcomeMatchResult matchResult = matchOutcome(nextPendingAttempt, outcomeSignal);
				nextPendingAttempt = matchResult.nextPendingAttempt();

				collector.add(
					StageOrder.BLOCK_BREAK,
					traceContext -> traceEventFactory.createBlockBreakSuccess(
						traceContext,
						outcomeSignal.worldTick(),
						outcomeSignal.dimensionKey(),
						outcomeSignal.blockId(),
						outcomeSignal.pos(),
						matchResult.hitFace(),
						matchResult.hand(),
						outcomeSignal.selectedSlot(),
						outcomeSignal.heldItem()
					)
				);
			}

			BlockBreakPendingAttempt committedPendingAttempt = nextPendingAttempt;
			collector.addCommit(() -> {
				pendingAttempt = committedPendingAttempt;
				pendingSignals.clear();
			});
		}

		@Override
		public void reset() {
			pendingAttempt = null;
			pendingSignals.clear();
			nextSignalOrder = 1L;
		}

		private static BlockBreakOutcomeMatchResult matchOutcome(
			BlockBreakPendingAttempt pendingAttempt,
			BlockBreakOutcomeSignal successCandidate
		) {
			if (pendingAttempt == null) {
				return new BlockBreakOutcomeMatchResult(null, MAIN_HAND_KEY, null);
			}

			if (!Objects.equals(pendingAttempt.dimensionKey(), successCandidate.dimensionKey())) {
				return new BlockBreakOutcomeMatchResult(null, MAIN_HAND_KEY, null);
			}

			if (successCandidate.worldTick() - pendingAttempt.worldTick() > 5L) {
				return new BlockBreakOutcomeMatchResult(null, MAIN_HAND_KEY, null);
			}

			if (!pendingAttempt.pos().equals(successCandidate.pos())) {
				return new BlockBreakOutcomeMatchResult(null, MAIN_HAND_KEY, pendingAttempt);
			}

			return new BlockBreakOutcomeMatchResult(
				pendingAttempt.hitFace(),
				pendingAttempt.hand(),
				null
			);
		}

		private interface BlockBreakSignal {
			long signalOrder();
		}

		private record BlockBreakAttemptSignal(
			long signalOrder,
			String dimensionKey,
			long worldTick,
			BlockPos pos,
			String blockId,
			String hitFace,
			String hand,
			int selectedSlot,
			TraceEvent.ItemStackSnapshot heldItem
		) implements BlockBreakSignal {
			private BlockBreakAttemptSignal {
				pos = pos.toImmutable();
			}
		}

		private record BlockBreakOutcomeSignal(
			long signalOrder,
			String dimensionKey,
			long worldTick,
			BlockPos pos,
			String blockId,
			int selectedSlot,
			TraceEvent.ItemStackSnapshot heldItem
		) implements BlockBreakSignal {
			private BlockBreakOutcomeSignal {
				pos = pos.toImmutable();
			}
		}

		private record BlockBreakPendingAttempt(
			String dimensionKey,
			long worldTick,
			BlockPos pos,
			String hitFace,
			String hand
		) {
			private BlockBreakPendingAttempt {
				pos = pos.toImmutable();
			}
		}

		private record BlockBreakOutcomeMatchResult(
			String hitFace,
			String hand,
			BlockBreakPendingAttempt nextPendingAttempt
		) {
		}
	}
}

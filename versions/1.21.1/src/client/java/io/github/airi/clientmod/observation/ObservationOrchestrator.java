package io.github.airi.clientmod.observation;

import io.github.airi.clientmod.core.trace.ObservationEmitter;
import io.github.airi.clientmod.core.trace.TraceEvent;
import io.github.airi.clientmod.session.WorldSessionTracker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
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
		InteractionContextCapture interactionContextCapture = new InteractionContextCapture(snapshotReader);
		this.inputInteractionCaptureStage = new InputInteractionCaptureStage(interactionContextCapture, traceEventFactory);
		this.blockBreakCaptureStage = new BlockBreakCaptureStage(interactionContextCapture, traceEventFactory);
		// Capture stage list order is the canonical flush order for raw evidence emission.
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

	public void onEndClientTick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			resetTransientState(ResetScope.WORLD_LIFETIME_TRANSIENT);
			return;
		}
		if (!worldSessionTracker.hasActiveSession()) {
			resetTransientState(ResetScope.PER_SESSION_TRANSIENT);
			return;
		}

		ClientSnapshot snapshot = snapshotReader.read(client);
		DraftCollector draftCollector = new DraftCollector();
		for (int stageOrdinal = 0; stageOrdinal < captureStages.size(); stageOrdinal++) {
			draftCollector.beginStage(stageOrdinal);
			captureStages.get(stageOrdinal).collect(snapshot, draftCollector);
		}

		List<DraftEvent> drafts = draftCollector.ordered();
		if (drafts.isEmpty()) {
			return;
		}

		List<WorldSessionTracker.SampleTraceContext> traceContexts = worldSessionTracker.beginTraces(drafts.size());
		if (traceContexts == null) {
			// Retry policy: no commits run, so buffered callback signals remain pending for the next flush attempt.
			return;
		}

		// `seq` is canonical flush order (stage ordinal + stage-local signal order), not global callback arrival order.
		// Failure policy: commit actions only run after a full flush; a mid-batch emit failure may duplicate already
		// emitted events on retry.
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

	private void resetTransientState(ResetScope scope) {
		for (CaptureStage stage : captureStages) {
			stage.reset(scope);
		}
	}

	private interface CaptureStage {
		void collect(ClientSnapshot snapshot, DraftCollector collector);

		void reset(ResetScope scope);
	}

	private enum ResetScope {
		PER_TICK_TRANSIENT,
		PER_SESSION_TRANSIENT,
		WORLD_LIFETIME_TRANSIENT
	}

	private interface DraftMaterializer {
		TraceEvent materialize(WorldSessionTracker.SampleTraceContext traceContext);
	}

	private record InteractionActionContext(int selectedSlot, TraceEvent.ItemStackSnapshot heldItem) {
	}

	private static final class InteractionContextCapture {
		private final ClientSnapshotReader snapshotReader;

		InteractionContextCapture(ClientSnapshotReader snapshotReader) {
			this.snapshotReader = snapshotReader;
		}

		InteractionActionContext captureForHand(PlayerEntity player, Hand hand) {
			return new InteractionActionContext(player.getInventory().selectedSlot, captureHeldItem(player, hand));
		}

		InteractionActionContext captureMainHand(PlayerEntity player) {
			return new InteractionActionContext(
				player.getInventory().selectedSlot,
				snapshotReader.captureItemStack(player.getMainHandStack())
			);
		}

		private TraceEvent.ItemStackSnapshot captureHeldItem(PlayerEntity player, Hand hand) {
			return hand == Hand.OFF_HAND
				? snapshotReader.captureItemStack(player.getOffHandStack())
				: snapshotReader.captureItemStack(player.getMainHandStack());
		}
	}

	private record DraftOrder(
		int stageOrdinal,
		long stageSignalOrder,
		long sourceWorldTick,
		long insertionOrder
	) {
	}

	private record DraftEvent(DraftOrder order, DraftMaterializer materializer, Runnable commitAction) {
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
		private int currentStageOrdinal = -1;
		private long nextInsertionOrder;

		void beginStage(int stageOrdinal) {
			currentStageOrdinal = stageOrdinal;
		}

		void add(long stageSignalOrder, long sourceWorldTick, DraftMaterializer materializer) {
			add(stageSignalOrder, sourceWorldTick, materializer, NOOP);
		}

		void add(
			long stageSignalOrder,
			long sourceWorldTick,
			DraftMaterializer materializer,
			Runnable commitAction
		) {
			if (currentStageOrdinal < 0) {
				throw new IllegalStateException("DraftCollector stage ordinal is not initialized");
			}

			drafts.add(
				new DraftEvent(
					new DraftOrder(currentStageOrdinal, stageSignalOrder, sourceWorldTick, nextInsertionOrder++),
					materializer,
					commitAction
				)
			);
		}

		void addCommit(Runnable commitAction) {
			additionalCommits.add(commitAction);
		}

		List<DraftEvent> ordered() {
			List<DraftEvent> ordered = new ArrayList<>(drafts);
			ordered.sort(
				Comparator
					.comparingInt((DraftEvent draft) -> draft.order().stageOrdinal())
					.thenComparingLong(draft -> draft.order().stageSignalOrder())
					.thenComparingLong(draft -> draft.order().sourceWorldTick())
					.thenComparingLong(draft -> draft.order().insertionOrder())
			);
			return ordered;
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
			long nextStageSignalOrder = 0L;
			LookTargetChangeDetector.LookTargetChangeDraft lookTargetDraft = lookTargetChangeDetector.detect(snapshot);
			if (lookTargetDraft != null) {
				collector.add(
					nextStageSignalOrder++,
					snapshot.worldTick(),
					traceContext -> traceEventFactory.createLookTargetChanged(traceContext, snapshot, lookTargetDraft),
					() -> lookTargetChangeDetector.commit(lookTargetDraft)
				);
			}

			SelectedSlotChangeDetector.SelectedSlotChangeDraft selectedSlotDraft = selectedSlotChangeDetector.detect(snapshot);
			if (selectedSlotDraft != null) {
				collector.add(
					nextStageSignalOrder++,
					snapshot.worldTick(),
					traceContext -> traceEventFactory.createSelectedSlotChanged(traceContext, snapshot, selectedSlotDraft),
					() -> selectedSlotChangeDetector.commit(selectedSlotDraft)
				);
			}

			HandStateChangeDetector.HandStateChangeDraft handStateDraft = handStateChangeDetector.detect(snapshot);
			if (handStateDraft != null) {
				collector.add(
					nextStageSignalOrder++,
					snapshot.worldTick(),
					traceContext -> traceEventFactory.createHandStateChanged(traceContext, snapshot, handStateDraft),
					() -> handStateChangeDetector.commit(handStateDraft)
				);
			}
		}

		@Override
		public void reset(ResetScope scope) {
			lookTargetChangeDetector.reset();
			selectedSlotChangeDetector.reset();
			handStateChangeDetector.reset();
		}
	}

	private static final class InputInteractionCaptureStage implements CaptureStage {
		private static final String MAIN_HAND_KEY = "main_hand";
		private static final String OFF_HAND_KEY = "off_hand";

		private final InteractionContextCapture interactionContextCapture;
		private final TraceEventFactory traceEventFactory;
		private final List<ItemUseAttemptSignal> pendingItemUseSignals = new ArrayList<>();
		private final List<BlockUseAttemptSignal> pendingBlockUseSignals = new ArrayList<>();
		private final List<EntityUseAttemptSignal> pendingEntityUseSignals = new ArrayList<>();
		private final List<EntityAttackAttemptSignal> pendingEntityAttackSignals = new ArrayList<>();
		private long nextSignalOrder = 1L;

		InputInteractionCaptureStage(InteractionContextCapture interactionContextCapture, TraceEventFactory traceEventFactory) {
			this.interactionContextCapture = interactionContextCapture;
			this.traceEventFactory = traceEventFactory;
		}

		void recordItemUseAttempt(PlayerEntity player, World world, Hand hand) {
			String handKey = toHandKey(hand);
			InteractionActionContext actionContext = interactionContextCapture.captureForHand(player, hand);
			// selectedSlot/heldItem are callback-time action context, not flush-time snapshot state.
			pendingItemUseSignals.add(
				new ItemUseAttemptSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					handKey,
					actionContext.selectedSlot(),
					actionContext.heldItem()
				)
			);
		}

		void recordBlockUseAttempt(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
			BlockPos immutablePos = hitResult.getBlockPos().toImmutable();
			String handKey = toHandKey(hand);
			InteractionActionContext actionContext = interactionContextCapture.captureForHand(player, hand);
			// selectedSlot/heldItem are callback-time action context, not flush-time snapshot state.
			pendingBlockUseSignals.add(
				new BlockUseAttemptSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					Registries.BLOCK.getId(world.getBlockState(immutablePos).getBlock()).toString(),
					immutablePos,
					hitResult.getSide() == null ? null : hitResult.getSide().asString(),
					handKey,
					actionContext.selectedSlot(),
					actionContext.heldItem()
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
			InteractionActionContext actionContext = interactionContextCapture.captureForHand(player, hand);
			// selectedSlot/heldItem are callback-time action context, not flush-time snapshot state.
			pendingEntityUseSignals.add(
				new EntityUseAttemptSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					captureEntityReference(entity),
					handKey,
					actionContext.selectedSlot(),
					actionContext.heldItem()
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
			InteractionActionContext actionContext = interactionContextCapture.captureForHand(player, hand);
			// selectedSlot/heldItem are callback-time action context, not flush-time snapshot state.
			pendingEntityAttackSignals.add(
				new EntityAttackAttemptSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					captureEntityReference(entity),
					handKey,
					actionContext.selectedSlot(),
					actionContext.heldItem()
				)
			);
		}

		@Override
		public void collect(ClientSnapshot snapshot, DraftCollector collector) {
			if (hasNoPendingSignals()) {
				return;
			}

			List<InputInteractionSignal> orderedSignals = new ArrayList<>(
				pendingItemUseSignals.size() +
				pendingBlockUseSignals.size() +
				pendingEntityUseSignals.size() +
				pendingEntityAttackSignals.size()
			);
			orderedSignals.addAll(pendingItemUseSignals);
			orderedSignals.addAll(pendingBlockUseSignals);
			orderedSignals.addAll(pendingEntityUseSignals);
			orderedSignals.addAll(pendingEntityAttackSignals);
			orderedSignals.sort(Comparator.comparingLong(InputInteractionSignal::signalOrder));

			for (InputInteractionSignal signal : orderedSignals) {
				if (signal instanceof ItemUseAttemptSignal itemUseAttemptSignal) {
					collector.add(
						itemUseAttemptSignal.signalOrder(),
						itemUseAttemptSignal.worldTick(),
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
						blockUseAttemptSignal.signalOrder(),
						blockUseAttemptSignal.worldTick(),
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
						entityUseAttemptSignal.signalOrder(),
						entityUseAttemptSignal.worldTick(),
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
					entityAttackAttemptSignal.signalOrder(),
					entityAttackAttemptSignal.worldTick(),
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

			collector.addCommit(this::clearPendingSignals);
		}

		@Override
		public void reset(ResetScope scope) {
			clearPendingSignals();
			nextSignalOrder = 1L;
		}

		private boolean hasNoPendingSignals() {
			return pendingItemUseSignals.isEmpty() &&
				pendingBlockUseSignals.isEmpty() &&
				pendingEntityUseSignals.isEmpty() &&
				pendingEntityAttackSignals.isEmpty();
		}

		private void clearPendingSignals() {
			pendingItemUseSignals.clear();
			pendingBlockUseSignals.clear();
			pendingEntityUseSignals.clear();
			pendingEntityAttackSignals.clear();
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

			long worldTick();
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
				0L,
				snapshot.worldTick(),
				traceContext -> traceEventFactory.createInventoryTransaction(traceContext, snapshot, inventoryDeltaDraft),
				() -> inventoryDeltaDetector.commit(inventoryDeltaDraft)
			);
		}

		@Override
		public void reset(ResetScope scope) {
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
				0L,
				snapshot.worldTick(),
				traceContext -> traceEventFactory.createPlayerMotionSample(traceContext, snapshot)
			);
		}

		@Override
		public void reset(ResetScope scope) {
			periodicMotionSampler.reset();
		}
	}

	private static final class BlockBreakCaptureStage implements CaptureStage {
		private static final String MAIN_HAND_KEY = "main_hand";
		private static final String UNKNOWN_HAND_KEY = "unknown";
		private static final long MATCH_WINDOW_TICKS = 5L;
		private static final int MAX_PENDING_ATTEMPTS = 16;

		private final InteractionContextCapture interactionContextCapture;
		private final TraceEventFactory traceEventFactory;
		private final List<BlockBreakSignal> pendingSignals = new ArrayList<>();
		private final ArrayDeque<BlockBreakPendingAttempt> pendingAttempts = new ArrayDeque<>();

		private long nextSignalOrder = 1L;

		BlockBreakCaptureStage(InteractionContextCapture interactionContextCapture, TraceEventFactory traceEventFactory) {
			this.interactionContextCapture = interactionContextCapture;
			this.traceEventFactory = traceEventFactory;
		}

		void recordAttempt(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
			BlockPos immutablePos = pos.toImmutable();
			InteractionActionContext actionContext = interactionContextCapture.captureMainHand(player);
			// selectedSlot/heldItem are callback-time action context, not flush-time snapshot state.
			pendingSignals.add(
				new BlockBreakAttemptSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					immutablePos,
					Registries.BLOCK.getId(world.getBlockState(immutablePos).getBlock()).toString(),
					direction == null ? null : direction.asString(),
					hand == Hand.OFF_HAND ? "off_hand" : MAIN_HAND_KEY,
					actionContext.selectedSlot(),
					actionContext.heldItem()
				)
			);
		}

		void recordOutcome(ClientWorld world, ClientPlayerEntity player, BlockPos pos, BlockState state) {
			InteractionActionContext actionContext = interactionContextCapture.captureMainHand(player);
			// selectedSlot/heldItem are callback-time action context, not flush-time snapshot state.
			pendingSignals.add(
				new BlockBreakOutcomeSignal(
					nextSignalOrder++,
					world.getRegistryKey().getValue().toString(),
					world.getTime(),
					pos.toImmutable(),
					Registries.BLOCK.getId(state.getBlock()).toString(),
					actionContext.selectedSlot(),
					actionContext.heldItem()
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

			ArrayDeque<BlockBreakPendingAttempt> nextPendingAttempts = new ArrayDeque<>(pendingAttempts);
			for (BlockBreakSignal signal : orderedSignals) {
				if (signal instanceof BlockBreakAttemptSignal attemptSignal) {
					expireStaleAttempts(nextPendingAttempts, attemptSignal.dimensionKey(), attemptSignal.worldTick());
					appendPendingAttempt(
						nextPendingAttempts,
						new BlockBreakPendingAttempt(
							attemptSignal.dimensionKey(),
							attemptSignal.worldTick(),
							attemptSignal.pos(),
							attemptSignal.hitFace(),
							attemptSignal.hand()
						)
					);

					collector.add(
						attemptSignal.signalOrder(),
						attemptSignal.worldTick(),
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
				expireStaleAttempts(nextPendingAttempts, outcomeSignal.dimensionKey(), outcomeSignal.worldTick());
				BlockBreakOutcomeMatchResult matchResult = matchOutcome(nextPendingAttempts, outcomeSignal);

				collector.add(
					outcomeSignal.signalOrder(),
					outcomeSignal.worldTick(),
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

			collector.addCommit(() -> {
				pendingAttempts.clear();
				pendingAttempts.addAll(nextPendingAttempts);
				pendingSignals.clear();
			});
		}

		@Override
		public void reset(ResetScope scope) {
			pendingAttempts.clear();
			pendingSignals.clear();
			nextSignalOrder = 1L;
		}

		private static BlockBreakOutcomeMatchResult matchOutcome(
			ArrayDeque<BlockBreakPendingAttempt> pendingAttempts,
			BlockBreakOutcomeSignal successCandidate
		) {
			Iterator<BlockBreakPendingAttempt> descendingIterator = pendingAttempts.descendingIterator();
			while (descendingIterator.hasNext()) {
				BlockBreakPendingAttempt pendingAttempt = descendingIterator.next();
				if (!Objects.equals(pendingAttempt.dimensionKey(), successCandidate.dimensionKey())) {
					continue;
				}

				long tickDelta = successCandidate.worldTick() - pendingAttempt.worldTick();
				if (tickDelta < 0L || tickDelta > MATCH_WINDOW_TICKS) {
					continue;
				}

				if (!pendingAttempt.pos().equals(successCandidate.pos())) {
					continue;
				}

				descendingIterator.remove();
				return new BlockBreakOutcomeMatchResult(
					pendingAttempt.hitFace(),
					pendingAttempt.hand(),
					BlockBreakMatchStatus.MATCHED
				);
			}

			return new BlockBreakOutcomeMatchResult(
				null,
				UNKNOWN_HAND_KEY,
				BlockBreakMatchStatus.UNMATCHED
			);
		}

		private static void expireStaleAttempts(
			ArrayDeque<BlockBreakPendingAttempt> pendingAttempts,
			String dimensionKey,
			long referenceWorldTick
		) {
			for (Iterator<BlockBreakPendingAttempt> iterator = pendingAttempts.iterator(); iterator.hasNext();) {
				BlockBreakPendingAttempt pendingAttempt = iterator.next();
				if (!Objects.equals(pendingAttempt.dimensionKey(), dimensionKey)) {
					continue;
				}

				if (referenceWorldTick - pendingAttempt.worldTick() > MATCH_WINDOW_TICKS) {
					iterator.remove();
				}
			}
		}

		private static void appendPendingAttempt(
			ArrayDeque<BlockBreakPendingAttempt> pendingAttempts,
			BlockBreakPendingAttempt nextPendingAttempt
		) {
			pendingAttempts.addLast(nextPendingAttempt);
			while (pendingAttempts.size() > MAX_PENDING_ATTEMPTS) {
				pendingAttempts.removeFirst();
			}
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
			BlockBreakMatchStatus status
		) {
		}

		private enum BlockBreakMatchStatus {
			MATCHED,
			UNMATCHED
		}
	}
}

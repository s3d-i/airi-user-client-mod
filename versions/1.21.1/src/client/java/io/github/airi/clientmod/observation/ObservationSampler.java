package io.github.airi.clientmod.observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.airi.clientmod.core.trace.InteractionBlockBreakTraceEvent;
import io.github.airi.clientmod.core.trace.InventoryTransactionTraceEvent;
import io.github.airi.clientmod.core.trace.ObservationEmitter;
import io.github.airi.clientmod.core.trace.ObservationSample;
import io.github.airi.clientmod.core.trace.PlayerHandStateChangedTraceEvent;
import io.github.airi.clientmod.core.trace.PlayerLookTargetChangedTraceEvent;
import io.github.airi.clientmod.core.trace.PlayerSelectedSlotChangedTraceEvent;
import io.github.airi.clientmod.core.trace.TraceEvent;
import io.github.airi.clientmod.session.WorldSessionTracker;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class ObservationSampler {
	public static final int EMIT_INTERVAL_TICKS = 10;
	private static final int MAX_INVENTORY_SLOT_DELTAS = 24;

	private final ObservationEmitter emitter;
	private final WorldSessionTracker worldSessionTracker;
	private int ticksUntilEmit = EMIT_INTERVAL_TICKS;
	private TraceEvent.LookTarget lastLookTarget;
	private Integer lastSelectedSlot;
	private TraceEvent.ItemStackSnapshot lastMainHand;
	private TraceEvent.ItemStackSnapshot lastOffHand;
	private List<TraceEvent.ItemStackSnapshot> lastInventorySnapshot = List.of();
	private PendingBlockAttack pendingBlockAttack;

	public ObservationSampler(ObservationEmitter emitter, WorldSessionTracker worldSessionTracker) {
		this.emitter = emitter;
		this.worldSessionTracker = worldSessionTracker;
	}

	public void onEndClientTick(MinecraftClient client) {
		if (client.world == null || client.player == null || !worldSessionTracker.hasActiveSession()) {
			resetTransientState();
			return;
		}

		emitLookTargetChangeIfNeeded(client);
		emitSelectedSlotChangeIfNeeded(client);
		emitHandStateChangeIfNeeded(client);
		emitInventoryTransactionIfNeeded(client);

		ticksUntilEmit--;
		if (ticksUntilEmit > 0) {
			return;
		}

		ticksUntilEmit = EMIT_INTERVAL_TICKS;

		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		Vec3d position = client.player.getPos();
		Vec3d velocity = client.player.getVelocity();
		TraceEvent.LookTarget currentLookTarget = captureLookTarget(client);

		emitter.emit(new ObservationSample(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			client.world.getTime(),
			client.getCurrentFps(),
			client.world.getRegistryKey().getValue().toString(),
			position.x,
			position.y,
			position.z,
			velocity.x,
			velocity.y,
			velocity.z,
			describeLookTarget(currentLookTarget)
		));
	}

	public void onAttackBlock(net.minecraft.entity.player.PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
		if (!world.isClient() || !worldSessionTracker.hasActiveSession()) {
			return;
		}

		pendingBlockAttack = new PendingBlockAttack(
			world.getRegistryKey().getValue().toString(),
			world.getTime(),
			pos.toImmutable(),
			direction == null ? null : direction.asString(),
			hand == Hand.OFF_HAND ? "off_hand" : "main_hand"
		);
	}

	public void onAfterClientBlockBreak(ClientWorld world, ClientPlayerEntity player, BlockPos pos, BlockState state) {
		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		PendingBlockAttack attack = consumePendingBlockAttack(world, pos);
		String hitFace = attack == null ? null : attack.hitFace();
		String hand = attack == null ? "main_hand" : attack.hand();

		emitter.emit(new InteractionBlockBreakTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			world.getTime(),
			world.getRegistryKey().getValue().toString(),
			new TraceEvent.BlockReference(
				Registries.BLOCK.getId(state.getBlock()).toString(),
				new TraceEvent.BlockPosition(pos.getX(), pos.getY(), pos.getZ()),
				hitFace
			),
			hand,
			player.getInventory().selectedSlot,
			captureItemStack(player.getMainHandStack())
		));
	}

	private void emitLookTargetChangeIfNeeded(MinecraftClient client) {
		TraceEvent.LookTarget currentLookTarget = captureLookTarget(client);
		if (Objects.equals(lastLookTarget, currentLookTarget)) {
			return;
		}

		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		lastLookTarget = currentLookTarget;
		emitter.emit(new PlayerLookTargetChangedTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			client.world.getTime(),
			client.world.getRegistryKey().getValue().toString(),
			currentLookTarget
		));
	}

	private void emitSelectedSlotChangeIfNeeded(MinecraftClient client) {
		int selectedSlot = client.player.getInventory().selectedSlot;
		if (lastSelectedSlot == null) {
			lastSelectedSlot = selectedSlot;
			return;
		}

		if (lastSelectedSlot == selectedSlot) {
			return;
		}

		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		emitter.emit(new PlayerSelectedSlotChangedTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			client.world.getTime(),
			client.world.getRegistryKey().getValue().toString(),
			lastSelectedSlot,
			selectedSlot,
			captureItemStack(client.player.getMainHandStack()),
			captureItemStack(client.player.getOffHandStack())
		));

		lastSelectedSlot = selectedSlot;
	}

	private void emitHandStateChangeIfNeeded(MinecraftClient client) {
		TraceEvent.ItemStackSnapshot mainHand = captureItemStack(client.player.getMainHandStack());
		TraceEvent.ItemStackSnapshot offHand = captureItemStack(client.player.getOffHandStack());
		if (lastMainHand == null || lastOffHand == null) {
			lastMainHand = mainHand;
			lastOffHand = offHand;
			return;
		}

		if (Objects.equals(lastMainHand, mainHand) && Objects.equals(lastOffHand, offHand)) {
			return;
		}

		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		emitter.emit(new PlayerHandStateChangedTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			client.world.getTime(),
			client.world.getRegistryKey().getValue().toString(),
			client.player.getInventory().selectedSlot,
			mainHand,
			offHand
		));

		lastMainHand = mainHand;
		lastOffHand = offHand;
	}

	private void emitInventoryTransactionIfNeeded(MinecraftClient client) {
		List<TraceEvent.ItemStackSnapshot> currentInventorySnapshot = captureInventorySnapshot(client.player);
		if (lastInventorySnapshot.isEmpty()) {
			lastInventorySnapshot = currentInventorySnapshot;
			return;
		}

		List<TraceEvent.InventorySlotDelta> changedSlots = buildInventorySlotDeltas(lastInventorySnapshot, currentInventorySnapshot);
		if (changedSlots.isEmpty()) {
			lastInventorySnapshot = currentInventorySnapshot;
			return;
		}

		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginTrace();
		if (traceContext == null) {
			return;
		}

		lastInventorySnapshot = currentInventorySnapshot;
		emitter.emit(new InventoryTransactionTraceEvent(
			traceContext.sequence(),
			traceContext.capturedAtMillis(),
			client.world.getTime(),
			client.world.getRegistryKey().getValue().toString(),
			"player_inventory",
			"player_inventory.scan",
			changedSlots
		));
	}

	private static TraceEvent.LookTarget captureLookTarget(MinecraftClient client) {
		HitResult hitResult = client.crosshairTarget;
		if (hitResult == null) {
			return new TraceEvent.LookTarget("none", "none", null, null);
		}

		return switch (hitResult.getType()) {
			case BLOCK -> captureBlockTarget(client, (BlockHitResult) hitResult);
			case ENTITY -> captureEntityTarget((EntityHitResult) hitResult);
			case MISS -> new TraceEvent.LookTarget("miss", "miss", null, null);
			default -> new TraceEvent.LookTarget(
				hitResult.getType().name().toLowerCase(Locale.ROOT),
				hitResult.getType().name().toLowerCase(Locale.ROOT),
				null,
				null
			);
		};
	}

	private static TraceEvent.LookTarget captureBlockTarget(MinecraftClient client, BlockHitResult hitResult) {
		BlockPos blockPos = hitResult.getBlockPos();
		BlockState blockState = client.world.getBlockState(blockPos);
		String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
		String description = "block " + blockId + " @ " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ();
		return new TraceEvent.LookTarget(
			"block",
			description,
			new TraceEvent.BlockReference(
				blockId,
				new TraceEvent.BlockPosition(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
				hitResult.getSide().asString()
			),
			null
		);
	}

	private static TraceEvent.LookTarget captureEntityTarget(EntityHitResult hitResult) {
		Entity entity = hitResult.getEntity();
		String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
		return new TraceEvent.LookTarget(
			"entity",
			"entity " + entityId,
			null,
			new TraceEvent.LookTargetEntity(entityId, entity.getId())
		);
	}

	private static String describeLookTarget(TraceEvent.LookTarget target) {
		if (target == null) {
			return "none";
		}

		if (target.targetDescription() != null && !target.targetDescription().isBlank()) {
			return target.targetDescription();
		}

		return target.kind();
	}

	private static TraceEvent.ItemStackSnapshot captureItemStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return new TraceEvent.ItemStackSnapshot(null, 0, 0, 0);
		}

		return new TraceEvent.ItemStackSnapshot(
			Registries.ITEM.getId(stack.getItem()).toString(),
			stack.getCount(),
			stack.getDamage(),
			stack.getMaxDamage()
		);
	}

	private static List<TraceEvent.ItemStackSnapshot> captureInventorySnapshot(ClientPlayerEntity player) {
		List<TraceEvent.ItemStackSnapshot> snapshot = new ArrayList<>(player.getInventory().size());
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			snapshot.add(captureItemStack(player.getInventory().getStack(slot)));
		}
		return snapshot;
	}

	private static List<TraceEvent.InventorySlotDelta> buildInventorySlotDeltas(
		List<TraceEvent.ItemStackSnapshot> previousSnapshot,
		List<TraceEvent.ItemStackSnapshot> currentSnapshot
	) {
		int size = Math.min(previousSnapshot.size(), currentSnapshot.size());
		List<TraceEvent.InventorySlotDelta> changedSlots = new ArrayList<>();
		for (int slot = 0; slot < size; slot++) {
			TraceEvent.ItemStackSnapshot previous = previousSnapshot.get(slot);
			TraceEvent.ItemStackSnapshot current = currentSnapshot.get(slot);
			if (Objects.equals(previous, current)) {
				continue;
			}
			changedSlots.add(new TraceEvent.InventorySlotDelta(slot, previous, current));
			if (changedSlots.size() >= MAX_INVENTORY_SLOT_DELTAS) {
				break;
			}
		}
		return changedSlots;
	}

	private void resetTransientState() {
		ticksUntilEmit = EMIT_INTERVAL_TICKS;
		lastLookTarget = null;
		lastSelectedSlot = null;
		lastMainHand = null;
		lastOffHand = null;
		lastInventorySnapshot = List.of();
		pendingBlockAttack = null;
	}

	private PendingBlockAttack consumePendingBlockAttack(ClientWorld world, BlockPos pos) {
		if (pendingBlockAttack == null) {
			return null;
		}

		if (!Objects.equals(pendingBlockAttack.dimensionKey(), world.getRegistryKey().getValue().toString())) {
			pendingBlockAttack = null;
			return null;
		}

		if (world.getTime() - pendingBlockAttack.worldTick() > 5L) {
			pendingBlockAttack = null;
			return null;
		}

		if (!pendingBlockAttack.pos().equals(pos)) {
			return null;
		}

		PendingBlockAttack matched = pendingBlockAttack;
		pendingBlockAttack = null;
		return matched;
	}

	private record PendingBlockAttack(
		String dimensionKey,
		long worldTick,
		BlockPos pos,
		String hitFace,
		String hand
	) {
	}
}

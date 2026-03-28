package io.github.airi.clientmod.observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.airi.clientmod.core.trace.TraceEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ClientSnapshotReader {
	public ClientSnapshot read(MinecraftClient client) {
		ClientWorld world = client.world;
		ClientPlayerEntity player = client.player;
		if (world == null || player == null) {
			throw new IllegalStateException("ClientSnapshotReader requires an in-world client state");
		}

		Vec3d position = player.getPos();
		Vec3d velocity = player.getVelocity();
		TraceEvent.LookTarget lookTarget = captureLookTarget(client);

		return new ClientSnapshot(
			world.getTime(),
			world.getRegistryKey().getValue().toString(),
			new ClientSnapshot.Position(position.x, position.y, position.z),
			new ClientSnapshot.Velocity(velocity.x, velocity.y, velocity.z),
			lookTarget,
			player.getInventory().selectedSlot,
			captureItemStack(player.getMainHandStack()),
			captureItemStack(player.getOffHandStack()),
			captureInventorySnapshot(player)
		);
	}

	public TraceEvent.ItemStackSnapshot captureItemStack(ItemStack stack) {
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

	private List<TraceEvent.ItemStackSnapshot> captureInventorySnapshot(ClientPlayerEntity player) {
		List<TraceEvent.ItemStackSnapshot> snapshot = new ArrayList<>(player.getInventory().size());
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			snapshot.add(captureItemStack(player.getInventory().getStack(slot)));
		}
		return snapshot;
	}
}

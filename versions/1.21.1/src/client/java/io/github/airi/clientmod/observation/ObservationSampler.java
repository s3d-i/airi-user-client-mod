package io.github.airi.clientmod.observation;

import java.util.Locale;

import io.github.airi.clientmod.core.trace.ObservationEmitter;
import io.github.airi.clientmod.core.trace.ObservationSample;
import io.github.airi.clientmod.session.WorldSessionTracker;
import io.github.airi.clientmod.transport.WebSocketObservationSink;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ObservationSampler {
	public static final int EMIT_INTERVAL_TICKS = 10;

	private final ObservationEmitter observationEmitter;
	private final WebSocketObservationSink rawTraceSink;
	private final WorldSessionTracker worldSessionTracker;
	private int ticksUntilEmit = EMIT_INTERVAL_TICKS;

	public ObservationSampler(
		ObservationEmitter observationEmitter,
		WebSocketObservationSink rawTraceSink,
		WorldSessionTracker worldSessionTracker
	) {
		this.observationEmitter = observationEmitter;
		this.rawTraceSink = rawTraceSink;
		this.worldSessionTracker = worldSessionTracker;
	}

	public void onEndClientTick(MinecraftClient client) {
		if (client.world == null || client.player == null || !worldSessionTracker.hasActiveSession()) {
			ticksUntilEmit = EMIT_INTERVAL_TICKS;
			return;
		}

		ticksUntilEmit--;
		if (ticksUntilEmit > 0) {
			return;
		}

		ticksUntilEmit = EMIT_INTERVAL_TICKS;
		WorldSessionTracker.SampleTraceContext traceContext = worldSessionTracker.beginObservationTrace();

		if (traceContext == null) {
			return;
		}

		Vec3d position = client.player.getPos();
		Vec3d velocity = client.player.getVelocity();

		ObservationSample sample = new ObservationSample(
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
			describeTarget(client)
		);
		observationEmitter.emit(sample);
		rawTraceSink.emitObservationSample(traceContext.sessionId(), sample);
	}

	private static String describeTarget(MinecraftClient client) {
		HitResult hitResult = client.crosshairTarget;
		if (hitResult == null) {
			return "none";
		}

		return switch (hitResult.getType()) {
			case BLOCK -> describeBlockTarget(client, (BlockHitResult) hitResult);
			case ENTITY -> describeEntityTarget((EntityHitResult) hitResult);
			case MISS -> "miss";
			default -> hitResult.getType().name().toLowerCase(Locale.ROOT);
		};
	}

	private static String describeBlockTarget(MinecraftClient client, BlockHitResult hitResult) {
		BlockPos blockPos = hitResult.getBlockPos();
		BlockState blockState = client.world.getBlockState(blockPos);
		String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
		return "block " + blockId + " @ " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ();
	}

	private static String describeEntityTarget(EntityHitResult hitResult) {
		Entity entity = hitResult.getEntity();
		String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
		return "entity " + entityId;
	}
}

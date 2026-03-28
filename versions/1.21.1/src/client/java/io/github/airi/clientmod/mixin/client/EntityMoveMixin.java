package io.github.airi.clientmod.mixin.client;

import java.util.stream.Stream;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class EntityMoveMixin {
	/*
	 * Fixes runtime crash:
	 * java.lang.IllegalStateException: stream has already been operated upon or closed
	 * at Entity.move(...) -> World#getStatesInBoxIfLoaded(...).noneMatch(...)
	 *
	 * Reason:
	 * The call site in Entity.move expects a fresh Stream each tick. In our runtime
	 * stack this stream can arrive already consumed/closed (after mixin composition),
	 * then noneMatch immediately throws and crashes entity ticking.
	 *
	 * Redirecting this invocation to recreate the stream from the same inputs keeps
	 * vanilla behavior (region-loaded guard + block-state traversal) while ensuring
	 * the stream instance is always new and safe to consume.
	 */
	@Redirect(
		method = "move",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/World;getStatesInBoxIfLoaded(Lnet/minecraft/util/math/Box;)Ljava/util/stream/Stream;"
		)
	)
	private Stream<BlockState> airi$freshStatesInBox(World world, Box box) {
		int minX = MathHelper.floor(box.minX);
		int maxX = MathHelper.floor(box.maxX);
		int minY = MathHelper.floor(box.minY);
		int maxY = MathHelper.floor(box.maxY);
		int minZ = MathHelper.floor(box.minZ);
		int maxZ = MathHelper.floor(box.maxZ);
		if (!world.isRegionLoaded(minX, minY, minZ, maxX, maxY, maxZ)) {
			return Stream.empty();
		}
		return BlockPos.stream(box).map(world::getBlockState);
	}
}

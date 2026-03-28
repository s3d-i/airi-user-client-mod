package io.github.airi.clientmod.observation;

import java.util.Objects;

import net.minecraft.util.math.BlockPos;

public final class BlockBreakInteractionMatcher implements PendingInteractionMatcher<
	BlockBreakInteractionMatcher.BlockBreakAttempt,
	BlockBreakInteractionMatcher.BlockBreakSuccessCandidate,
	BlockBreakInteractionMatcher.BlockBreakMatchResult
> {
	private BlockBreakAttempt pendingAttempt;

	@Override
	public void registerAttempt(BlockBreakAttempt attempt) {
		pendingAttempt = attempt;
	}

	@Override
	public BlockBreakMatchResult matchSuccess(BlockBreakSuccessCandidate successCandidate) {
		if (pendingAttempt == null) {
			return null;
		}

		if (!Objects.equals(pendingAttempt.dimensionKey(), successCandidate.dimensionKey())) {
			pendingAttempt = null;
			return null;
		}

		if (successCandidate.worldTick() - pendingAttempt.worldTick() > 5L) {
			pendingAttempt = null;
			return null;
		}

		if (!pendingAttempt.pos().equals(successCandidate.pos())) {
			return null;
		}

		BlockBreakMatchResult matched = new BlockBreakMatchResult(
			pendingAttempt.hitFace(),
			pendingAttempt.hand()
		);
		pendingAttempt = null;
		return matched;
	}

	@Override
	public void reset() {
		pendingAttempt = null;
	}

	public record BlockBreakAttempt(
		String dimensionKey,
		long worldTick,
		BlockPos pos,
		String hitFace,
		String hand
	) {
		public BlockBreakAttempt {
			pos = pos.toImmutable();
		}
	}

	public record BlockBreakSuccessCandidate(
		String dimensionKey,
		long worldTick,
		BlockPos pos
	) {
	}

	public record BlockBreakMatchResult(
		String hitFace,
		String hand
	) {
	}
}

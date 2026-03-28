package io.github.airi.clientmod.observation;

public final class PeriodicMotionSampler {
	public static final int EMIT_INTERVAL_TICKS = 10;

	private int ticksUntilEmit = EMIT_INTERVAL_TICKS;

	public boolean shouldEmitThisTick() {
		ticksUntilEmit--;
		if (ticksUntilEmit > 0) {
			return false;
		}

		ticksUntilEmit = EMIT_INTERVAL_TICKS;
		return true;
	}

	public void reset() {
		ticksUntilEmit = EMIT_INTERVAL_TICKS;
	}
}

package io.github.airi.clientmod.observation;

public final class PeriodicMotionSampler {
	private static final String PLAYER_MOTION_SAMPLING_MODE = "seconds";
	private static final int PLAYER_MOTION_SAMPLE_INTERVAL_TICKS = 10;
	private static final long PLAYER_MOTION_SAMPLE_INTERVAL_MILLIS = 500L;

	private long nextEmitAtMillis;

	public boolean shouldEmitThisTick() {
		long nowMillis = System.currentTimeMillis();
		if (nextEmitAtMillis == 0L) {
			nextEmitAtMillis = nowMillis + PLAYER_MOTION_SAMPLE_INTERVAL_MILLIS;
			return false;
		}
		if (nowMillis < nextEmitAtMillis) {
			return false;
		}

		nextEmitAtMillis = nowMillis + PLAYER_MOTION_SAMPLE_INTERVAL_MILLIS;
		return true;
	}

	public void reset() {
		nextEmitAtMillis = 0L;
	}

	public static String playerMotionSamplingMode() {
		return PLAYER_MOTION_SAMPLING_MODE;
	}

	public static int playerMotionSampleIntervalTicks() {
		return PLAYER_MOTION_SAMPLE_INTERVAL_TICKS;
	}

	public static long playerMotionSampleIntervalMillis() {
		return PLAYER_MOTION_SAMPLE_INTERVAL_MILLIS;
	}
}

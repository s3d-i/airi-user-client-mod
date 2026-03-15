package io.github.airi.clientmod.observation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public final class DebugHudObservationStore implements ObservationEmitter {
	private static final int HISTORY_LIMIT = 32;

	private final Deque<ObservationSample> recentSamples = new ArrayDeque<>();
	private ObservationSample latestSample;
	private long totalEmitted;
	private long firstCapturedAtMillis;

	@Override
	public void emit(ObservationSample sample) {
		if (firstCapturedAtMillis == 0L) {
			firstCapturedAtMillis = sample.capturedAtMillis();
		}

		totalEmitted++;
		latestSample = sample;
		recentSamples.addLast(sample);

		while (recentSamples.size() > HISTORY_LIMIT) {
			recentSamples.removeFirst();
		}
	}

	public List<String> buildPanelLines() {
		List<String> lines = new ArrayList<>();
		lines.add("[AIRI] observation emit");
		lines.add("mode: experimental / client-only");
		lines.add("interval: every " + ObservationSampler.EMIT_INTERVAL_TICKS + " client ticks");

		if (latestSample == null) {
			lines.add("status: waiting for in-world samples");
			lines.add("buffer: 0/" + HISTORY_LIMIT);
			return lines;
		}

		long ageMillis = Math.max(0L, System.currentTimeMillis() - latestSample.capturedAtMillis());
		long spanMillis = Math.max(1L, latestSample.capturedAtMillis() - firstCapturedAtMillis + 1L);
		double emitsPerSecond = (totalEmitted * 1000.0D) / spanMillis;

		lines.add("status: live");
		lines.add("emit seq: " + latestSample.sequence() + " | rate: " + format(emitsPerSecond) + "/s");
		lines.add("world tick: " + latestSample.worldTick() + " | fps: " + latestSample.fps());
		lines.add("dimension: " + latestSample.dimensionKey());
		lines.add("pos: " + format(latestSample.x()) + ", " + format(latestSample.y()) + ", " + format(latestSample.z()));
		lines.add("vel: " + format(latestSample.vx()) + ", " + format(latestSample.vy()) + ", " + format(latestSample.vz()));
		lines.add("target: " + latestSample.targetDescription());
		lines.add("buffer: " + recentSamples.size() + "/" + HISTORY_LIMIT + " | last emit: " + ageMillis + " ms ago");
		return lines;
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}

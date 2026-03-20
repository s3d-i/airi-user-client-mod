package io.github.airi.clientmod.core.trace;

public record ObservationSample(
	long sequence,
	long capturedAtMillis,
	long worldTick,
	int fps,
	String dimensionKey,
	double x,
	double y,
	double z,
	double vx,
	double vy,
	double vz,
	String targetDescription
) {
}

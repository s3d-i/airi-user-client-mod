package io.github.airi.clientmod.core.trace;

public record PlayerMotionSampleTraceEvent(
	long sequence,
	long capturedAtMillis,
	long worldTick,
	String dimensionKey,
	double x,
	double y,
	double z,
	double vx,
	double vy,
	double vz
) implements TraceEvent {
}

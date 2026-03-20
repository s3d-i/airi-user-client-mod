package io.github.airi.clientmod.core.trace;

public record PlayerLookTargetChangedTraceEvent(
	long sequence,
	long capturedAtMillis,
	long worldTick,
	String dimensionKey,
	TraceEvent.LookTarget target
) implements TraceEvent {
}

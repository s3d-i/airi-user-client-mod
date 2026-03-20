package io.github.airi.clientmod.observation;

import java.util.List;

import io.github.airi.clientmod.AiriUserClientMod;
import io.github.airi.clientmod.core.trace.ObservationEmitter;
import io.github.airi.clientmod.core.trace.TraceEvent;

public final class FanoutObservationEmitter implements ObservationEmitter {
	private final List<ObservationEmitter> emitters;

	public FanoutObservationEmitter(ObservationEmitter... emitters) {
		this.emitters = List.of(emitters);
	}

	@Override
	public void emit(TraceEvent event) {
		for (ObservationEmitter emitter : emitters) {
			try {
				emitter.emit(event);
			} catch (RuntimeException exception) {
				AiriUserClientMod.LOGGER.warn(
					"Observation sink {} failed while handling trace {} ({})",
					emitter.getClass().getSimpleName(),
					event.sequence(),
					event.getClass().getSimpleName(),
					exception
				);
			}
		}
	}
}

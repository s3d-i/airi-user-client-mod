package io.github.airi.clientmod.observation;

import java.util.List;

import io.github.airi.clientmod.AiriUserClientMod;

public final class FanoutObservationEmitter implements ObservationEmitter {
	private final List<ObservationEmitter> emitters;

	public FanoutObservationEmitter(ObservationEmitter... emitters) {
		this.emitters = List.of(emitters);
	}

	@Override
	public void emit(ObservationSample sample) {
		for (ObservationEmitter emitter : emitters) {
			try {
				emitter.emit(sample);
			} catch (RuntimeException exception) {
				AiriUserClientMod.LOGGER.warn(
					"Observation sink {} failed while handling sample {}",
					emitter.getClass().getSimpleName(),
					sample.sequence(),
					exception
				);
			}
		}
	}
}

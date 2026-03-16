package io.github.airi.clientmod;

import io.github.airi.clientmod.observation.DebugHudObservationStore;
import io.github.airi.clientmod.observation.FanoutObservationEmitter;
import io.github.airi.clientmod.observation.ObservationSampler;
import io.github.airi.clientmod.transport.TransportStatusStore;
import io.github.airi.clientmod.transport.WebSocketObservationSink;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class AiriUserClientModClient implements ClientModInitializer {
	private static final DebugHudObservationStore DEBUG_STORE = new DebugHudObservationStore();
	private static final TransportStatusStore TRANSPORT_STATUS_STORE = new TransportStatusStore();
	private static final WebSocketObservationSink WEBSOCKET_SINK = new WebSocketObservationSink(TRANSPORT_STATUS_STORE);
	private static final ObservationSampler OBSERVATION_SAMPLER =
		new ObservationSampler(new FanoutObservationEmitter(DEBUG_STORE, WEBSOCKET_SINK));

	public static DebugHudObservationStore getDebugStore() {
		return DEBUG_STORE;
	}

	public static TransportStatusStore getTransportStatusStore() {
		return TRANSPORT_STATUS_STORE;
	}

	@Override
	public void onInitializeClient() {
		WEBSOCKET_SINK.start();
		ClientTickEvents.END_CLIENT_TICK.register(OBSERVATION_SAMPLER::onEndClientTick);
		AiriUserClientMod.LOGGER.info("Initialized AIRI experimental Fabric client instrumentation for Minecraft 1.21.1");
	}
}

package io.github.airi.clientmod;

import io.github.airi.clientmod.observation.DebugHudObservationStore;
import io.github.airi.clientmod.observation.FanoutObservationEmitter;
import io.github.airi.clientmod.observation.ObservationSampler;
import io.github.airi.clientmod.telemetry.OtelBootstrap;
import io.github.airi.clientmod.transport.TransportStatusStore;
import io.github.airi.clientmod.transport.TransportTelemetry;
import io.github.airi.clientmod.transport.WebSocketObservationSink;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class AiriUserClientModClient implements ClientModInitializer {
	private static final DebugHudObservationStore DEBUG_STORE = new DebugHudObservationStore();
	private static final TransportStatusStore TRANSPORT_STATUS_STORE =
		new TransportStatusStore(WebSocketObservationSink.MAX_QUEUE_DEPTH);

	private WebSocketObservationSink websocketSink;
	private ObservationSampler observationSampler;

	public static DebugHudObservationStore getDebugStore() {
		return DEBUG_STORE;
	}

	public static TransportStatusStore getTransportStatusStore() {
		return TRANSPORT_STATUS_STORE;
	}

	@Override
	public void onInitializeClient() {
		TransportTelemetry transportTelemetry = OtelBootstrap.init();
		websocketSink = new WebSocketObservationSink(TRANSPORT_STATUS_STORE, transportTelemetry);
		observationSampler = new ObservationSampler(new FanoutObservationEmitter(DEBUG_STORE, websocketSink));
		websocketSink.start();
		ClientTickEvents.END_CLIENT_TICK.register(observationSampler::onEndClientTick);
		AiriUserClientMod.LOGGER.info("Initialized AIRI experimental Fabric client instrumentation for Minecraft 1.21.1");
	}
}

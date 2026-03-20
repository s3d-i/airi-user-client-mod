package io.github.airi.clientmod;

import io.github.airi.clientmod.observation.DebugHudObservationStore;
import io.github.airi.clientmod.observation.ObservationSampler;
import io.github.airi.clientmod.session.WorldSessionTracker;
import io.github.airi.clientmod.telemetry.OtelBootstrap;
import io.github.airi.clientmod.transport.TransportStatusStore;
import io.github.airi.clientmod.transport.TransportTelemetry;
import io.github.airi.clientmod.transport.WebSocketObservationSink;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class AiriUserClientModClient implements ClientModInitializer {
	private static final DebugHudObservationStore DEBUG_STORE = new DebugHudObservationStore();
	private static final TransportStatusStore TRANSPORT_STATUS_STORE = new TransportStatusStore();

	private WebSocketObservationSink websocketSink;
	private ObservationSampler observationSampler;
	private WorldSessionTracker worldSessionTracker;

	public static DebugHudObservationStore getDebugStore() {
		return DEBUG_STORE;
	}

	public static TransportStatusStore getTransportStatusStore() {
		return TRANSPORT_STATUS_STORE;
	}

	@Override
	public void onInitializeClient() {
		TransportTelemetry transportTelemetry = OtelBootstrap.init();
		worldSessionTracker = new WorldSessionTracker();
		websocketSink = new WebSocketObservationSink(TRANSPORT_STATUS_STORE, transportTelemetry, () -> {
			WorldSessionTracker.ActiveSessionState activeSession = worldSessionTracker.getActiveSession();
			if (activeSession == null) {
				return null;
			}

			return new WebSocketObservationSink.SessionReplay(activeSession.sessionId(), activeSession.startedAtMillis());
		});
		observationSampler = new ObservationSampler(DEBUG_STORE, websocketSink, worldSessionTracker);
		websocketSink.start();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			DEBUG_STORE.reset();
			WorldSessionTracker.SessionControlFrame frame = worldSessionTracker.startWorldSession();
			websocketSink.emitSessionStart(frame.sessionId(), frame.sequence(), frame.capturedAtMillis());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			WorldSessionTracker.SessionControlFrame frame = worldSessionTracker.endWorldSession();
			if (frame != null) {
				websocketSink.emitSessionEnd(frame.sessionId(), frame.sequence(), frame.capturedAtMillis());
			}
			DEBUG_STORE.reset();
		});
		ClientTickEvents.END_CLIENT_TICK.register(observationSampler::onEndClientTick);
		AiriUserClientMod.LOGGER.info("Initialized AIRI experimental Fabric client instrumentation for Minecraft 1.21.1");
	}
}

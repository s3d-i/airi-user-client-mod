package io.github.airi.clientmod;

import io.github.airi.clientmod.observation.DebugHudObservationStore;
import io.github.airi.clientmod.observation.FanoutObservationEmitter;
import io.github.airi.clientmod.observation.CaptureCoordinator;
import io.github.airi.clientmod.session.WorldSessionTracker;
import io.github.airi.clientmod.telemetry.OtelBootstrap;
import io.github.airi.clientmod.transport.SessionStartPayloadSupplier;
import io.github.airi.clientmod.transport.TransportStatusStore;
import io.github.airi.clientmod.transport.TransportTelemetry;
import io.github.airi.clientmod.transport.WebSocketObservationSink;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

public final class AiriUserClientModClient implements ClientModInitializer {
	private static final DebugHudObservationStore DEBUG_STORE = new DebugHudObservationStore();
	private static final TransportStatusStore TRANSPORT_STATUS_STORE = new TransportStatusStore();

	private WebSocketObservationSink websocketSink;
	private CaptureCoordinator captureCoordinator;
	private WorldSessionTracker worldSessionTracker;

	public static DebugHudObservationStore getDebugStore() {
		return DEBUG_STORE;
	}

	public static TransportStatusStore getTransportStatusStore() {
		return TRANSPORT_STATUS_STORE;
	}

	@Override
	public void onInitializeClient() {
		TransportTelemetry transportTelemetry = TransportTelemetry.NOOP;
		try {
			transportTelemetry = OtelBootstrap.init();
		} catch (LinkageError | RuntimeException exception) {
			AiriUserClientMod.LOGGER.warn(
				"OpenTelemetry bootstrap unavailable; continuing with telemetry noop fallback",
				exception
			);
		}
		worldSessionTracker = new WorldSessionTracker();
		websocketSink = new WebSocketObservationSink(
			TRANSPORT_STATUS_STORE,
			transportTelemetry,
			() -> {
				WorldSessionTracker.ActiveSessionState activeSession = worldSessionTracker.getActiveSession();
				if (activeSession == null) {
					return null;
				}

				return new WebSocketObservationSink.ActiveSessionDescriptor(
					activeSession.sessionId(),
					activeSession.startedAtMillis()
				);
			},
			new SessionStartPayloadSupplier()
		);
		captureCoordinator = new CaptureCoordinator(
			new FanoutObservationEmitter(DEBUG_STORE, websocketSink),
			worldSessionTracker
		);
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
		ClientTickEvents.END_CLIENT_TICK.register(captureCoordinator::onEndClientTick);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			captureCoordinator.onAttackBlock(player, world, hand, pos, direction);
			return ActionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			captureCoordinator.onUseItem(player, world, hand);
			return TypedActionResult.pass(player.getStackInHand(hand));
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			captureCoordinator.onUseBlock(player, world, hand, hitResult);
			return ActionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			captureCoordinator.onUseEntity(player, world, hand, entity, hitResult);
			return ActionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			captureCoordinator.onAttackEntity(player, world, hand, entity, hitResult);
			return ActionResult.PASS;
		});
		ClientPlayerBlockBreakEvents.AFTER.register(captureCoordinator::onAfterClientBlockBreak);
		AiriUserClientMod.LOGGER.info("Initialized AIRI experimental Fabric client instrumentation for Minecraft 1.21.1");
	}
}

package io.github.airi.clientmod.transport;

import io.github.airi.clientmod.AiriUserClientMod;
import io.github.airi.clientmod.observation.InventoryDeltaDetector;
import io.github.airi.clientmod.observation.PeriodicMotionSampler;
import io.github.airi.clientmod.transport.contract.SessionStartPayload;
import io.github.airi.clientmod.transport.contract.TraceEventKinds;
import net.fabricmc.loader.api.FabricLoader;

public final class SessionStartPayloadSupplier {
	private static final int TRACE_VERSION = 3;
	private static final String PRODUCER_MOD_ID = "airi-user-client-mod";
	private static final String MINECRAFT_VERSION = "1.21.1";
	private static final String LOADER = "fabric";

	public SessionStartPayload createPayload() {
		return new SessionStartPayload(
			new SessionStartPayload.Metadata(
				new SessionStartPayload.Producer(
					PRODUCER_MOD_ID,
					resolveModVersion(),
					MINECRAFT_VERSION,
					LOADER
				),
				new SessionStartPayload.Schema(TRACE_VERSION)
			),
			new SessionStartPayload.Capabilities(TraceEventKinds.CAPABILITY_EVENT_KINDS),
			new SessionStartPayload.Sampling(
				PeriodicMotionSampler.playerMotionSamplingMode(),
				PeriodicMotionSampler.playerMotionSampleIntervalTicks(),
				PeriodicMotionSampler.playerMotionSampleIntervalMillis(),
				InventoryDeltaDetector.INVENTORY_SCAN_MODE,
				InventoryDeltaDetector.MAX_INVENTORY_SLOT_DELTAS
			)
		);
	}

	private static String resolveModVersion() {
		return FabricLoader.getInstance()
			.getModContainer(AiriUserClientMod.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}
}

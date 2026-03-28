package io.github.airi.clientmod.transport;

import java.util.List;
import java.util.Objects;

public record SessionStartPayload(
	Metadata metadata,
	Capabilities capabilities,
	Sampling sampling
) {
	public SessionStartPayload {
		metadata = Objects.requireNonNull(metadata, "metadata");
		capabilities = Objects.requireNonNull(capabilities, "capabilities");
		sampling = Objects.requireNonNull(sampling, "sampling");
	}

	public record Metadata(
		Producer producer,
		Schema schema
	) {
		public Metadata {
			producer = Objects.requireNonNull(producer, "producer");
			schema = Objects.requireNonNull(schema, "schema");
		}
	}

	public record Producer(
		String modId,
		String modVersion,
		String minecraftVersion,
		String loader
	) {
		public Producer {
			modId = Objects.requireNonNull(modId, "modId");
			modVersion = Objects.requireNonNull(modVersion, "modVersion");
			minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
			loader = Objects.requireNonNull(loader, "loader");
		}
	}

	public record Schema(
		int wireVersion,
		int canonicalVersion
	) {
	}

	public record Capabilities(
		List<String> eventKinds
	) {
		public Capabilities {
			eventKinds = List.copyOf(Objects.requireNonNull(eventKinds, "eventKinds"));
			for (String eventKind : eventKinds) {
				Objects.requireNonNull(eventKind, "eventKinds contains null value");
			}
		}
	}

	public record Sampling(
		int observationSampleIntervalTicks,
		String inventoryScanMode,
		int inventoryMaxChangedSlots
	) {
		public Sampling {
			inventoryScanMode = Objects.requireNonNull(inventoryScanMode, "inventoryScanMode");
		}
	}
}

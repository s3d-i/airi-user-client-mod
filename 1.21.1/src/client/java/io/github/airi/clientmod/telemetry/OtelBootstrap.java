package io.github.airi.clientmod.telemetry;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.github.airi.clientmod.AiriUserClientMod;
import io.github.airi.clientmod.transport.TransportTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import net.fabricmc.loader.api.FabricLoader;

public final class OtelBootstrap {
	private static final String ENABLED_PROPERTY = "airi.otel.enabled";
	private static final String METRICS_EXPORTER_PROPERTY = "airi.otel.metrics.exporter";
	private static final String METRICS_EXPORT_INTERVAL_PROPERTY = "airi.otel.metrics.export.interval.millis";
	private static final String SERVICE_NAME_PROPERTY = "airi.otel.service.name";
	private static final String DEFAULT_SERVICE_NAME = "airi-user-client-mod";
	private static final String CONSOLE_EXPORTER = "console";
	private static final long DEFAULT_EXPORT_INTERVAL_MILLIS = 5000L;
	private static final String INSTRUMENTATION_SCOPE_NAME = "io.github.airi.clientmod.transport";

	private static boolean initialized;
	private static TransportTelemetry transportTelemetry = TransportTelemetry.NOOP;
	private static SdkMeterProvider meterProvider;

	private OtelBootstrap() {
	}

	public static synchronized TransportTelemetry init() {
		if (initialized) {
			return transportTelemetry;
		}

		initialized = true;
		if (!Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"))) {
			return transportTelemetry;
		}

		String exporter = System.getProperty(METRICS_EXPORTER_PROPERTY, CONSOLE_EXPORTER).trim().toLowerCase(Locale.ROOT);
		if (!CONSOLE_EXPORTER.equals(exporter)) {
			AiriUserClientMod.LOGGER.warn(
				"Unsupported OTel metrics exporter '{}'; supported values: {}",
				exporter,
				CONSOLE_EXPORTER
			);
			return transportTelemetry;
		}

		long exportIntervalMillis = parseExportIntervalMillis();
		String serviceName = System.getProperty(SERVICE_NAME_PROPERTY, DEFAULT_SERVICE_NAME);

		PeriodicMetricReader metricReader = PeriodicMetricReader.builder(LoggingMetricExporter.create())
			.setInterval(Duration.ofMillis(exportIntervalMillis))
			.build();

		meterProvider = SdkMeterProvider.builder()
			.setResource(Resource.getDefault().merge(Resource.create(Attributes.of(
				AttributeKey.stringKey("service.name"), serviceName,
				AttributeKey.stringKey("service.version"), resolveServiceVersion()
			))))
			.registerMetricReader(metricReader)
			.build();

		OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
			.setMeterProvider(meterProvider)
			.build();

		Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE_NAME);
		transportTelemetry = new OtelTransportTelemetry(meter);
		Runtime.getRuntime().addShutdownHook(new Thread(OtelBootstrap::shutdown, "airi-otel-shutdown"));

		AiriUserClientMod.LOGGER.info(
			"OpenTelemetry transport metrics enabled with '{}' exporter at {} ms intervals",
			exporter,
			exportIntervalMillis
		);
		return transportTelemetry;
	}

	private static long parseExportIntervalMillis() {
		String configuredValue = System.getProperty(
			METRICS_EXPORT_INTERVAL_PROPERTY,
			Long.toString(DEFAULT_EXPORT_INTERVAL_MILLIS)
		);

		try {
			long parsedValue = Long.parseLong(configuredValue);
			if (parsedValue < 1000L) {
				throw new IllegalArgumentException("must be >= 1000");
			}
			return parsedValue;
		} catch (RuntimeException exception) {
			AiriUserClientMod.LOGGER.warn(
				"Invalid -D{}={} ; falling back to {}",
				METRICS_EXPORT_INTERVAL_PROPERTY,
				configuredValue,
				DEFAULT_EXPORT_INTERVAL_MILLIS
			);
			return DEFAULT_EXPORT_INTERVAL_MILLIS;
		}
	}

	private static String resolveServiceVersion() {
		return FabricLoader.getInstance()
			.getModContainer(AiriUserClientMod.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}

	private static void shutdown() {
		if (meterProvider == null) {
			return;
		}

		meterProvider.forceFlush().join(10, TimeUnit.SECONDS);
		meterProvider.shutdown().join(10, TimeUnit.SECONDS);
	}
}

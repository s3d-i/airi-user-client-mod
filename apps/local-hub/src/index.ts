import {
  CURRENT_MOD_TRACE_KIND_TRACE_SESSION_END,
  createHubRuntime,
  type HubLogger,
  type HubTraceSink,
  type HubRuntime,
  type ProjectionState
} from "@airi-client-mod/hub-runtime";
import { pathToFileURL } from "node:url";

import { createAiriWsBridge } from "@airi-client-mod/airi-ws-bridge";
import {
  createHubDebugSurfaceServer,
  type HubDebugSurfaceServer,
  type HubDebugSurfaceServerOptions
} from "@airi-client-mod/hub-debug-surface";
import {
  createHubIngressWsServer,
  type HubIngressWsServer,
  type HubIngressWsServerOptions
} from "@airi-client-mod/hub-ingress-ws";
import { createConsoleHubLogSink, createStructuredHubLogger } from "@airi-client-mod/hub-logging";
import { createHubTraceStore, type HubTraceStore } from "@airi-client-mod/hub-trace-store";

import { createMemoryHubLogBuffer } from "./logger/index.js";
import { createRawTraceJsonlWriter } from "./raw-trace/index.js";

export const DEFAULT_LOCAL_HUB_INGRESS_OPTIONS = {
  host: "127.0.0.1",
  port: 8787,
  path: "/ws"
} satisfies HubIngressWsServerOptions;

export const DEFAULT_LOCAL_HUB_DEBUG_SURFACE_OPTIONS = {
  host: "127.0.0.1",
  port: 8788,
  apiBasePath: "/api/debug",
  feedIntervalMillis: 1000
} satisfies HubDebugSurfaceServerOptions;

export interface LocalHubAppOptions {
  readonly ingress?: Partial<HubIngressWsServerOptions>;
  readonly debugSurface?: Partial<HubDebugSurfaceServerOptions>;
  readonly rawTraceDataDir?: string;
  readonly traceStoreCapacity?: number;
  readonly logBufferCapacity?: number;
  readonly logOutput?: Pick<Console, "debug" | "info" | "warn" | "error">;
}

export interface LocalHubApp {
  readonly bridge: ReturnType<typeof createAiriWsBridge>;
  readonly debugSurface: HubDebugSurfaceServer;
  readonly ingress: HubIngressWsServer;
  readonly logger: HubLogger;
  readonly runtime: HubRuntime;
  readonly traceStore: HubTraceStore;
  snapshot(): ProjectionState;
  start(): Promise<void>;
  stop(): Promise<void>;
}

function resolveIngressOptions(
  overrides?: Partial<HubIngressWsServerOptions>
): HubIngressWsServerOptions {
  return {
    ...DEFAULT_LOCAL_HUB_INGRESS_OPTIONS,
    ...overrides
  };
}

function resolveDebugSurfaceOptions(
  overrides?: Partial<HubDebugSurfaceServerOptions>
): HubDebugSurfaceServerOptions {
  return {
    ...DEFAULT_LOCAL_HUB_DEBUG_SURFACE_OPTIONS,
    ...overrides
  };
}

function isMainModule(metaUrl: string): boolean {
  const entryPoint = process.argv[1];
  return entryPoint != null && pathToFileURL(entryPoint).href === metaUrl;
}

export function createLocalHubApp(options: LocalHubAppOptions = {}): LocalHubApp {
  const logBuffer = createMemoryHubLogBuffer(options.logBufferCapacity);
  const logger = createStructuredHubLogger({
    scope: "local-hub",
    sinks: [createConsoleHubLogSink(options.logOutput), logBuffer]
  });
  const runtime = createHubRuntime({
    logger: logger.child("runtime")
  });
  const traceStore = createHubTraceStore({
    capacity: options.traceStoreCapacity,
    logger: logger.child("trace-store")
  });
  const rawTraceWriter = createRawTraceJsonlWriter({
    dataDir: options.rawTraceDataDir,
    logger: logger.child("raw-trace")
  });
  const traceSink: HubTraceSink = {
    acceptTrace(event) {
      rawTraceWriter.acceptTrace(event);

      if (event.kind === CURRENT_MOD_TRACE_KIND_TRACE_SESSION_END) {
        runtime.reset();
        traceStore.clear();
        return;
      }

      runtime.acceptTrace(event);
      traceStore.acceptTrace(event);
    }
  };
  const ingress = createHubIngressWsServer(traceSink, resolveIngressOptions(options.ingress), {
    logger: logger.child("ingress")
  });
  const bridge = createAiriWsBridge();
  const debugSurface = createHubDebugSurfaceServer(
    {
      ingress,
      runtime,
      traceStore,
      logs: logBuffer,
      logger: logger.child("debug-surface")
    },
    resolveDebugSurfaceOptions(options.debugSurface)
  );
  let started = false;

  return {
    bridge,
    debugSurface,
    ingress,
    logger,
    runtime,
    traceStore,
    snapshot() {
      return runtime.snapshot();
    },
    async start() {
      if (started) {
        return;
      }

      await debugSurface.start();

      try {
        await ingress.start();
        started = true;
        logger.info("local hub started", {
          debugSurfaceUrl: debugSurface.getBoundAddress()?.baseUrl,
          ingressUrl: ingress.getBoundAddress()?.url,
          rawTraceDataDir: rawTraceWriter.dataDir
        });
      } catch (error) {
        await debugSurface.stop();
        throw error;
      }
    },
    async stop() {
      if (!started) {
        await debugSurface.stop();
        await ingress.stop();
        rawTraceWriter.close();
        return;
      }

      started = false;
      await ingress.stop();
      await debugSurface.stop();
      rawTraceWriter.close();
      logger.info("local hub stopped");
    }
  };
}

export async function startLocalHubApp(options?: LocalHubAppOptions): Promise<LocalHubApp> {
  const app = createLocalHubApp(options);
  await app.start();
  return app;
}

async function runAsProcess(): Promise<void> {
  const app = await startLocalHubApp();
  const processLogger = app.logger.child("process");
  let shutdownPromise: Promise<void> | null = null;

  const shutdown = (signal: NodeJS.Signals): Promise<void> => {
    if (shutdownPromise) {
      return shutdownPromise;
    }

    shutdownPromise = (async () => {
      processLogger.info("received shutdown signal", {
        signal
      });
      await app.stop();
      processLogger.info("final runtime snapshot", {
        snapshot: app.snapshot()
      });
    })();

    return shutdownPromise;
  };

  const handleSignal = (signal: NodeJS.Signals) => {
    void shutdown(signal).catch(error => {
      processLogger.error("shutdown failed", {
        error: error instanceof Error ? error.message : String(error),
        signal
      });
      process.exitCode = 1;
    });
  };

  process.on("SIGINT", handleSignal);
  process.on("SIGTERM", handleSignal);
}

if (isMainModule(import.meta.url)) {
  try {
    await runAsProcess();
  } catch (error) {
    console.error(
      `[local-hub.process] failed to start ${JSON.stringify({
        error: error instanceof Error ? error.message : String(error)
      })}`
    );
    process.exitCode = 1;
  }
}

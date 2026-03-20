import { pathToFileURL } from "node:url";

import { createAiriWsBridge } from "@airi-client-mod/airi-ws-bridge";
import {
  createHubIngressWsServer,
  type HubIngressWsServer,
  type HubIngressWsServerOptions
} from "@airi-client-mod/hub-ingress-ws";
import {
  createHubRuntime,
  type HubRuntime,
  type ProjectionState
} from "@airi-client-mod/hub-runtime";

export const DEFAULT_LOCAL_HUB_INGRESS_OPTIONS = {
  host: "127.0.0.1",
  port: 8787,
  path: "/ws"
} satisfies HubIngressWsServerOptions;

export interface LocalHubAppOptions {
  readonly ingress?: Partial<HubIngressWsServerOptions>;
  readonly log?: Pick<Console, "error" | "info">;
}

export interface LocalHubApp {
  readonly bridge: ReturnType<typeof createAiriWsBridge>;
  readonly ingress: HubIngressWsServer;
  readonly runtime: HubRuntime;
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

function isMainModule(metaUrl: string): boolean {
  const entryPoint = process.argv[1];
  return entryPoint != null && pathToFileURL(entryPoint).href === metaUrl;
}

export function createLocalHubApp(options: LocalHubAppOptions = {}): LocalHubApp {
  const logger = options.log ?? console;
  const runtime = createHubRuntime();
  const ingress = createHubIngressWsServer(runtime, resolveIngressOptions(options.ingress));
  const bridge = createAiriWsBridge();
  let started = false;

  return {
    bridge,
    ingress,
    runtime,
    snapshot() {
      return runtime.snapshot();
    },
    async start() {
      if (started) {
        return;
      }

      const address = await ingress.start();
      started = true;
      logger.info(`[local-hub] ingress listening on ${address.url}`);
    },
    async stop() {
      if (!started) {
        await ingress.stop();
        return;
      }

      started = false;
      await ingress.stop();
      logger.info("[local-hub] ingress stopped");
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
  let shutdownPromise: Promise<void> | null = null;

  const shutdown = (signal: NodeJS.Signals): Promise<void> => {
    if (shutdownPromise) {
      return shutdownPromise;
    }

    shutdownPromise = (async () => {
      console.info(`[local-hub] received ${signal}, shutting down`);
      await app.stop();
      console.info(`[local-hub] final snapshot ${JSON.stringify(app.snapshot())}`);
    })();

    return shutdownPromise;
  };

  const handleSignal = (signal: NodeJS.Signals) => {
    void shutdown(signal).catch(error => {
      const message = error instanceof Error ? error.message : String(error);
      console.error(`[local-hub] shutdown failed: ${message}`);
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
    const message = error instanceof Error ? error.message : String(error);
    console.error(`[local-hub] failed to start: ${message}`);
    process.exitCode = 1;
  }
}

import type { HubRuntime } from "@airi-client-mod/hub-runtime";

import { plugin as websocketPlugin } from "crossws/server";
import { defineWebSocketHandler, H3, serve } from "h3";

import { parseRawTraceEventMessage } from "../adapter/index.js";

export interface HubIngressWsServerOptions {
  readonly host: string;
  readonly port: number;
  readonly path: string;
}

export interface HubIngressWsBoundAddress extends HubIngressWsServerOptions {
  readonly url: string;
}

export interface HubIngressWsServer {
  readonly options: HubIngressWsServerOptions;
  start(): Promise<HubIngressWsBoundAddress>;
  stop(): Promise<void>;
  getBoundAddress(): HubIngressWsBoundAddress | null;
}

interface ManagedServerInstance {
  readonly close: (closeActiveConnections?: boolean) => Promise<void>;
}

function createBoundAddress(
  options: HubIngressWsServerOptions,
  serverUrl?: string
): HubIngressWsBoundAddress {
  const effectiveUrl = serverUrl == null
    ? new URL(`http://${options.host}:${options.port}`)
    : new URL(serverUrl);
  const protocol = effectiveUrl.protocol === "https:" ? "wss:" : "ws:";
  const port = effectiveUrl.port.length > 0 ? Number(effectiveUrl.port) : options.port;

  return {
    host: effectiveUrl.hostname,
    port,
    path: options.path,
    url: `${protocol}//${effectiveUrl.hostname}:${port}${options.path}`
  };
}

function normalizeOptions(options: HubIngressWsServerOptions): HubIngressWsServerOptions {
  const path = options.path.startsWith("/") ? options.path : `/${options.path}`;

  if (!Number.isInteger(options.port) || options.port < 0 || options.port > 65_535) {
    throw new RangeError(`invalid websocket ingress port: ${options.port}`);
  }

  if (options.host.length === 0) {
    throw new Error("websocket ingress host must be a non-empty string");
  }

  return {
    host: options.host,
    port: options.port,
    path
  };
}

function describeError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function createHubIngressWsServer(
  runtime: Pick<HubRuntime, "acceptTrace">,
  options: HubIngressWsServerOptions
): HubIngressWsServer {
  const resolvedOptions = normalizeOptions(options);
  let serverInstance: ManagedServerInstance | null = null;
  let boundAddress: HubIngressWsBoundAddress | null = null;

  return {
    options: resolvedOptions,
    async start() {
      if (serverInstance && boundAddress) {
        return boundAddress;
      }

      const peers = new Set<{ close?: () => void }>();
      const app = new H3({
        onError(error) {
          console.error(`[hub-ingress-ws] app error: ${describeError(error)}`);
        }
      });

      app.get(
        resolvedOptions.path,
        defineWebSocketHandler({
          open(peer) {
            peers.add(peer);
          },
          close(peer) {
            peers.delete(peer);
          },
          message(peer, message) {
            const result = parseRawTraceEventMessage(message.text());

            if (!result.ok) {
              console.warn(
                `[hub-ingress-ws] rejected frame from peer ${peer.id}: ${result.reason}`
              );
              return;
            }

            runtime.acceptTrace(result.event);
          }
        })
      );

      const instance = serve(app, {
        // @ts-expect-error - h3's response types do not expose the crossws extension.
        plugins: [websocketPlugin({ resolve: async request => (await app.fetch(request)).crossws })],
        port: resolvedOptions.port,
        hostname: resolvedOptions.host,
        reusePort: true,
        silent: true,
        manual: true,
        gracefulShutdown: {
          forceTimeout: 0.5,
          gracefulTimeout: 0.5
        }
      });

      serverInstance = {
        close: async (closeActiveConnections = false) => {
          for (const peer of peers) {
            try {
              peer.close?.();
            } catch {
              // Best-effort shutdown only.
            }
          }

          peers.clear();
          await instance.close(closeActiveConnections);
        }
      };

      instance.serve();
      await instance.ready();

      const nextBoundAddress = createBoundAddress(resolvedOptions, instance.url);
      boundAddress = nextBoundAddress;

      return nextBoundAddress;
    },
    async stop() {
      if (!serverInstance) {
        return;
      }

      const instance = serverInstance;
      serverInstance = null;
      boundAddress = null;

      await instance.close(true);
    },
    getBoundAddress() {
      return boundAddress;
    }
  };
}

export const createHubIngressWs = createHubIngressWsServer;

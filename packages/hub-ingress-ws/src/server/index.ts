import {
  createNoopHubLogger,
  decodeCurrentModTraceEvent,
  type HubLogger,
  type HubTraceSink
} from "@airi-client-mod/hub-runtime";

import { plugin as websocketPlugin } from "crossws/server";
import { defineWebSocketHandler, H3, serve } from "h3";

export interface HubIngressWsServerOptions {
  readonly host: string;
  readonly port: number;
  readonly path: string;
}

export interface HubIngressWsBoundAddress extends HubIngressWsServerOptions {
  readonly url: string;
}

export interface HubIngressWsStatusSnapshot {
  readonly listening: boolean;
  readonly startedAt?: number;
  readonly boundAddress: HubIngressWsBoundAddress | null;
  readonly connectedPeers: number;
  readonly acceptedFrames: number;
  readonly rejectedFrames: number;
  readonly handoffFailures: number;
  readonly lastAcceptedAt?: number;
  readonly lastRejectedAt?: number;
  readonly lastRejectedReason?: string;
}

export interface HubIngressWsServerDependencies {
  readonly logger?: HubLogger;
}

export interface HubIngressWsServer {
  readonly options: HubIngressWsServerOptions;
  start(): Promise<HubIngressWsBoundAddress>;
  stop(): Promise<void>;
  getBoundAddress(): HubIngressWsBoundAddress | null;
  status(): HubIngressWsStatusSnapshot;
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
  traceSink: HubTraceSink,
  options: HubIngressWsServerOptions,
  dependencies: HubIngressWsServerDependencies = {}
): HubIngressWsServer {
  const resolvedOptions = normalizeOptions(options);
  const logger = dependencies.logger ?? createNoopHubLogger("hub.ingress");
  let serverInstance: ManagedServerInstance | null = null;
  let boundAddress: HubIngressWsBoundAddress | null = null;
  let startedAt: number | undefined;
  let connectedPeers = 0;
  let acceptedFrames = 0;
  let rejectedFrames = 0;
  let handoffFailures = 0;
  let lastAcceptedAt: number | undefined;
  let lastRejectedAt: number | undefined;
  let lastRejectedReason: string | undefined;

  const recordRejection = (reason: string, peerId?: string) => {
    rejectedFrames += 1;
    lastRejectedAt = Date.now();
    lastRejectedReason = reason;
    logger.warn("rejected ingress frame", {
      peerId,
      reason,
      rejectedFrames
    });
  };

  return {
    options: resolvedOptions,
    async start() {
      if (serverInstance && boundAddress) {
        return boundAddress;
      }

      const peers = new Set<{ close?: () => void }>();
      const app = new H3({
        onError(error) {
          logger.error("ingress app error", {
            error: describeError(error)
          });
        }
      });

      app.get(
        resolvedOptions.path,
        defineWebSocketHandler({
          open(peer) {
            peers.add(peer);
            connectedPeers = peers.size;
            logger.info("peer connected", {
              connectedPeers,
              peerId: String(peer.id)
            });
          },
          close(peer) {
            peers.delete(peer);
            connectedPeers = peers.size;
            logger.info("peer disconnected", {
              connectedPeers,
              peerId: String(peer.id)
            });
          },
          message(peer, message) {
            const peerId = String(peer.id);
            let decodedFrame: unknown;

            try {
              decodedFrame = JSON.parse(message.text());
            } catch (error) {
              recordRejection(`invalid JSON: ${describeError(error)}`, peerId);
              return;
            }

            const result = decodeCurrentModTraceEvent(decodedFrame);

            if (!result.ok) {
              recordRejection(result.reason, peerId);
              return;
            }

            try {
              traceSink.acceptTrace(result.event);
              acceptedFrames += 1;
              lastAcceptedAt = Date.now();
              logger.debug("accepted ingress frame", {
                kind: result.event.kind,
                peerId,
                seq: result.event.seq,
                sessionId: result.event.sessionId,
                acceptedFrames
              });
            } catch (error) {
              handoffFailures += 1;
              logger.error("failed to hand off decoded trace", {
                error: describeError(error),
                handoffFailures,
                peerId
              });
            }
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
      startedAt = Date.now();
      logger.info("ingress listening", {
        url: nextBoundAddress.url
      });

      return nextBoundAddress;
    },
    async stop() {
      if (!serverInstance) {
        return;
      }

      const instance = serverInstance;
      serverInstance = null;
      boundAddress = null;
      connectedPeers = 0;
      startedAt = undefined;

      await instance.close(true);
      logger.info("ingress stopped");
    },
    getBoundAddress() {
      return boundAddress;
    },
    status() {
      return {
        listening: serverInstance != null,
        startedAt,
        boundAddress,
        connectedPeers,
        acceptedFrames,
        rejectedFrames,
        handoffFailures,
        lastAcceptedAt,
        lastRejectedAt,
        lastRejectedReason
      };
    }
  };
}

export const createHubIngressWs = createHubIngressWsServer;

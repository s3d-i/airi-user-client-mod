import { createServer, type IncomingMessage, type Server as HttpServer, type ServerResponse } from "node:http";

import type { HubIngressWsStatusSnapshot } from "@airi-client-mod/hub-ingress-ws";
import type { HubLogEntry, HubLogger, HubRuntime, ProjectionState } from "@airi-client-mod/hub-runtime";
import type { HubTraceStore, HubTraceStoreSnapshot, RetainedTraceRecord } from "@airi-client-mod/hub-trace-store";

const DEFAULT_DEBUG_STATE_TRACE_LIMIT = 25;
const DEFAULT_DEBUG_STATE_LOG_LIMIT = 100;

export interface HubLogStoreSnapshot {
  readonly capacity: number;
  readonly retainedCount: number;
  readonly droppedCount: number;
  readonly lastEntryAt?: number;
}

export interface HubLogReader {
  listRecent(limit?: number): readonly HubLogEntry[];
  snapshot(): HubLogStoreSnapshot;
}

export interface HubDebugSurfaceStateQuery {
  readonly traceLimit?: number;
  readonly logLimit?: number;
}

export interface HubDebugState {
  readonly generatedAtMillis: number;
  readonly ingress: HubIngressWsStatusSnapshot;
  readonly runtime: ProjectionState;
  readonly traceStore: HubTraceStoreSnapshot;
  readonly logging: HubLogStoreSnapshot;
  readonly traces: readonly RetainedTraceRecord[];
  readonly logs: readonly HubLogEntry[];
}

export interface HubDebugSurfaceBoundAddress extends HubDebugSurfaceServerOptions {
  readonly baseUrl: string;
  readonly feedUrl: string;
}

export interface HubDebugSurfaceServerOptions {
  readonly host: string;
  readonly port: number;
  readonly apiBasePath: string;
  readonly feedIntervalMillis: number;
}

export interface HubDebugSurfaceServerDependencies {
  readonly ingress: Pick<{ status(): HubIngressWsStatusSnapshot }, "status">;
  readonly runtime: Pick<HubRuntime, "snapshot">;
  readonly traceStore: Pick<HubTraceStore, "listRecent" | "snapshot">;
  readonly logs: HubLogReader;
  readonly logger: HubLogger;
}

type CloseDebugFeedClient = () => void;

export interface HubDebugSurfaceServer {
  readonly options: HubDebugSurfaceServerOptions;
  start(): Promise<HubDebugSurfaceBoundAddress>;
  stop(): Promise<void>;
  getBoundAddress(): HubDebugSurfaceBoundAddress | null;
  buildState(query?: HubDebugSurfaceStateQuery): HubDebugState;
}

export function createHubDebugSurfaceServer(
  dependencies: HubDebugSurfaceServerDependencies,
  options: HubDebugSurfaceServerOptions
): HubDebugSurfaceServer {
  const resolvedOptions = normalizeOptions(options);
  const { logger } = dependencies;
  let server: HttpServer | null = null;
  let boundAddress: HubDebugSurfaceBoundAddress | null = null;
  const activeFeedClients = new Set<CloseDebugFeedClient>();

  const buildState = (query: HubDebugSurfaceStateQuery = {}): HubDebugState => {
    const traceLimit = query.traceLimit ?? DEFAULT_DEBUG_STATE_TRACE_LIMIT;
    const logLimit = query.logLimit ?? DEFAULT_DEBUG_STATE_LOG_LIMIT;

    return {
      generatedAtMillis: Date.now(),
      ingress: dependencies.ingress.status(),
      runtime: dependencies.runtime.snapshot(),
      traceStore: dependencies.traceStore.snapshot(),
      logging: dependencies.logs.snapshot(),
      traces: dependencies.traceStore.listRecent({ limit: traceLimit }),
      logs: dependencies.logs.listRecent(logLimit)
    };
  };

  return {
    options: resolvedOptions,
    async start() {
      if (server && boundAddress) {
        return boundAddress;
      }

      const nextServer = createServer((request, response) => {
        handleRequest(request, response, resolvedOptions, buildState, logger, activeFeedClients);
      });

      await listen(nextServer, resolvedOptions.host, resolvedOptions.port);

      server = nextServer;
      boundAddress = createBoundAddress(resolvedOptions, nextServer);
      logger.info("debug surface listening", {
        baseUrl: boundAddress.baseUrl,
        feedUrl: boundAddress.feedUrl
      });

      return boundAddress;
    },
    async stop() {
      if (!server) {
        return;
      }

      const currentServer = server;
      const currentFeedClients = [...activeFeedClients];

      server = null;
      boundAddress = null;

      activeFeedClients.clear();
      for (const closeFeedClient of currentFeedClients) {
        closeFeedClient();
      }

      await closeServer(currentServer);
      logger.info("debug surface stopped");
    },
    getBoundAddress() {
      return boundAddress;
    },
    buildState
  };
}

function normalizeOptions(options: HubDebugSurfaceServerOptions): HubDebugSurfaceServerOptions {
  const apiBasePath = options.apiBasePath.startsWith("/")
    ? options.apiBasePath
    : `/${options.apiBasePath}`;

  if (!Number.isInteger(options.port) || options.port < 0 || options.port > 65_535) {
    throw new RangeError(`invalid debug surface port: ${options.port}`);
  }

  if (options.host.length === 0) {
    throw new Error("debug surface host must be a non-empty string");
  }

  if (!Number.isInteger(options.feedIntervalMillis) || options.feedIntervalMillis <= 0) {
    throw new RangeError(`invalid debug feed interval: ${options.feedIntervalMillis}`);
  }

  return {
    host: options.host,
    port: options.port,
    apiBasePath,
    feedIntervalMillis: options.feedIntervalMillis
  };
}

function createBoundAddress(
  options: HubDebugSurfaceServerOptions,
  server: HttpServer
): HubDebugSurfaceBoundAddress {
  const address = server.address();

  if (address == null || typeof address === "string") {
    throw new Error("debug surface did not bind to an IP address");
  }

  const host = address.address === "::" ? options.host : address.address;
  const baseUrl = `http://${host}:${address.port}${options.apiBasePath}`;

  return {
    host,
    port: address.port,
    apiBasePath: options.apiBasePath,
    feedIntervalMillis: options.feedIntervalMillis,
    baseUrl,
    feedUrl: `${baseUrl}/feed`
  };
}

function handleRequest(
  request: IncomingMessage,
  response: ServerResponse,
  options: HubDebugSurfaceServerOptions,
  buildState: (query?: HubDebugSurfaceStateQuery) => HubDebugState,
  logger: HubLogger,
  activeFeedClients: Set<CloseDebugFeedClient>
): void {
  setCorsHeaders(response);

  try {
    const requestUrl = new URL(request.url ?? "/", `http://${request.headers.host ?? "127.0.0.1"}`);
    const pathname = requestUrl.pathname;
    const basePath = options.apiBasePath;

    if (request.method === "OPTIONS") {
      response.writeHead(204);
      response.end();
      return;
    }

    if (request.method !== "GET") {
      writeJson(response, 405, {
        error: "method not allowed"
      });
      return;
    }

    if (pathname === basePath || pathname === `${basePath}/`) {
      writeJson(response, 200, {
        state: `${basePath}/state`,
        traces: `${basePath}/traces`,
        logs: `${basePath}/logs`,
        feed: `${basePath}/feed`,
        health: `${basePath}/health`
      });
      return;
    }

    if (pathname === `${basePath}/health`) {
      writeJson(response, 200, {
        ok: true,
        generatedAtMillis: Date.now()
      });
      return;
    }

    if (pathname === `${basePath}/state`) {
      writeJson(response, 200, buildState(readStateQuery(requestUrl)));
      return;
    }

    if (pathname === `${basePath}/traces`) {
      const state = buildState({
        traceLimit: readOptionalIntegerQuery(requestUrl, "limit")
      });

      writeJson(response, 200, {
        generatedAtMillis: state.generatedAtMillis,
        traceStore: state.traceStore,
        traces: state.traces
      });
      return;
    }

    if (pathname === `${basePath}/logs`) {
      const state = buildState({
        logLimit: readOptionalIntegerQuery(requestUrl, "limit")
      });

      writeJson(response, 200, {
        generatedAtMillis: state.generatedAtMillis,
        logging: state.logging,
        logs: state.logs
      });
      return;
    }

    if (pathname === `${basePath}/feed`) {
      logger.info("opened debug feed client");
      let closeFeedClient: CloseDebugFeedClient;
      closeFeedClient = openStateFeed(
        request,
        response,
        options.feedIntervalMillis,
        buildState,
        () => activeFeedClients.delete(closeFeedClient)
      );
      activeFeedClients.add(closeFeedClient);
      return;
    }

    writeJson(response, 404, {
      error: "not found"
    });
  } catch (error) {
    logger.warn("failed to serve debug request", {
      error: error instanceof Error ? error.message : String(error)
    });
    writeJson(response, 400, {
      error: error instanceof Error ? error.message : String(error)
    });
  }
}

function openStateFeed(
  request: IncomingMessage,
  response: ServerResponse,
  feedIntervalMillis: number,
  buildState: (query?: HubDebugSurfaceStateQuery) => HubDebugState,
  onClose?: () => void
): CloseDebugFeedClient {
  const requestUrl = new URL(request.url ?? "/", `http://${request.headers.host ?? "127.0.0.1"}`);
  const query = readStateQuery(requestUrl);

  response.writeHead(200, {
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-cache, no-transform",
    Connection: "keep-alive"
  });

  response.write(": connected\n\n");
  response.write(renderSseEvent("state", buildState(query)));

  const interval = setInterval(() => {
    response.write(renderSseEvent("state", buildState(query)));
  }, feedIntervalMillis);

  let closed = false;
  const cleanup = () => {
    if (closed) {
      return;
    }

    closed = true;
    clearInterval(interval);
    onClose?.();
    response.end();
  };

  request.on("close", cleanup);
  request.on("error", cleanup);
  response.on("close", cleanup);
  response.on("error", cleanup);

  return cleanup;
}

function renderSseEvent(eventName: string, payload: unknown): string {
  return `event: ${eventName}\ndata: ${JSON.stringify(payload)}\n\n`;
}

function readStateQuery(requestUrl: URL): HubDebugSurfaceStateQuery {
  return {
    traceLimit: readOptionalIntegerQuery(requestUrl, "traceLimit"),
    logLimit: readOptionalIntegerQuery(requestUrl, "logLimit")
  };
}

function readOptionalIntegerQuery(requestUrl: URL, key: string): number | undefined {
  const value = requestUrl.searchParams.get(key);

  if (value == null) {
    return undefined;
  }

  const parsed = Number(value);

  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new RangeError(`invalid integer query parameter: ${key}=${value}`);
  }

  return parsed;
}

function setCorsHeaders(response: ServerResponse): void {
  response.setHeader("Access-Control-Allow-Origin", "*");
  response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  response.setHeader("Access-Control-Allow-Headers", "Content-Type");
}

function writeJson(response: ServerResponse, statusCode: number, body: unknown): void {
  response.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8"
  });
  response.end(JSON.stringify(body, null, 2));
}

function listen(server: HttpServer, host: string, port: number): Promise<void> {
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(port, host, () => {
      server.off("error", reject);
      resolve();
    });
  });
}

function closeServer(server: HttpServer): Promise<void> {
  return new Promise((resolve, reject) => {
    server.close(error => {
      if (error) {
        reject(error);
        return;
      }

      resolve();
    });
  });
}

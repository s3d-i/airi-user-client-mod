import type { HubDebugState } from "@airi-client-mod/hub-debug-surface";

const DEFAULT_DEBUG_SURFACE_BASE_URL = "http://127.0.0.1:8788/api/debug";
const DEFAULT_DEBUG_STATE_TRACE_LIMIT = 200;

const DEFAULT_DEBUG_STATE_QUERY = new URLSearchParams({
  traceLimit: String(DEFAULT_DEBUG_STATE_TRACE_LIMIT)
}).toString();

export function resolveDebugSurfaceBaseUrl(): string {
  const configured = import.meta.env.VITE_DEBUG_SURFACE_BASE_URL;

  if (configured == null || configured.length === 0) {
    return DEFAULT_DEBUG_SURFACE_BASE_URL;
  }

  return configured.endsWith("/") ? configured.slice(0, -1) : configured;
}

export async function fetchDebugState(signal?: AbortSignal): Promise<HubDebugState> {
  const response = await fetch(`${resolveDebugSurfaceBaseUrl()}/state?${DEFAULT_DEBUG_STATE_QUERY}`, {
    signal
  });

  if (!response.ok) {
    throw new Error(`debug state request failed with ${response.status}`);
  }

  return response.json() as Promise<HubDebugState>;
}

export function openDebugStateFeed(handlers: {
  readonly onState: (state: HubDebugState) => void;
  readonly onError: (message: string) => void;
}): () => void {
  const feed = new EventSource(`${resolveDebugSurfaceBaseUrl()}/feed?${DEFAULT_DEBUG_STATE_QUERY}`);

  feed.addEventListener("state", event => {
    try {
      handlers.onState(JSON.parse((event as MessageEvent<string>).data) as HubDebugState);
    } catch (error) {
      handlers.onError(error instanceof Error ? error.message : String(error));
    }
  });

  feed.onerror = () => {
    handlers.onError("debug feed disconnected");
  };

  return () => {
    feed.close();
  };
}

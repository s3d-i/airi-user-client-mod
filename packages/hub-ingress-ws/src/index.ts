import type { HubRuntime } from "@airi-client-mod/hub-runtime";

export type { IngressWsFrame } from "./adapter/index.js";
export type { HubIngressWsServerOptions } from "./server/index.js";

import type { IngressWsFrame } from "./adapter/index.js";
import type { HubIngressWsServerOptions } from "./server/index.js";

export interface HubIngressWsServer {
  readonly options: HubIngressWsServerOptions;
  ingest(frame: IngressWsFrame): void;
}

export function createHubIngressWs(
  runtime: Pick<HubRuntime, "acceptTrace">,
  options: HubIngressWsServerOptions
): HubIngressWsServer {
  return {
    options,
    ingest(frame) {
      runtime.acceptTrace(frame.event);
    }
  };
}

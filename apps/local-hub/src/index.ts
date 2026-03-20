import { createAiriWsBridge } from "@airi/airi-ws-bridge";
import { createHubIngressWs } from "@airi/hub-ingress-ws";
import { createHubRuntime } from "@airi/hub-runtime";

export interface LocalHubApp {
  readonly bridge: ReturnType<typeof createAiriWsBridge>;
  readonly ingress: ReturnType<typeof createHubIngressWs>;
  readonly runtime: ReturnType<typeof createHubRuntime>;
}

export function createLocalHubApp(): LocalHubApp {
  const runtime = createHubRuntime();
  const ingress = createHubIngressWs(runtime, { port: 0 });
  const bridge = createAiriWsBridge();

  return {
    bridge,
    ingress,
    runtime
  };
}

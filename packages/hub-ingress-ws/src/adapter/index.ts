import type { RawTraceEvent } from "@airi-client-mod/hub-runtime";

export interface IngressWsFrame {
  readonly event: RawTraceEvent;
}

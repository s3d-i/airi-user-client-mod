import type { RawTraceEvent } from "@airi/hub-runtime";

export interface IngressWsFrame {
  readonly event: RawTraceEvent;
}

import type { ProjectionState } from "./projection/index.js";
import type { RawTraceEvent } from "./trace/index.js";

export type { DetectorSignal } from "./detector/index.js";
export type { EpisodeOutput } from "./episode/index.js";
export type { ProjectionState } from "./projection/index.js";
export type { RawTraceEvent } from "./trace/index.js";

export interface HubRuntime {
  acceptTrace(event: RawTraceEvent): void;
  snapshot(): ProjectionState;
}

export function createHubRuntime(): HubRuntime {
  let traceCount = 0;

  return {
    acceptTrace(_event) {
      traceCount += 1;
    },
    snapshot() {
      return { traceCount };
    }
  };
}

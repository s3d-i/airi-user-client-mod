import type { ProjectionState } from "./projection/index.js";
import type { ObservationSampleTraceEvent, RawTraceEvent } from "./trace/index.js";

export type { DetectorSignal } from "./detector/index.js";
export type { EpisodeOutput } from "./episode/index.js";
export type { ProjectionState } from "./projection/index.js";
export type {
  ObservationSampleTraceEvent,
  ObservationSampleTracePayload,
  RawTraceEvent
} from "./trace/index.js";

export interface HubRuntime {
  acceptTrace(event: RawTraceEvent): void;
  snapshot(): ProjectionState;
}

export function createHubRuntime(): HubRuntime {
  let traceCount = 0;
  let latestObservation: ObservationSampleTraceEvent | undefined;
  let lastAcceptedAt: number | undefined;

  return {
    acceptTrace(event) {
      traceCount += 1;
      latestObservation = event;
      lastAcceptedAt = Date.now();
    },
    snapshot() {
      return {
        traceCount,
        latestObservation,
        lastAcceptedAt
      };
    }
  };
}

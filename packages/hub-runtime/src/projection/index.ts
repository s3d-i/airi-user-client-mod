import type { ObservationSampleTraceEvent } from "../trace/index.js";

export interface ProjectionState {
  readonly traceCount: number;
  readonly latestObservation?: ObservationSampleTraceEvent;
  readonly lastAcceptedAt?: number;
}

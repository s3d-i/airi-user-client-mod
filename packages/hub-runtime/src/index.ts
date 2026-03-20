import type { HubLogger } from "./logging/index.js";
import type { ProjectionState } from "./projection/index.js";
import type { ObservationSampleTraceEvent, RawTraceEvent } from "./trace/index.js";

export type { DetectorSignal } from "./detector/index.js";
export type { EpisodeOutput } from "./episode/index.js";
export type {
  HubLogEntry,
  HubLogFields,
  HubLogger,
  HubLoggerFactory,
  HubLogSink,
  HubLogLevel
} from "./logging/index.js";
export type { ProjectionState } from "./projection/index.js";
export type {
  CurrentModObservationSampleTraceEvent,
  CurrentModObservationSampleTracePayload,
  CurrentModTraceEvent,
  ObservationSampleTraceEvent,
  ObservationSampleTracePayload,
  RawTraceDecodeFailure,
  RawTraceDecodeResult,
  RawTraceDecodeSuccess,
  RawTraceEvent
} from "./trace/index.js";
export {
  CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE,
  CURRENT_MOD_TRACE_VERSION,
  decodeCurrentModTraceEvent
} from "./trace/index.js";

export interface HubTraceSink {
  acceptTrace(event: RawTraceEvent): void;
}

export interface HubRuntime extends HubTraceSink {
  snapshot(): ProjectionState;
}

export interface CreateHubRuntimeOptions {
  readonly logger: HubLogger;
}

export function createHubRuntime(options: CreateHubRuntimeOptions): HubRuntime {
  const { logger } = options;
  let traceCount = 0;
  let latestObservation: ObservationSampleTraceEvent | undefined;
  let lastAcceptedAt: number | undefined;

  return {
    acceptTrace(event) {
      traceCount += 1;
      latestObservation = event;
      lastAcceptedAt = Date.now();
      logger.debug("accepted trace", {
        kind: event.kind,
        seq: event.seq,
        sessionId: event.sessionId,
        traceCount
      });
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

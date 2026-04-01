import type { HubLogger } from "./logging/index.js";
import {
  createInitialEpisodeMachineState,
  stepEpisodeMachine,
  type EpisodeMachineState,
  type EpisodeOutput
} from "./episode/index.js";
import { evaluateDetectors, type DetectorSnapshot, type DetectorSignal } from "./detector/index.js";
import {
  createEmptyProjectionSnapshot,
  reduceProjectionSnapshot,
  type ProjectionSnapshot
} from "./projection/index.js";
import {
  CURRENT_MOD_TRACE_KIND_PLAYER_MOTION_SAMPLE,
  type PlayerMotionSampleTraceEvent,
  type RawTraceEvent
} from "./trace/index.js";

export type { DetectorSignal, DetectorSnapshot } from "./detector/index.js";
export type { EpisodeOutput } from "./episode/index.js";
export type {
  HubLogEntry,
  HubLogFields,
  HubLogger,
  HubLoggerFactory,
  HubLogSink,
  HubLogLevel
} from "./logging/index.js";
export type {
  AggregatedItemDelta,
  ContinuityProjectionSnapshot,
  FocusProjectionSnapshot,
  HandProjectionSnapshot,
  InteractionWindowProjectionSnapshot,
  InventoryDeltaProjectionSnapshot,
  MotionProjectionSnapshot,
  ProjectionSnapshot,
  ResourceCategoryCountMap
} from "./projection/index.js";
export type {
  CanonicalTraceEvent,
  CurrentModWireTraceEvent,
  CurrentModPlayerMotionSampleTraceEvent,
  CurrentModPlayerMotionSampleTracePayload,
  CurrentModSessionEndTraceEvent,
  CurrentModSessionStartTraceEvent,
  CurrentModTraceEvent,
  InteractionBlockUseAttemptTraceEvent,
  InteractionBlockAttackAttemptTraceEvent,
  InteractionBlockBreakSuccessTraceEvent,
  InteractionBlockTracePayload,
  InteractionEntityAttackAttemptTraceEvent,
  InteractionEntityTracePayload,
  InteractionEntityUseAttemptTraceEvent,
  InteractionItemTracePayload,
  InteractionItemUseAttemptTraceEvent,
  InventoryTransactionTraceEvent,
  InventoryTransactionTracePayload,
  PlayerMotionSampleTraceEvent,
  PlayerMotionSampleTracePayload,
  PlayerHandStateChangedTraceEvent,
  PlayerHandStateChangedTracePayload,
  PlayerLookTargetChangedTraceEvent,
  PlayerLookTargetChangedTracePayload,
  PlayerSelectedSlotChangedTraceEvent,
  PlayerSelectedSlotChangedTracePayload,
  RawTraceEvent,
  SessionEndTraceEvent,
  SessionStartTraceEvent,
  TraceBlockFace,
  TraceBlockPosition,
  TraceEvidenceRef,
  TraceHandType,
  TraceInventorySlotDelta,
  TraceItemStackSnapshot,
  TraceLookTarget,
  TraceLookTargetBlockDetails,
  TraceLookTargetEntityDetails,
  TraceTargetKind
} from "./trace/index.js";
export {
  createRawTraceId,
  CURRENT_MOD_TRACE_KIND_TRACE_SESSION_END,
  CURRENT_MOD_TRACE_KIND_TRACE_SESSION_START,
  CURRENT_MOD_TRACE_KIND_PLAYER_MOTION_SAMPLE,
  CURRENT_MOD_TRACE_KIND_INTERACTION_ITEM_USE_ATTEMPT,
  CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_USE_ATTEMPT,
  CURRENT_MOD_TRACE_KIND_INTERACTION_ENTITY_USE_ATTEMPT,
  CURRENT_MOD_TRACE_KIND_INTERACTION_ENTITY_ATTACK_ATTEMPT,
  CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_ATTACK_ATTEMPT,
  CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK_SUCCESS,
  CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION,
  CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED,
  CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED,
  CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED,
  CURRENT_MOD_CANONICAL_VERSION,
  CURRENT_MOD_WS_PROTOCOL_VERSION,
  decodeCurrentModTraceEvent,
  toTraceEvidenceRef
} from "./trace/index.js";

export interface HubTraceSink {
  acceptTrace(event: RawTraceEvent): void;
}

export interface HubRuntimeSnapshot {
  readonly traceCount: number;
  readonly latestMotionSample?: PlayerMotionSampleTraceEvent;
  readonly latestTrace?: RawTraceEvent;
  readonly lastAcceptedAt?: number;
  readonly projections: ProjectionSnapshot;
  readonly detectors: DetectorSnapshot;
  readonly episodes: EpisodeOutput;
}

export type ProjectionState = HubRuntimeSnapshot;

export interface HubRuntime extends HubTraceSink {
  reset(): void;
  snapshot(): HubRuntimeSnapshot;
}

export interface CreateHubRuntimeOptions {
  readonly logger: HubLogger;
}

export function createHubRuntime(options: CreateHubRuntimeOptions): HubRuntime {
  const { logger } = options;
  let traceCount = 0;
  let latestMotionSample: PlayerMotionSampleTraceEvent | undefined;
  let latestTrace: RawTraceEvent | undefined;
  let lastAcceptedAt: number | undefined;
  let projections = createEmptyProjectionSnapshot();
  let detectors = evaluateDetectors(projections);
  let episodeState: EpisodeMachineState = createInitialEpisodeMachineState();

  const reset = () => {
    logger.info("reset runtime snapshot", {
      previousTraceCount: traceCount,
      hadLatestMotionSample: latestMotionSample != null,
      hadLatestTrace: latestTrace != null
    });
    traceCount = 0;
    latestMotionSample = undefined;
    latestTrace = undefined;
    lastAcceptedAt = undefined;
    projections = createEmptyProjectionSnapshot();
    detectors = evaluateDetectors(projections);
    episodeState = createInitialEpisodeMachineState();
  };

  return {
    acceptTrace(event) {
      traceCount += 1;
      latestTrace = event;

      if (event.kind === CURRENT_MOD_TRACE_KIND_PLAYER_MOTION_SAMPLE) {
        latestMotionSample = event;
      }

      projections = reduceProjectionSnapshot(projections, event);
      detectors = evaluateDetectors(projections);
      episodeState = stepEpisodeMachine(episodeState, {
        event,
        projections,
        detectors
      });

      lastAcceptedAt = Date.now();
      logger.debug("accepted trace", {
        kind: event.kind,
        seq: event.seq,
        sessionId: event.sessionId,
        traceCount,
        woodEpisodeState: episodeState.output.woodGathering.state,
        woodSupportScore: detectors.composites.woodGatheringSupport.score
      });
    },
    reset,
    snapshot() {
      return {
        traceCount,
        latestMotionSample,
        latestTrace,
        lastAcceptedAt,
        projections,
        detectors,
        episodes: episodeState.output
      };
    }
  };
}

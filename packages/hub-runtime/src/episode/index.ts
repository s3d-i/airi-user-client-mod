import type { DetectorReason, DetectorSnapshot } from "../detector/index.js";
import type { ProjectionSnapshot } from "../projection/index.js";
import type { RawTraceEvent } from "../trace/index.js";

export const WOOD_GATHERING_EPISODE_KIND = "resource.gathering.wood" as const;

const OPEN_SUSTAIN_MILLIS = 1_200;
const WARMING_GRACE_MILLIS = 1_500;
const ACTIVE_DECAY_GRACE_MILLIS = 4_000;
const ACTIVE_KEEP_ALIVE_SCORE = 0.35;
const RECENT_EPISODE_LIMIT = 6;

export type WoodGatheringEpisodeState = "idle" | "warming" | "active";

export interface EpisodeEvidenceSummary {
  readonly code: string;
  readonly message: string;
  readonly contribution: number;
  readonly traceIds: readonly string[];
}

export interface WoodGatheringEpisodeSnapshot {
  readonly kind: typeof WOOD_GATHERING_EPISODE_KIND;
  readonly state: WoodGatheringEpisodeState;
  readonly active: boolean;
  readonly episodeId?: string;
  readonly supportScore: number;
  readonly supportActive: boolean;
  readonly supportStartedAtMillis?: number;
  readonly openedAtMillis?: number;
  readonly lastSupportedAtMillis?: number;
  readonly lastTransitionAtMillis?: number;
  readonly lastTransitionReason?: string;
  readonly lastClosedAtMillis?: number;
  readonly lastCloseReason?: string;
  readonly evidenceSummary: readonly EpisodeEvidenceSummary[];
}

export interface ClosedWoodGatheringEpisode {
  readonly kind: typeof WOOD_GATHERING_EPISODE_KIND;
  readonly episodeId: string;
  readonly openedAtMillis: number;
  readonly closedAtMillis: number;
  readonly durationMillis: number;
  readonly closeReason: string;
  readonly finalEvidenceSummary: readonly EpisodeEvidenceSummary[];
}

export interface EpisodeOutput {
  readonly woodGathering: WoodGatheringEpisodeSnapshot;
  readonly recent: readonly ClosedWoodGatheringEpisode[];
}

export interface EpisodeMachineState {
  readonly output: EpisodeOutput;
  readonly observedResetCount: number;
  readonly nextEpisodeOrdinal: number;
}

export interface EpisodeMachineInput {
  readonly event: RawTraceEvent;
  readonly projections: ProjectionSnapshot;
  readonly detectors: DetectorSnapshot;
}

export function createInitialEpisodeMachineState(): EpisodeMachineState {
  return {
    output: {
      woodGathering: createIdleWoodGatheringEpisodeSnapshot(),
      recent: []
    },
    observedResetCount: 0,
    nextEpisodeOrdinal: 1
  };
}

export function stepEpisodeMachine(
  previous: EpisodeMachineState,
  input: EpisodeMachineInput
): EpisodeMachineState {
  const support = input.detectors.composites.woodGatheringSupport;
  const hardReset = input.projections.continuity.resetCount !== previous.observedResetCount;
  const now = input.event.capturedAtMillis;

	if (hardReset) {
		return closeOrResetEpisode(
			previous,
			now,
			input.projections.continuity.lastResetReason ?? "reset",
			input.projections.continuity.resetCount
		);
	}

  const current = previous.output.woodGathering;
  const evidenceSummary = summarizeDetectorReasons(support.reasons);

  switch (current.state) {
    case "idle":
      if (!support.active) {
        return {
          ...previous,
          output: {
            ...previous.output,
            woodGathering: {
              ...current,
              supportScore: support.score,
              supportActive: false,
              evidenceSummary
            }
          }
        };
      }

      return {
        ...previous,
        output: {
          ...previous.output,
          woodGathering: {
            kind: WOOD_GATHERING_EPISODE_KIND,
            state: "warming",
            active: false,
            supportScore: support.score,
            supportActive: true,
            supportStartedAtMillis: now,
            lastSupportedAtMillis: now,
            lastTransitionAtMillis: now,
            lastTransitionReason: "support threshold reached",
            evidenceSummary
          }
        }
      };
    case "warming":
      if (!support.active) {
        const lastSupportedAtMillis = current.lastSupportedAtMillis ?? current.supportStartedAtMillis ?? now;

        if (now - lastSupportedAtMillis > WARMING_GRACE_MILLIS) {
          return {
            ...previous,
            output: {
              ...previous.output,
              woodGathering: {
                ...createIdleWoodGatheringEpisodeSnapshot(),
                supportScore: support.score,
                supportActive: false,
                lastTransitionAtMillis: now,
                lastTransitionReason: "support decayed before opening",
                evidenceSummary
              }
            }
          };
        }

        return {
          ...previous,
          output: {
            ...previous.output,
            woodGathering: {
              ...current,
              supportScore: support.score,
              supportActive: false,
              evidenceSummary
            }
          }
        };
      }

      if (now - (current.supportStartedAtMillis ?? now) >= OPEN_SUSTAIN_MILLIS) {
        const episodeId = `${WOOD_GATHERING_EPISODE_KIND}:${previous.nextEpisodeOrdinal}`;

        return {
          ...previous,
          nextEpisodeOrdinal: previous.nextEpisodeOrdinal + 1,
          output: {
            ...previous.output,
            woodGathering: {
              kind: WOOD_GATHERING_EPISODE_KIND,
              state: "active",
              active: true,
              episodeId,
              supportScore: support.score,
              supportActive: true,
              supportStartedAtMillis: current.supportStartedAtMillis ?? now,
              openedAtMillis: now,
              lastSupportedAtMillis: now,
              lastTransitionAtMillis: now,
              lastTransitionReason: "support sustained long enough to open",
              evidenceSummary
            }
          }
        };
      }

      return {
        ...previous,
        output: {
          ...previous.output,
          woodGathering: {
            ...current,
            supportScore: support.score,
            supportActive: true,
            lastSupportedAtMillis: now,
            evidenceSummary
          }
        }
      };
    case "active":
      if (support.active || support.score >= ACTIVE_KEEP_ALIVE_SCORE) {
        return {
          ...previous,
          output: {
            ...previous.output,
            woodGathering: {
              ...current,
              supportScore: support.score,
              supportActive: support.active,
              lastSupportedAtMillis: now,
              evidenceSummary
            }
          }
        };
      }

      if (now - (current.lastSupportedAtMillis ?? current.openedAtMillis ?? now) < ACTIVE_DECAY_GRACE_MILLIS) {
        return {
          ...previous,
          output: {
            ...previous.output,
            woodGathering: {
              ...current,
              supportScore: support.score,
              supportActive: false,
              evidenceSummary
            }
          }
        };
      }

      return closeOrResetEpisode(
        previous,
        now,
        "support decayed after grace window",
        previous.observedResetCount
      );
  }
}

function closeOrResetEpisode(
  previous: EpisodeMachineState,
  closedAtMillis: number,
  reason: string,
  observedResetCount: number
): EpisodeMachineState {
  const current = previous.output.woodGathering;
  const recent = current.state === "active" && current.episodeId != null && current.openedAtMillis != null
    ? [
        {
          kind: WOOD_GATHERING_EPISODE_KIND,
          episodeId: current.episodeId,
          openedAtMillis: current.openedAtMillis,
          closedAtMillis,
          durationMillis: Math.max(0, closedAtMillis - current.openedAtMillis),
          closeReason: reason,
          finalEvidenceSummary: current.evidenceSummary
        },
        ...previous.output.recent
      ].slice(0, RECENT_EPISODE_LIMIT)
    : previous.output.recent;

	return {
		...previous,
		observedResetCount,
		output: {
			woodGathering: {
        ...createIdleWoodGatheringEpisodeSnapshot(),
        lastClosedAtMillis: current.state === "active" ? closedAtMillis : current.lastClosedAtMillis,
        lastCloseReason: reason,
        lastTransitionAtMillis: closedAtMillis,
        lastTransitionReason: reason,
        evidenceSummary: current.evidenceSummary
      },
      recent
    }
  };
}

function createIdleWoodGatheringEpisodeSnapshot(): WoodGatheringEpisodeSnapshot {
  return {
    kind: WOOD_GATHERING_EPISODE_KIND,
    state: "idle",
    active: false,
    supportScore: 0,
    supportActive: false,
    evidenceSummary: []
  };
}

function summarizeDetectorReasons(reasons: readonly DetectorReason[]): readonly EpisodeEvidenceSummary[] {
  return reasons.map(reason => ({
    code: reason.code,
    message: reason.message,
    contribution: reason.contribution,
    traceIds: reason.evidence.map(evidence => evidence.traceId)
  }));
}

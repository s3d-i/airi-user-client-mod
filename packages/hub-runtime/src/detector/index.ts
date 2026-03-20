import { getBlockResourceCategories } from "../classification/index.js";
import type { ProjectionSnapshot } from "../projection/index.js";
import { toTraceEvidenceRef, type TraceEvidenceRef } from "../trace/index.js";

export interface DetectorReason {
  readonly code: string;
  readonly message: string;
  readonly contribution: number;
  readonly evidence: readonly TraceEvidenceRef[];
}

export interface DetectorSignal {
  readonly id: string;
  readonly label: string;
  readonly score: number;
  readonly active: boolean;
  readonly reasons: readonly DetectorReason[];
}

export interface DetectorSnapshot {
  readonly primitives: {
    readonly lookingAtWood: DetectorSignal;
    readonly holdingAxe: DetectorSignal;
    readonly recentWoodBreak: DetectorSignal;
    readonly recentWoodGain: DetectorSignal;
    readonly lowMotionNearTarget: DetectorSignal;
  };
  readonly composites: {
    readonly woodHarvestContext: DetectorSignal;
    readonly woodGatheringSupport: DetectorSignal;
  };
}

export type Detector = (snapshot: ProjectionSnapshot) => DetectorSignal;

const WOOD_GATHERING_THRESHOLD = 0.75;

export function evaluateDetectors(snapshot: ProjectionSnapshot): DetectorSnapshot {
  const primitives = {
    lookingAtWood: lookingAtWood(snapshot),
    holdingAxe: holdingAxe(snapshot),
    recentWoodBreak: recentWoodBreak(snapshot),
    recentWoodGain: recentWoodGain(snapshot),
    lowMotionNearTarget: lowMotionNearTarget(snapshot)
  };
  const woodHarvestContext = allOf("detector.wood_harvest_context", "Wood Harvest Context", [
    primitives.lookingAtWood,
    primitives.holdingAxe
  ]);
  const woodGatheringSupport = threshold(
    weightedSum("detector.wood_gathering_support", "Wood Gathering Support", [
      { signal: woodHarvestContext, weight: 0.25 },
      { signal: primitives.recentWoodBreak, weight: 0.65 },
      { signal: primitives.recentWoodGain, weight: 0.95 },
      { signal: primitives.lowMotionNearTarget, weight: 0.1 }
    ]),
    WOOD_GATHERING_THRESHOLD
  );

  return {
    primitives,
    composites: {
      woodHarvestContext,
      woodGatheringSupport
    }
  };
}

export function lookingAtWood(snapshot: ProjectionSnapshot): DetectorSignal {
  const target = snapshot.focus.currentTarget;
  const isWood = target?.kind === "block" &&
    target.block != null &&
    getBlockResourceCategories(target.block.blockId).includes("wood");

  return {
    id: "detector.looking_at_wood",
    label: "Looking At Wood",
    score: isWood ? 1 : 0,
    active: isWood,
    reasons: !isWood || target?.block == null
      ? []
      : [
          {
            code: "focus.wood_target",
            message: `Current focus is ${target.block.blockId} with ${snapshot.focus.targetDwellMillis}ms dwell`,
            contribution: 1,
            evidence: []
          }
        ]
  };
}

export function holdingAxe(snapshot: ProjectionSnapshot): DetectorSignal {
  const mainHand = snapshot.hand.mainHand;
  const isAxe = snapshot.hand.mainHandToolCategory === "axe";

  return {
    id: "detector.holding_axe",
    label: "Holding Axe",
    score: isAxe ? 1 : 0,
    active: isAxe,
    reasons: !isAxe || mainHand?.itemId == null
      ? []
      : [
          {
            code: "hand.main_hand_axe",
            message: `Main hand is ${mainHand.itemId}`,
            contribution: 1,
            evidence: []
          }
        ]
  };
}

export function recentWoodBreak(snapshot: ProjectionSnapshot): DetectorSignal {
  const recentWoodBreaks = snapshot.interactionWindow.recentBlockBreaks.filter(event =>
    getBlockResourceCategories(event.payload.block.blockId).includes("wood")
  );
  const score = Math.min(recentWoodBreaks.length, 2) / 2;
  const latestBreak = recentWoodBreaks[recentWoodBreaks.length - 1];

  return {
    id: "detector.recent_wood_break",
    label: "Recent Wood Break",
    score,
    active: score > 0,
    reasons: latestBreak == null
      ? []
      : [
          {
            code: "interaction.recent_wood_break",
            message: `${recentWoodBreaks.length} recent wood break event(s) in ${snapshot.interactionWindow.windowMillis}ms`,
            contribution: score,
            evidence: recentWoodBreaks.slice(-2).map(toTraceEvidenceRef)
          }
        ]
  };
}

export function recentWoodGain(snapshot: ProjectionSnapshot): DetectorSignal {
  const gainedWood = snapshot.inventoryDelta.recentGainedItems.filter(item =>
    item.resourceCategories.includes("wood")
  );
  const totalWoodGain = gainedWood.reduce((sum, item) => sum + item.count, 0);
  const score = totalWoodGain > 0 ? Math.min(totalWoodGain, 4) / 4 : 0;
  const latestTransaction = snapshot.inventoryDelta.recentTransactions.at(-1);

  return {
    id: "detector.recent_wood_gain",
    label: "Recent Wood Gain",
    score,
    active: score > 0,
    reasons: latestTransaction == null || totalWoodGain === 0
      ? []
      : [
          {
            code: "inventory.recent_wood_gain",
            message: `${totalWoodGain} wood item(s) gained in ${snapshot.inventoryDelta.windowMillis}ms`,
            contribution: score,
            evidence: snapshot.inventoryDelta.recentTransactions
              .slice(-2)
              .map(toTraceEvidenceRef)
          }
        ]
  };
}

export function lowMotionNearTarget(snapshot: ProjectionSnapshot): DetectorSignal {
  const lookingAtWoodSignal = lookingAtWood(snapshot);
  const isLowMotion = snapshot.motion.movementState === "low_motion";
  const hasDwell = snapshot.focus.targetDwellMillis >= 800;
  const active = lookingAtWoodSignal.active && isLowMotion && hasDwell;

  return {
    id: "detector.low_motion_near_target",
    label: "Low Motion Near Target",
    score: active ? 1 : 0,
    active,
    reasons: !active
      ? []
      : [
          {
            code: "motion.low_motion_near_wood",
            message: `Low motion with ${snapshot.focus.targetDwellMillis}ms focus dwell`,
            contribution: 1,
            evidence: []
          }
        ]
  };
}

export function allOf(id: string, label: string, signals: readonly DetectorSignal[]): DetectorSignal {
  const score = signals.length === 0 ? 0 : Math.min(...signals.map(signal => signal.score));
  return {
    id,
    label,
    score,
    active: signals.length > 0 && signals.every(signal => signal.active),
    reasons: signals.flatMap(signal => signal.reasons)
  };
}

export function anyOf(id: string, label: string, signals: readonly DetectorSignal[]): DetectorSignal {
  const sortedSignals = [...signals].sort((left, right) => right.score - left.score);
  const topSignal = sortedSignals[0];

  return {
    id,
    label,
    score: topSignal?.score ?? 0,
    active: sortedSignals.some(signal => signal.active),
    reasons: topSignal == null ? [] : topSignal.reasons
  };
}

export function not(id: string, label: string, signal: DetectorSignal): DetectorSignal {
  return {
    id,
    label,
    score: 1 - signal.score,
    active: !signal.active,
    reasons: signal.active
      ? []
      : [
          {
            code: "logical.not",
            message: `Inverse of ${signal.label}`,
            contribution: 1 - signal.score,
            evidence: signal.reasons.flatMap(reason => reason.evidence)
          }
        ]
  };
}

export function weightedSum(
  id: string,
  label: string,
  inputs: readonly { signal: DetectorSignal; weight: number }[]
): DetectorSignal {
  const score = Math.min(
    inputs.reduce((sum, input) => sum + input.signal.score * input.weight, 0),
    1
  );

  return {
    id,
    label,
    score,
    active: score > 0,
    reasons: inputs.flatMap(input =>
      input.signal.score <= 0
        ? []
        : input.signal.reasons.map(reason => ({
            ...reason,
            contribution: reason.contribution * input.weight
          }))
    )
  };
}

export function threshold(signal: DetectorSignal, minimumScore: number): DetectorSignal {
  return {
    ...signal,
    active: signal.score >= minimumScore,
    reasons: signal.reasons
  };
}

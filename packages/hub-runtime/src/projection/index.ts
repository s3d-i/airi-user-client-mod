import {
  getBlockResourceCategories,
  getItemResourceCategories,
  getToolCategory,
  type ResourceCategory,
  type ToolCategory
} from "../classification/index.js";
import {
  CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK,
  CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION,
  CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE,
  CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED,
  CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED,
  CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED,
  type InteractionBlockBreakTraceEvent,
  type InventoryTransactionTraceEvent,
  type ObservationSampleTraceEvent,
  type RawTraceEvent,
  type TraceItemStackSnapshot,
  type TraceLookTarget,
  type TraceTargetKind
} from "../trace/index.js";

const INTERACTION_WINDOW_MILLIS = 12_000;
const INVENTORY_WINDOW_MILLIS = 12_000;
const CONTINUITY_INACTIVITY_GAP_MILLIS = 15_000;
const MAX_WINDOW_EVENTS = 32;
const LOW_MOTION_HORIZONTAL_SPEED_THRESHOLD = 0.08;

export type MovementState = "unknown" | "low_motion" | "moving";
export type ContinuityResetReason = "session.changed" | "dimension.changed" | "inactivity.gap";
export type ResourceCategoryCountMap = Partial<Record<ResourceCategory, number>>;

export interface AggregatedItemDelta {
  readonly itemId: string;
  readonly count: number;
  readonly resourceCategories: readonly ResourceCategory[];
}

export interface FocusProjectionSnapshot {
  readonly currentTarget?: TraceLookTarget;
  readonly currentTargetCategory: TraceTargetKind | "unknown";
  readonly targetStartedAtMillis?: number;
  readonly targetDwellMillis: number;
  readonly lastChangedAtMillis?: number;
}

export interface HandProjectionSnapshot {
  readonly selectedSlot?: number;
  readonly mainHand?: TraceItemStackSnapshot;
  readonly offHand?: TraceItemStackSnapshot;
  readonly mainHandToolCategory?: ToolCategory;
  readonly offHandToolCategory?: ToolCategory;
  readonly lastChangedAtMillis?: number;
}

export interface MotionProjectionSnapshot {
  readonly movementState: MovementState;
  readonly speed: number;
  readonly horizontalSpeed: number;
  readonly lowMotionSince?: number;
  readonly lastUpdatedAtMillis?: number;
}

export interface InteractionWindowProjectionSnapshot {
  readonly windowMillis: number;
  readonly recentBlockBreaks: readonly InteractionBlockBreakTraceEvent[];
  readonly recentBreaksByResourceCategory: ResourceCategoryCountMap;
  readonly lastUpdatedAtMillis?: number;
}

export interface InventoryDeltaProjectionSnapshot {
  readonly windowMillis: number;
  readonly recentTransactions: readonly InventoryTransactionTraceEvent[];
  readonly recentGainedItems: readonly AggregatedItemDelta[];
  readonly recentLostItems: readonly AggregatedItemDelta[];
  readonly recentGainsByResourceCategory: ResourceCategoryCountMap;
  readonly recentLossesByResourceCategory: ResourceCategoryCountMap;
  readonly lastUpdatedAtMillis?: number;
}

export interface ContinuityProjectionSnapshot {
  readonly currentSessionId?: string;
  readonly currentDimensionKey?: string;
  readonly lastEventCapturedAtMillis?: number;
  readonly lastResetAtMillis?: number;
  readonly lastResetReason?: ContinuityResetReason;
  readonly resetCount: number;
}

export interface ProjectionSnapshot {
  readonly focus: FocusProjectionSnapshot;
  readonly hand: HandProjectionSnapshot;
  readonly motion: MotionProjectionSnapshot;
  readonly interactionWindow: InteractionWindowProjectionSnapshot;
  readonly inventoryDelta: InventoryDeltaProjectionSnapshot;
  readonly continuity: ContinuityProjectionSnapshot;
}

export function createEmptyProjectionSnapshot(): ProjectionSnapshot {
  return {
    focus: createEmptyFocusProjectionSnapshot(),
    hand: createEmptyHandProjectionSnapshot(),
    motion: createEmptyMotionProjectionSnapshot(),
    interactionWindow: createEmptyInteractionWindowProjectionSnapshot(),
    inventoryDelta: createEmptyInventoryDeltaProjectionSnapshot(),
    continuity: createEmptyContinuityProjectionSnapshot()
  };
}

export function reduceProjectionSnapshot(
  previous: ProjectionSnapshot,
  event: RawTraceEvent
): ProjectionSnapshot {
  const continuity = reduceContinuityProjectionSnapshot(previous.continuity, event);
  const didHardReset = continuity.resetCount !== previous.continuity.resetCount;
  const base = didHardReset
    ? createProjectionSnapshotForReset(continuity)
    : previous;

  return {
    focus: reduceFocusProjectionSnapshot(base.focus, event),
    hand: reduceHandProjectionSnapshot(base.hand, event),
    motion: reduceMotionProjectionSnapshot(base.motion, event),
    interactionWindow: reduceInteractionWindowProjectionSnapshot(base.interactionWindow, event),
    inventoryDelta: reduceInventoryDeltaProjectionSnapshot(base.inventoryDelta, event),
    continuity
  };
}

function createProjectionSnapshotForReset(
  continuity: ContinuityProjectionSnapshot
): ProjectionSnapshot {
  return {
    ...createEmptyProjectionSnapshot(),
    continuity
  };
}

function createEmptyFocusProjectionSnapshot(): FocusProjectionSnapshot {
  return {
    currentTargetCategory: "unknown",
    targetDwellMillis: 0
  };
}

function createEmptyHandProjectionSnapshot(): HandProjectionSnapshot {
  return {};
}

function createEmptyMotionProjectionSnapshot(): MotionProjectionSnapshot {
  return {
    movementState: "unknown",
    speed: 0,
    horizontalSpeed: 0
  };
}

function createEmptyInteractionWindowProjectionSnapshot(): InteractionWindowProjectionSnapshot {
  return {
    windowMillis: INTERACTION_WINDOW_MILLIS,
    recentBlockBreaks: [],
    recentBreaksByResourceCategory: {}
  };
}

function createEmptyInventoryDeltaProjectionSnapshot(): InventoryDeltaProjectionSnapshot {
  return {
    windowMillis: INVENTORY_WINDOW_MILLIS,
    recentTransactions: [],
    recentGainedItems: [],
    recentLostItems: [],
    recentGainsByResourceCategory: {},
    recentLossesByResourceCategory: {}
  };
}

function createEmptyContinuityProjectionSnapshot(): ContinuityProjectionSnapshot {
  return {
    resetCount: 0
  };
}

function reduceContinuityProjectionSnapshot(
  previous: ContinuityProjectionSnapshot,
  event: RawTraceEvent
): ContinuityProjectionSnapshot {
  const dimensionKey = event.payload.dimensionKey;
  let nextResetCount = previous.resetCount;
  let lastResetAtMillis = previous.lastResetAtMillis;
  let lastResetReason = previous.lastResetReason;

  if (
    previous.currentSessionId != null &&
    previous.currentSessionId !== event.sessionId
  ) {
    nextResetCount += 1;
    lastResetAtMillis = event.capturedAtMillis;
    lastResetReason = "session.changed";
  } else if (
    previous.currentDimensionKey != null &&
    previous.currentDimensionKey !== dimensionKey
  ) {
    nextResetCount += 1;
    lastResetAtMillis = event.capturedAtMillis;
    lastResetReason = "dimension.changed";
  } else if (
    previous.lastEventCapturedAtMillis != null &&
    event.capturedAtMillis - previous.lastEventCapturedAtMillis > CONTINUITY_INACTIVITY_GAP_MILLIS
  ) {
    nextResetCount += 1;
    lastResetAtMillis = event.capturedAtMillis;
    lastResetReason = "inactivity.gap";
  }

  return {
    currentSessionId: event.sessionId,
    currentDimensionKey: dimensionKey,
    lastEventCapturedAtMillis: event.capturedAtMillis,
    lastResetAtMillis,
    lastResetReason,
    resetCount: nextResetCount
  };
}

function reduceFocusProjectionSnapshot(
  previous: FocusProjectionSnapshot,
  event: RawTraceEvent
): FocusProjectionSnapshot {
  if (event.kind !== CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED) {
    return {
      ...previous,
      targetDwellMillis:
        previous.targetStartedAtMillis == null
          ? 0
          : Math.max(0, event.capturedAtMillis - previous.targetStartedAtMillis)
    };
  }

  return {
    currentTarget: event.payload.target,
    currentTargetCategory: event.payload.target.kind,
    targetStartedAtMillis: event.capturedAtMillis,
    targetDwellMillis: 0,
    lastChangedAtMillis: event.capturedAtMillis
  };
}

function reduceHandProjectionSnapshot(
  previous: HandProjectionSnapshot,
  event: RawTraceEvent
): HandProjectionSnapshot {
  switch (event.kind) {
    case CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED:
      return applyHandState(
        event.payload.selectedSlot,
        event.payload.mainHand,
        event.payload.offHand,
        event.capturedAtMillis
      );
    case CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED:
      return applyHandState(
        event.payload.selectedSlot,
        event.payload.mainHand,
        event.payload.offHand,
        event.capturedAtMillis
      );
    case CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK:
      return {
        ...previous,
        selectedSlot: event.payload.selectedSlot,
        mainHand: event.payload.heldItem,
        mainHandToolCategory: getToolCategory(event.payload.heldItem.itemId),
        lastChangedAtMillis: event.capturedAtMillis
      };
    default:
      return previous;
  }
}

function applyHandState(
  selectedSlot: number,
  mainHand: TraceItemStackSnapshot,
  offHand: TraceItemStackSnapshot,
  capturedAtMillis: number
): HandProjectionSnapshot {
  return {
    selectedSlot,
    mainHand,
    offHand,
    mainHandToolCategory: getToolCategory(mainHand.itemId),
    offHandToolCategory: getToolCategory(offHand.itemId),
    lastChangedAtMillis: capturedAtMillis
  };
}

function reduceMotionProjectionSnapshot(
  previous: MotionProjectionSnapshot,
  event: RawTraceEvent
): MotionProjectionSnapshot {
  if (event.kind !== CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE) {
    return previous;
  }

  const horizontalSpeed = Math.hypot(event.payload.vx, event.payload.vz);
  const speed = Math.hypot(event.payload.vx, event.payload.vy, event.payload.vz);
  const movementState: MovementState =
    horizontalSpeed <= LOW_MOTION_HORIZONTAL_SPEED_THRESHOLD ? "low_motion" : "moving";
  const lowMotionSince = movementState === "low_motion"
    ? previous.movementState === "low_motion"
      ? previous.lowMotionSince ?? event.capturedAtMillis
      : event.capturedAtMillis
    : undefined;

  return {
    movementState,
    speed,
    horizontalSpeed,
    lowMotionSince,
    lastUpdatedAtMillis: event.capturedAtMillis
  };
}

function reduceInteractionWindowProjectionSnapshot(
  previous: InteractionWindowProjectionSnapshot,
  event: RawTraceEvent
): InteractionWindowProjectionSnapshot {
  const recentBlockBreaks = trimWindow(
    event.kind === CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK
      ? [...previous.recentBlockBreaks, event]
      : previous.recentBlockBreaks,
    event.capturedAtMillis,
    previous.windowMillis
  );

  return {
    windowMillis: previous.windowMillis,
    recentBlockBreaks,
    recentBreaksByResourceCategory: aggregateBreaksByResourceCategory(recentBlockBreaks),
    lastUpdatedAtMillis: event.capturedAtMillis
  };
}

function reduceInventoryDeltaProjectionSnapshot(
  previous: InventoryDeltaProjectionSnapshot,
  event: RawTraceEvent
): InventoryDeltaProjectionSnapshot {
  const recentTransactions = trimWindow(
    event.kind === CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION
      ? [...previous.recentTransactions, event]
      : previous.recentTransactions,
    event.capturedAtMillis,
    previous.windowMillis
  );
  const aggregatedItemDeltas = aggregateInventoryTransactions(recentTransactions);

  return {
    windowMillis: previous.windowMillis,
    recentTransactions,
    recentGainedItems: aggregatedItemDeltas.gainedItems,
    recentLostItems: aggregatedItemDeltas.lostItems,
    recentGainsByResourceCategory: aggregatedItemDeltas.gainsByResourceCategory,
    recentLossesByResourceCategory: aggregatedItemDeltas.lossesByResourceCategory,
    lastUpdatedAtMillis: event.capturedAtMillis
  };
}

function trimWindow<T extends RawTraceEvent>(
  events: readonly T[],
  capturedAtMillis: number,
  windowMillis: number
): readonly T[] {
  const cutoff = capturedAtMillis - windowMillis;
  const trimmed = events.filter(candidate => candidate.capturedAtMillis >= cutoff);
  return trimmed.length <= MAX_WINDOW_EVENTS ? trimmed : trimmed.slice(-MAX_WINDOW_EVENTS);
}

function aggregateBreaksByResourceCategory(
  events: readonly InteractionBlockBreakTraceEvent[]
): ResourceCategoryCountMap {
  const counts: ResourceCategoryCountMap = {};

  for (const event of events) {
    for (const category of getBlockResourceCategories(event.payload.block.blockId)) {
      counts[category] = (counts[category] ?? 0) + 1;
    }
  }

  return counts;
}

function aggregateInventoryTransactions(
  events: readonly InventoryTransactionTraceEvent[]
): {
  readonly gainedItems: readonly AggregatedItemDelta[];
  readonly lostItems: readonly AggregatedItemDelta[];
  readonly gainsByResourceCategory: ResourceCategoryCountMap;
  readonly lossesByResourceCategory: ResourceCategoryCountMap;
} {
  const gainedByItemId = new Map<string, number>();
  const lostByItemId = new Map<string, number>();

  for (const event of events) {
    for (const [itemId, count] of aggregateInventoryTransactionNetByItemId(event)) {
      if (count > 0) {
        gainedByItemId.set(itemId, (gainedByItemId.get(itemId) ?? 0) + count);
      } else if (count < 0) {
        lostByItemId.set(itemId, (lostByItemId.get(itemId) ?? 0) + Math.abs(count));
      }
    }
  }

  const gainedItems = buildAggregatedItemDeltas(gainedByItemId);
  const lostItems = buildAggregatedItemDeltas(lostByItemId);

  return {
    gainedItems: gainedItems.items,
    lostItems: lostItems.items,
    gainsByResourceCategory: gainedItems.countsByResourceCategory,
    lossesByResourceCategory: lostItems.countsByResourceCategory
  };
}

function aggregateInventoryTransactionNetByItemId(
  event: InventoryTransactionTraceEvent
): ReadonlyMap<string, number> {
  const netByItemId = new Map<string, number>();

  for (const changedSlot of event.payload.changedSlots) {
    const previousId = changedSlot.previous.itemId;
    const currentId = changedSlot.current.itemId;

    if (previousId != null) {
      netByItemId.set(previousId, (netByItemId.get(previousId) ?? 0) - changedSlot.previous.count);
    }

    if (currentId != null) {
      netByItemId.set(currentId, (netByItemId.get(currentId) ?? 0) + changedSlot.current.count);
    }
  }

  return netByItemId;
}

function buildAggregatedItemDeltas(
  countsByItemId: ReadonlyMap<string, number>
): {
  readonly items: readonly AggregatedItemDelta[];
  readonly countsByResourceCategory: ResourceCategoryCountMap;
} {
  const items: AggregatedItemDelta[] = [];
  const countsByResourceCategory: ResourceCategoryCountMap = {};

  for (const [itemId, count] of countsByItemId) {
    if (count <= 0) {
      continue;
    }

    const resourceCategories = getItemResourceCategories(itemId);
    items.push({
      itemId,
      count,
      resourceCategories
    });

    for (const category of resourceCategories) {
      countsByResourceCategory[category] = (countsByResourceCategory[category] ?? 0) + count;
    }
  }

  items.sort((left, right) => right.count - left.count || left.itemId.localeCompare(right.itemId));

  return {
    items,
    countsByResourceCategory
  };
}

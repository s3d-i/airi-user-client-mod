import type {
  InteractionBlockBreakSuccessTraceEvent,
  PlayerMotionSampleTraceEvent,
  ProjectionState,
  RawTraceEvent,
  TraceItemStackSnapshot,
  TraceLookTarget
} from "@airi-client-mod/hub-runtime";

export interface DisplayValueRow {
  readonly label: string;
  readonly value: string;
}

export interface StatRow extends DisplayValueRow {
  readonly wide?: boolean;
}

export interface WoodEvidenceView {
  readonly stats: readonly StatRow[];
  readonly highlights: readonly DisplayValueRow[];
}

export function toMotionSampleRows(
  sample: PlayerMotionSampleTraceEvent | undefined
): readonly DisplayValueRow[] | undefined {
  if (sample == null) {
    return undefined;
  }

  return [
    {
      label: "Position",
      value: formatVec3(sample.payload.x, sample.payload.y, sample.payload.z)
    },
    {
      label: "Velocity",
      value: formatVec3(sample.payload.vx, sample.payload.vy, sample.payload.vz)
    },
    {
      label: "Tick",
      value: String(sample.payload.worldTick)
    }
  ];
}

export function toWoodEvidenceView(runtime: ProjectionState): WoodEvidenceView {
  const latestGain = runtime.projections.inventoryDelta.recentGainedItems[0];

  return {
    stats: [
      {
        label: "Focus",
        value: formatTarget(runtime.projections.focus.currentTarget)
      },
      {
        label: "Focus Dwell",
        value: `${runtime.projections.focus.targetDwellMillis} ms`
      },
      {
        label: "Main Hand",
        value: formatItemStack(runtime.projections.hand.mainHand)
      },
      {
        label: "Tool",
        value: runtime.projections.hand.mainHandToolCategory ?? "n/a"
      },
      {
        label: "Motion",
        value: runtime.projections.motion.movementState
      },
      {
        label: "Wood Breaks",
        value: String(runtime.projections.interactionWindow.recentBreaksByResourceCategory.wood ?? 0)
      },
      {
        label: "Wood Gains",
        value: String(runtime.projections.inventoryDelta.recentGainsByResourceCategory.wood ?? 0)
      },
      {
        label: "Continuity Reset",
        value: runtime.projections.continuity.lastResetReason ?? "none"
      }
    ],
    highlights: [
      {
        label: "Latest Gain",
        value: latestGain == null ? "n/a" : `${latestGain.itemId} x${latestGain.count}`
      },
      {
        label: "Latest Break",
        value: describeLatestBlockBreak(runtime.projections.interactionWindow.recentBlockBreaks)
      },
      {
        label: "Detector Score",
        value: runtime.detectors.composites.woodGatheringSupport.score.toFixed(2)
      }
    ]
  };
}

export function describeTraceSummary(event: RawTraceEvent): string {
  if ("payload" in event) {
    return `${event.kind} · ${event.payload.dimensionKey}`;
  }

  return event.kind;
}

export function describeTraceDetail(event: RawTraceEvent): string {
  switch (event.kind) {
    case "trace.session.start":
    case "trace.session.end":
      return `session ${event.sessionId}`;
    case "player.motion.sample":
      return formatVec3(event.payload.x, event.payload.y, event.payload.z);
    case "player.look.target.changed":
      return formatTarget(event.payload.target);
    case "player.selected_slot.changed":
      return `slot ${event.payload.previousSelectedSlot} -> ${event.payload.selectedSlot}`;
    case "player.hand_state.changed":
      return formatItemStack(event.payload.mainHand);
    case "interaction.item.use.attempt":
      return `${formatItemStack(event.payload.heldItem)} via ${event.payload.hand}`;
    case "interaction.block.use.attempt":
    case "interaction.block.attack.attempt":
    case "interaction.block.break.success":
      return `${event.payload.block.blockId} @ ${formatBlockPosition(event.payload.block.position)}`;
    case "interaction.entity.use.attempt":
    case "interaction.entity.attack.attempt":
      return `${formatEntityReference(event.payload.entity)} via ${event.payload.hand}`;
    case "inventory.transaction":
      return `${event.payload.changedSlots.length} slot change(s)`;
  }
}

function describeLatestBlockBreak(recentBlockBreaks: readonly InteractionBlockBreakSuccessTraceEvent[]): string {
  return recentBlockBreaks.at(-1)?.payload.block.blockId ?? "n/a";
}

function formatItemStack(item: TraceItemStackSnapshot | undefined): string {
  if (item == null || item.itemId == null) {
    return "empty";
  }

  return `${item.itemId} x${item.count}`;
}

function formatTarget(target: TraceLookTarget | undefined): string {
  if (target == null) {
    return "n/a";
  }

  switch (target.kind) {
    case "block":
      return `${target.block?.blockId ?? "block"} @ ${formatBlockPosition(target.block?.position)}`;
    case "entity":
      return target.entity?.entityTypeId ?? "entity";
    case "miss":
      return "miss";
    case "none":
      return "none";
  }
}

function formatVec3(x: number, y: number, z: number): string {
  return `${x.toFixed(2)}, ${y.toFixed(2)}, ${z.toFixed(2)}`;
}

function formatBlockPosition(
  position: { readonly x: number; readonly y: number; readonly z: number } | undefined
): string {
  return position == null ? "n/a" : `${position.x}, ${position.y}, ${position.z}`;
}

function formatEntityReference(entity: {
  readonly entityTypeId: string;
  readonly entityId?: number;
}): string {
  return entity.entityId == null ? entity.entityTypeId : `${entity.entityTypeId}#${entity.entityId}`;
}

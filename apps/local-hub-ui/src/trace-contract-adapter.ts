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

  const payload = sample.payload;

  return [
    row("Position", formatVec3(payload.x, payload.y, payload.z)),
    row("Velocity", formatVec3(payload.vx, payload.vy, payload.vz)),
    row("Tick", String(payload.worldTick)),
    row("Health", formatHealth(payload.health, payload.maxHealth, payload.absorption)),
    row("On Ground", formatBoolean(payload.onGround)),
    row("In Water", formatWaterState(payload.touchingWater, payload.submergedInWater))
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
      return `${formatVec3(event.payload.x, event.payload.y, event.payload.z)} · ${formatHealth(event.payload.health, event.payload.maxHealth, event.payload.absorption)} · water ${formatWaterState(event.payload.touchingWater, event.payload.submergedInWater)}`;
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
  const values = [toFiniteNumber(x), toFiniteNumber(y), toFiniteNumber(z)];
  if (values.some(value => value == null)) {
    return "n/a";
  }

  const [xValue, yValue, zValue] = values as [number, number, number];
  return `${xValue.toFixed(2)}, ${yValue.toFixed(2)}, ${zValue.toFixed(2)}`;
}

function formatHealth(health: number, maxHealth: number, absorption: number): string {
  const healthValue = toFiniteNumber(health);
  const maxHealthValue = toFiniteNumber(maxHealth);
  const absorptionValue = toFiniteNumber(absorption) ?? 0;

  if (healthValue == null || maxHealthValue == null) {
    return "n/a";
  }

  const base = `${healthValue.toFixed(1)} / ${maxHealthValue.toFixed(1)}`;
  return absorptionValue > 0 ? `${base} (+${absorptionValue.toFixed(1)})` : base;
}

function formatWaterState(touchingWater: boolean, submergedInWater: boolean): string {
  return submergedInWater === true
    ? "submerged"
    : touchingWater === true
      ? "touching"
      : touchingWater === false && submergedInWater === false
        ? "no"
        : "n/a";
}

function formatBoolean(value: boolean): string {
  return value === true ? "yes" : value === false ? "no" : "n/a";
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

function toFiniteNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function row(label: string, value: string): DisplayValueRow {
  return { label, value };
}

export const CURRENT_MOD_TRACE_VERSION = 1 as const;
export const CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE = "observation.sample" as const;
export const CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED = "player.look.target.changed" as const;
export const CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED =
  "player.selected_slot.changed" as const;
export const CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED = "player.hand_state.changed" as const;
export const CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK = "interaction.block.break" as const;
export const CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION = "inventory.transaction" as const;

export type RawTraceKind =
  | typeof CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE
  | typeof CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED
  | typeof CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED
  | typeof CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED
  | typeof CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK
  | typeof CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION;

export type TraceBlockFace = "up" | "down" | "north" | "south" | "east" | "west";
export type TraceTargetKind = "none" | "miss" | "block" | "entity";
export type TraceHandType = "main_hand" | "off_hand";

export interface TraceBlockPosition {
  readonly x: number;
  readonly y: number;
  readonly z: number;
}

export interface TraceItemStackSnapshot {
  readonly itemId: string | null;
  readonly count: number;
  readonly damage: number;
  readonly maxDamage: number;
}

export interface TraceLookTargetBlockDetails {
  readonly blockId: string;
  readonly position: TraceBlockPosition;
  readonly hitFace?: TraceBlockFace;
}

export interface TraceLookTargetEntityDetails {
  readonly entityTypeId: string;
  readonly entityId?: number;
}

export interface TraceLookTarget {
  readonly kind: TraceTargetKind;
  readonly targetDescription?: string;
  readonly block?: TraceLookTargetBlockDetails;
  readonly entity?: TraceLookTargetEntityDetails;
}

export interface TraceBlockReference {
  readonly blockId: string;
  readonly position: TraceBlockPosition;
  readonly hitFace?: TraceBlockFace;
}

export interface TraceInventorySlotDelta {
  readonly slot: number;
  readonly previous: TraceItemStackSnapshot;
  readonly current: TraceItemStackSnapshot;
}

export interface TraceEvidenceRef {
  readonly traceId: string;
  readonly sessionId: string;
  readonly seq: number;
  readonly kind: RawTraceKind;
  readonly capturedAtMillis: number;
}

/**
 * Initial/current-state raw trace contract for local hub ingress.
 *
 * This mirrors the Java mod's current emit shape exactly. Do not add or rename
 * fields here unless the Java-side websocket payload changes as well.
 */
export interface CurrentModObservationSampleTracePayload {
  readonly worldTick: number;
  readonly fps: number;
  readonly dimensionKey: string;
  readonly x: number;
  readonly y: number;
  readonly z: number;
  readonly vx: number;
  readonly vy: number;
  readonly vz: number;
  readonly targetDescription: string;
}

/**
 * Initial/current-state observation sample emitted by the Java mod.
 */
export interface CurrentModObservationSampleTraceEvent {
  readonly v: typeof CURRENT_MOD_TRACE_VERSION;
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE;
  readonly sessionId: string;
  readonly seq: number;
  readonly capturedAtMillis: number;
  readonly payload: CurrentModObservationSampleTracePayload;
}

export interface PlayerLookTargetChangedTracePayload {
  readonly worldTick: number;
  readonly dimensionKey: string;
  readonly target: TraceLookTarget;
}

export interface PlayerLookTargetChangedTraceEvent {
  readonly v: typeof CURRENT_MOD_TRACE_VERSION;
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED;
  readonly sessionId: string;
  readonly seq: number;
  readonly capturedAtMillis: number;
  readonly payload: PlayerLookTargetChangedTracePayload;
}

export interface PlayerSelectedSlotChangedTracePayload {
  readonly worldTick: number;
  readonly dimensionKey: string;
  readonly previousSelectedSlot: number;
  readonly selectedSlot: number;
  readonly mainHand: TraceItemStackSnapshot;
  readonly offHand: TraceItemStackSnapshot;
}

export interface PlayerSelectedSlotChangedTraceEvent {
  readonly v: typeof CURRENT_MOD_TRACE_VERSION;
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED;
  readonly sessionId: string;
  readonly seq: number;
  readonly capturedAtMillis: number;
  readonly payload: PlayerSelectedSlotChangedTracePayload;
}

export interface PlayerHandStateChangedTracePayload {
  readonly worldTick: number;
  readonly dimensionKey: string;
  readonly selectedSlot: number;
  readonly mainHand: TraceItemStackSnapshot;
  readonly offHand: TraceItemStackSnapshot;
}

export interface PlayerHandStateChangedTraceEvent {
  readonly v: typeof CURRENT_MOD_TRACE_VERSION;
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED;
  readonly sessionId: string;
  readonly seq: number;
  readonly capturedAtMillis: number;
  readonly payload: PlayerHandStateChangedTracePayload;
}

export interface InteractionBlockBreakTracePayload {
  readonly worldTick: number;
  readonly dimensionKey: string;
  readonly block: TraceBlockReference;
  readonly hand: TraceHandType;
  readonly selectedSlot: number;
  readonly heldItem: TraceItemStackSnapshot;
}

export interface InteractionBlockBreakTraceEvent {
  readonly v: typeof CURRENT_MOD_TRACE_VERSION;
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK;
  readonly sessionId: string;
  readonly seq: number;
  readonly capturedAtMillis: number;
  readonly payload: InteractionBlockBreakTracePayload;
}

export interface InventoryTransactionTracePayload {
  readonly worldTick: number;
  readonly dimensionKey: string;
  readonly containerKind: string;
  readonly source: string;
  readonly changedSlots: readonly TraceInventorySlotDelta[];
}

export interface InventoryTransactionTraceEvent {
  readonly v: typeof CURRENT_MOD_TRACE_VERSION;
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION;
  readonly sessionId: string;
  readonly seq: number;
  readonly capturedAtMillis: number;
  readonly payload: InventoryTransactionTracePayload;
}

export type CurrentModTraceEvent =
  | CurrentModObservationSampleTraceEvent
  | PlayerLookTargetChangedTraceEvent
  | PlayerSelectedSlotChangedTraceEvent
  | PlayerHandStateChangedTraceEvent
  | InteractionBlockBreakTraceEvent
  | InventoryTransactionTraceEvent;
export type ObservationSampleTracePayload = CurrentModObservationSampleTracePayload;
export type ObservationSampleTraceEvent = CurrentModObservationSampleTraceEvent;
export type RawTraceEvent = CurrentModTraceEvent;

export interface RawTraceDecodeSuccess {
  readonly ok: true;
  readonly event: RawTraceEvent;
}

export interface RawTraceDecodeFailure {
  readonly ok: false;
  readonly reason: string;
}

export type RawTraceDecodeResult = RawTraceDecodeSuccess | RawTraceDecodeFailure;

export function decodeCurrentModTraceEvent(value: unknown): RawTraceDecodeResult {
  if (!isRecord(value)) {
    return { ok: false, reason: "frame must decode to an object" };
  }

  if (value.v !== CURRENT_MOD_TRACE_VERSION) {
    return { ok: false, reason: "unsupported trace version" };
  }

  const header = decodeTraceHeader(value);

  if (!header.ok) {
    return header;
  }

  switch (value.kind) {
    case CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE:
      return decodeObservationSampleTraceEvent(header.value);
    case CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED:
      return decodePlayerLookTargetChangedTraceEvent(header.value);
    case CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED:
      return decodePlayerSelectedSlotChangedTraceEvent(header.value);
    case CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED:
      return decodePlayerHandStateChangedTraceEvent(header.value);
    case CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK:
      return decodeInteractionBlockBreakTraceEvent(header.value);
    case CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION:
      return decodeInventoryTransactionTraceEvent(header.value);
    default:
      return { ok: false, reason: "unsupported trace kind" };
  }
}

export function createRawTraceId(event: Pick<RawTraceEvent, "sessionId" | "seq">): string {
  return `${event.sessionId}:${event.seq}`;
}

export function toTraceEvidenceRef(event: RawTraceEvent): TraceEvidenceRef {
  return {
    traceId: createRawTraceId(event),
    sessionId: event.sessionId,
    seq: event.seq,
    kind: event.kind,
    capturedAtMillis: event.capturedAtMillis
  };
}

interface DecodeValueSuccess<T> {
  readonly ok: true;
  readonly value: T;
}

interface DecodeValueFailure {
  readonly ok: false;
  readonly reason: string;
}

type DecodeValueResult<T> = DecodeValueSuccess<T> | DecodeValueFailure;

interface TraceHeader {
  readonly sessionId: string;
  readonly seq: number;
  readonly capturedAtMillis: number;
  readonly kind: RawTraceKind;
  readonly payload: Record<string, unknown>;
}

interface CommonTraceContext {
  readonly worldTick: number;
  readonly dimensionKey: string;
}

function decodeTraceHeader(value: Record<string, unknown>): DecodeValueResult<TraceHeader> {
  if (!isSupportedTraceKind(value.kind)) {
    return { ok: false, reason: "unsupported trace kind" };
  }

  if (typeof value.sessionId !== "string" || value.sessionId.length === 0) {
    return { ok: false, reason: "sessionId must be a non-empty string" };
  }

  if (!isIntegerNumber(value.seq)) {
    return { ok: false, reason: "seq must be an integer number" };
  }

  if (!isIntegerNumber(value.capturedAtMillis)) {
    return { ok: false, reason: "capturedAtMillis must be an integer number" };
  }

  if (!isRecord(value.payload)) {
    return { ok: false, reason: "payload must be an object" };
  }

  return {
    ok: true,
    value: {
      sessionId: value.sessionId,
      seq: value.seq,
      capturedAtMillis: value.capturedAtMillis,
      kind: value.kind,
      payload: value.payload
    }
  };
}

function decodeObservationSampleTraceEvent(
  header: TraceHeader
): RawTraceDecodeResult {
  const payload = header.payload;
  const context = decodeCommonTraceContext(payload);

  if (!context.ok) {
    return context;
  }

  if (!isIntegerNumber(payload.fps)) {
    return { ok: false, reason: "payload.fps must be an integer number" };
  }

  if (!isFiniteNumber(payload.x) || !isFiniteNumber(payload.y) || !isFiniteNumber(payload.z)) {
    return { ok: false, reason: "payload position values must be finite numbers" };
  }

  if (!isFiniteNumber(payload.vx) || !isFiniteNumber(payload.vy) || !isFiniteNumber(payload.vz)) {
    return { ok: false, reason: "payload velocity values must be finite numbers" };
  }

  if (typeof payload.targetDescription !== "string") {
    return { ok: false, reason: "payload.targetDescription must be a string" };
  }

  return {
    ok: true,
    event: {
      v: CURRENT_MOD_TRACE_VERSION,
      kind: CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE,
      sessionId: header.sessionId,
      seq: header.seq,
      capturedAtMillis: header.capturedAtMillis,
      payload: {
        worldTick: context.value.worldTick,
        fps: payload.fps,
        dimensionKey: context.value.dimensionKey,
        x: payload.x,
        y: payload.y,
        z: payload.z,
        vx: payload.vx,
        vy: payload.vy,
        vz: payload.vz,
        targetDescription: payload.targetDescription
      }
    }
  };
}

function decodePlayerLookTargetChangedTraceEvent(
  header: TraceHeader
): RawTraceDecodeResult {
  const payload = header.payload;
  const context = decodeCommonTraceContext(payload);

  if (!context.ok) {
    return context;
  }

  const target = decodeLookTarget(payload.target, "payload.target");

  if (!target.ok) {
    return target;
  }

  return {
    ok: true,
    event: {
      v: CURRENT_MOD_TRACE_VERSION,
      kind: CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED,
      sessionId: header.sessionId,
      seq: header.seq,
      capturedAtMillis: header.capturedAtMillis,
      payload: {
        worldTick: context.value.worldTick,
        dimensionKey: context.value.dimensionKey,
        target: target.value
      }
    }
  };
}

function decodePlayerSelectedSlotChangedTraceEvent(
  header: TraceHeader
): RawTraceDecodeResult {
  const payload = header.payload;
  const context = decodeCommonTraceContext(payload);

  if (!context.ok) {
    return context;
  }

  if (!isIntegerNumber(payload.previousSelectedSlot)) {
    return { ok: false, reason: "payload.previousSelectedSlot must be an integer number" };
  }

  if (!isIntegerNumber(payload.selectedSlot)) {
    return { ok: false, reason: "payload.selectedSlot must be an integer number" };
  }

  const mainHand = decodeItemStackSnapshot(payload.mainHand, "payload.mainHand");

  if (!mainHand.ok) {
    return mainHand;
  }

  const offHand = decodeItemStackSnapshot(payload.offHand, "payload.offHand");

  if (!offHand.ok) {
    return offHand;
  }

  return {
    ok: true,
    event: {
      v: CURRENT_MOD_TRACE_VERSION,
      kind: CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED,
      sessionId: header.sessionId,
      seq: header.seq,
      capturedAtMillis: header.capturedAtMillis,
      payload: {
        worldTick: context.value.worldTick,
        dimensionKey: context.value.dimensionKey,
        previousSelectedSlot: payload.previousSelectedSlot,
        selectedSlot: payload.selectedSlot,
        mainHand: mainHand.value,
        offHand: offHand.value
      }
    }
  };
}

function decodePlayerHandStateChangedTraceEvent(
  header: TraceHeader
): RawTraceDecodeResult {
  const payload = header.payload;
  const context = decodeCommonTraceContext(payload);

  if (!context.ok) {
    return context;
  }

  if (!isIntegerNumber(payload.selectedSlot)) {
    return { ok: false, reason: "payload.selectedSlot must be an integer number" };
  }

  const mainHand = decodeItemStackSnapshot(payload.mainHand, "payload.mainHand");

  if (!mainHand.ok) {
    return mainHand;
  }

  const offHand = decodeItemStackSnapshot(payload.offHand, "payload.offHand");

  if (!offHand.ok) {
    return offHand;
  }

  return {
    ok: true,
    event: {
      v: CURRENT_MOD_TRACE_VERSION,
      kind: CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED,
      sessionId: header.sessionId,
      seq: header.seq,
      capturedAtMillis: header.capturedAtMillis,
      payload: {
        worldTick: context.value.worldTick,
        dimensionKey: context.value.dimensionKey,
        selectedSlot: payload.selectedSlot,
        mainHand: mainHand.value,
        offHand: offHand.value
      }
    }
  };
}

function decodeInteractionBlockBreakTraceEvent(
  header: TraceHeader
): RawTraceDecodeResult {
  const payload = header.payload;
  const context = decodeCommonTraceContext(payload);

  if (!context.ok) {
    return context;
  }

  const block = decodeBlockReference(payload.block, "payload.block");

  if (!block.ok) {
    return block;
  }

  if (!isTraceHandType(payload.hand)) {
    return { ok: false, reason: "payload.hand must be a supported hand type" };
  }

  if (!isIntegerNumber(payload.selectedSlot)) {
    return { ok: false, reason: "payload.selectedSlot must be an integer number" };
  }

  const heldItem = decodeItemStackSnapshot(payload.heldItem, "payload.heldItem");

  if (!heldItem.ok) {
    return heldItem;
  }

  return {
    ok: true,
    event: {
      v: CURRENT_MOD_TRACE_VERSION,
      kind: CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK,
      sessionId: header.sessionId,
      seq: header.seq,
      capturedAtMillis: header.capturedAtMillis,
      payload: {
        worldTick: context.value.worldTick,
        dimensionKey: context.value.dimensionKey,
        block: block.value,
        hand: payload.hand,
        selectedSlot: payload.selectedSlot,
        heldItem: heldItem.value
      }
    }
  };
}

function decodeInventoryTransactionTraceEvent(
  header: TraceHeader
): RawTraceDecodeResult {
  const payload = header.payload;
  const context = decodeCommonTraceContext(payload);

  if (!context.ok) {
    return context;
  }

  if (typeof payload.containerKind !== "string" || payload.containerKind.length === 0) {
    return { ok: false, reason: "payload.containerKind must be a non-empty string" };
  }

  if (typeof payload.source !== "string" || payload.source.length === 0) {
    return { ok: false, reason: "payload.source must be a non-empty string" };
  }

  if (!Array.isArray(payload.changedSlots)) {
    return { ok: false, reason: "payload.changedSlots must be an array" };
  }

  const changedSlots: TraceInventorySlotDelta[] = [];

  for (let index = 0; index < payload.changedSlots.length; index++) {
    const changedSlot = decodeInventorySlotDelta(
      payload.changedSlots[index],
      `payload.changedSlots[${index}]`
    );

    if (!changedSlot.ok) {
      return changedSlot;
    }

    changedSlots.push(changedSlot.value);
  }

  return {
    ok: true,
    event: {
      v: CURRENT_MOD_TRACE_VERSION,
      kind: CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION,
      sessionId: header.sessionId,
      seq: header.seq,
      capturedAtMillis: header.capturedAtMillis,
      payload: {
        worldTick: context.value.worldTick,
        dimensionKey: context.value.dimensionKey,
        containerKind: payload.containerKind,
        source: payload.source,
        changedSlots
      }
    }
  };
}

function decodeCommonTraceContext(payload: Record<string, unknown>): DecodeValueResult<CommonTraceContext> {
  if (!isIntegerNumber(payload.worldTick)) {
    return { ok: false, reason: "payload.worldTick must be an integer number" };
  }

  if (typeof payload.dimensionKey !== "string" || payload.dimensionKey.length === 0) {
    return { ok: false, reason: "payload.dimensionKey must be a non-empty string" };
  }

  return {
    ok: true,
    value: {
      worldTick: payload.worldTick,
      dimensionKey: payload.dimensionKey
    }
  };
}

function decodeLookTarget(value: unknown, path: string): DecodeValueResult<TraceLookTarget> {
  if (!isRecord(value)) {
    return { ok: false, reason: `${path} must be an object` };
  }

  if (!isTraceTargetKind(value.kind)) {
    return { ok: false, reason: `${path}.kind must be a supported target kind` };
  }

  if (value.targetDescription != null && typeof value.targetDescription !== "string") {
    return { ok: false, reason: `${path}.targetDescription must be a string when present` };
  }

  const target: TraceLookTarget = {
    kind: value.kind,
    ...(typeof value.targetDescription === "string" ? { targetDescription: value.targetDescription } : {})
  };

  if (value.kind === "block") {
    const block = decodeBlockReference(value.block, `${path}.block`);

    if (!block.ok) {
      return block;
    }

    return {
      ok: true,
      value: {
        ...target,
        block: block.value
      }
    };
  }

  if (value.kind === "entity") {
    const entity = decodeLookTargetEntity(value.entity, `${path}.entity`);

    if (!entity.ok) {
      return entity;
    }

    return {
      ok: true,
      value: {
        ...target,
        entity: entity.value
      }
    };
  }

  return {
    ok: true,
    value: target
  };
}

function decodeLookTargetEntity(
  value: unknown,
  path: string
): DecodeValueResult<TraceLookTargetEntityDetails> {
  if (!isRecord(value)) {
    return { ok: false, reason: `${path} must be an object` };
  }

  if (typeof value.entityTypeId !== "string" || value.entityTypeId.length === 0) {
    return { ok: false, reason: `${path}.entityTypeId must be a non-empty string` };
  }

  if (value.entityId != null && !isIntegerNumber(value.entityId)) {
    return { ok: false, reason: `${path}.entityId must be an integer number when present` };
  }

  return {
    ok: true,
    value: {
      entityTypeId: value.entityTypeId,
      ...(isIntegerNumber(value.entityId) ? { entityId: value.entityId } : {})
    }
  };
}

function decodeBlockReference(value: unknown, path: string): DecodeValueResult<TraceBlockReference> {
  if (!isRecord(value)) {
    return { ok: false, reason: `${path} must be an object` };
  }

  if (typeof value.blockId !== "string" || value.blockId.length === 0) {
    return { ok: false, reason: `${path}.blockId must be a non-empty string` };
  }

  const position = decodeBlockPosition(value.position, `${path}.position`);

  if (!position.ok) {
    return position;
  }

  if (value.hitFace != null && !isTraceBlockFace(value.hitFace)) {
    return { ok: false, reason: `${path}.hitFace must be a supported block face when present` };
  }

  return {
    ok: true,
    value: {
      blockId: value.blockId,
      position: position.value,
      ...(isTraceBlockFace(value.hitFace) ? { hitFace: value.hitFace } : {})
    }
  };
}

function decodeBlockPosition(value: unknown, path: string): DecodeValueResult<TraceBlockPosition> {
  if (!isRecord(value)) {
    return { ok: false, reason: `${path} must be an object` };
  }

  if (!isIntegerNumber(value.x) || !isIntegerNumber(value.y) || !isIntegerNumber(value.z)) {
    return { ok: false, reason: `${path} coordinates must be integer numbers` };
  }

  return {
    ok: true,
    value: {
      x: value.x,
      y: value.y,
      z: value.z
    }
  };
}

function decodeItemStackSnapshot(
  value: unknown,
  path: string
): DecodeValueResult<TraceItemStackSnapshot> {
  if (!isRecord(value)) {
    return { ok: false, reason: `${path} must be an object` };
  }

  if (value.itemId != null && (typeof value.itemId !== "string" || value.itemId.length === 0)) {
    return { ok: false, reason: `${path}.itemId must be a non-empty string or null` };
  }

  if (!isNonNegativeInteger(value.count)) {
    return { ok: false, reason: `${path}.count must be a non-negative integer` };
  }

  if (!isNonNegativeInteger(value.damage)) {
    return { ok: false, reason: `${path}.damage must be a non-negative integer` };
  }

  if (!isNonNegativeInteger(value.maxDamage)) {
    return { ok: false, reason: `${path}.maxDamage must be a non-negative integer` };
  }

  return {
    ok: true,
    value: {
      itemId: value.itemId ?? null,
      count: value.count,
      damage: value.damage,
      maxDamage: value.maxDamage
    }
  };
}

function decodeInventorySlotDelta(
  value: unknown,
  path: string
): DecodeValueResult<TraceInventorySlotDelta> {
  if (!isRecord(value)) {
    return { ok: false, reason: `${path} must be an object` };
  }

  if (!isIntegerNumber(value.slot)) {
    return { ok: false, reason: `${path}.slot must be an integer number` };
  }

  const previous = decodeItemStackSnapshot(value.previous, `${path}.previous`);

  if (!previous.ok) {
    return previous;
  }

  const current = decodeItemStackSnapshot(value.current, `${path}.current`);

  if (!current.ok) {
    return current;
  }

  return {
    ok: true,
    value: {
      slot: value.slot,
      previous: previous.value,
      current: current.value
    }
  };
}

function isSupportedTraceKind(value: unknown): value is RawTraceKind {
  return (
    value === CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE ||
    value === CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED ||
    value === CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED ||
    value === CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED ||
    value === CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK ||
    value === CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION
  );
}

function isTraceBlockFace(value: unknown): value is TraceBlockFace {
  return (
    value === "up" ||
    value === "down" ||
    value === "north" ||
    value === "south" ||
    value === "east" ||
    value === "west"
  );
}

function isTraceTargetKind(value: unknown): value is TraceTargetKind {
  return value === "none" || value === "miss" || value === "block" || value === "entity";
}

function isTraceHandType(value: unknown): value is TraceHandType {
  return value === "main_hand" || value === "off_hand";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function isIntegerNumber(value: unknown): value is number {
  return isFiniteNumber(value) && Number.isInteger(value);
}

function isNonNegativeInteger(value: unknown): value is number {
  return isIntegerNumber(value) && value >= 0;
}

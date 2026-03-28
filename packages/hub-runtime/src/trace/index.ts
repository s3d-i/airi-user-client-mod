export const CURRENT_MOD_TRACE_VERSION = 1 as const;
export const CURRENT_MOD_TRACE_KIND_TRACE_SESSION_START = "trace.session.start" as const;
export const CURRENT_MOD_TRACE_KIND_TRACE_SESSION_END = "trace.session.end" as const;
export const CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE = "observation.sample" as const;
export const CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED = "player.look.target.changed" as const;
export const CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED =
  "player.selected_slot.changed" as const;
export const CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED = "player.hand_state.changed" as const;
export const CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_ATTACK_ATTEMPT =
  "interaction.block.attack.attempt" as const;
export const CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK_SUCCESS =
  "interaction.block.break.success" as const;
export const CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION = "inventory.transaction" as const;

export type RawTraceKind =
  | typeof CURRENT_MOD_TRACE_KIND_TRACE_SESSION_START
  | typeof CURRENT_MOD_TRACE_KIND_TRACE_SESSION_END
  | typeof CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE
  | typeof CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED
  | typeof CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED
  | typeof CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED
  | typeof CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_ATTACK_ATTEMPT
  | typeof CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK_SUCCESS
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

interface CurrentModWireTraceEventBase {
  readonly v: typeof CURRENT_MOD_TRACE_VERSION;
  readonly kind: RawTraceKind;
  readonly sessionId: string;
  readonly seq: number;
  readonly capturedAtMillis: number;
}

export interface CurrentModSessionStartTraceEvent extends CurrentModWireTraceEventBase {
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_TRACE_SESSION_START;
}

export interface CurrentModSessionEndTraceEvent extends CurrentModWireTraceEventBase {
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_TRACE_SESSION_END;
}

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

export interface CurrentModObservationSampleTraceEvent extends CurrentModWireTraceEventBase {
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE;
  readonly payload: CurrentModObservationSampleTracePayload;
}

export interface PlayerLookTargetChangedTracePayload {
  readonly worldTick: number;
  readonly dimensionKey: string;
  readonly target: TraceLookTarget;
}

export interface PlayerLookTargetChangedTraceEvent extends CurrentModWireTraceEventBase {
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED;
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

export interface PlayerSelectedSlotChangedTraceEvent extends CurrentModWireTraceEventBase {
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED;
  readonly payload: PlayerSelectedSlotChangedTracePayload;
}

export interface PlayerHandStateChangedTracePayload {
  readonly worldTick: number;
  readonly dimensionKey: string;
  readonly selectedSlot: number;
  readonly mainHand: TraceItemStackSnapshot;
  readonly offHand: TraceItemStackSnapshot;
}

export interface PlayerHandStateChangedTraceEvent extends CurrentModWireTraceEventBase {
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED;
  readonly payload: PlayerHandStateChangedTracePayload;
}

export interface InteractionBlockTracePayload {
  readonly worldTick: number;
  readonly dimensionKey: string;
  readonly block: TraceBlockReference;
  readonly hand: TraceHandType;
  readonly selectedSlot: number;
  readonly heldItem: TraceItemStackSnapshot;
}

export interface InteractionBlockAttackAttemptTraceEvent extends CurrentModWireTraceEventBase {
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_ATTACK_ATTEMPT;
  readonly payload: InteractionBlockTracePayload;
}

export interface InteractionBlockBreakSuccessTraceEvent extends CurrentModWireTraceEventBase {
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK_SUCCESS;
  readonly payload: InteractionBlockTracePayload;
}

export interface InventoryTransactionTracePayload {
  readonly worldTick: number;
  readonly dimensionKey: string;
  readonly containerKind: string;
  readonly source: string;
  readonly changedSlots: readonly TraceInventorySlotDelta[];
}

export interface InventoryTransactionTraceEvent extends CurrentModWireTraceEventBase {
  readonly kind: typeof CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION;
  readonly payload: InventoryTransactionTracePayload;
}

export type CurrentModWireTraceEvent =
  | CurrentModSessionStartTraceEvent
  | CurrentModSessionEndTraceEvent
  | CurrentModObservationSampleTraceEvent
  | PlayerLookTargetChangedTraceEvent
  | PlayerSelectedSlotChangedTraceEvent
  | PlayerHandStateChangedTraceEvent
  | InteractionBlockAttackAttemptTraceEvent
  | InteractionBlockBreakSuccessTraceEvent
  | InventoryTransactionTraceEvent;

export type CurrentModTraceEvent = CurrentModWireTraceEvent;

export type SessionStartTraceEvent = CurrentModSessionStartTraceEvent;
export type SessionEndTraceEvent = CurrentModSessionEndTraceEvent;
export type ObservationSampleTracePayload = CurrentModObservationSampleTracePayload;
export type ObservationSampleTraceEvent = CurrentModObservationSampleTraceEvent;

export type CanonicalTraceEvent =
  | SessionStartTraceEvent
  | SessionEndTraceEvent
  | ObservationSampleTraceEvent
  | PlayerLookTargetChangedTraceEvent
  | PlayerSelectedSlotChangedTraceEvent
  | PlayerHandStateChangedTraceEvent
  | InteractionBlockAttackAttemptTraceEvent
  | InteractionBlockBreakSuccessTraceEvent
  | InventoryTransactionTraceEvent;

export type RawTraceEvent = CanonicalTraceEvent;

type NonSessionTraceKind = Exclude<RawTraceKind, "trace.session.start" | "trace.session.end">;

interface ParsedTraceFrame {
  readonly kind: RawTraceKind;
  readonly sessionId: string;
  readonly seq: number;
  readonly capturedAtMillis: number;
  readonly payload?: unknown;
}

interface CommonTraceContext {
  readonly worldTick: number;
  readonly dimensionKey: string;
}

export function decodeCurrentModTraceEvent(input: unknown): CanonicalTraceEvent {
  const frame = parseTraceFrame(input);

  if (frame.kind === CURRENT_MOD_TRACE_KIND_TRACE_SESSION_START) {
    return {
      v: CURRENT_MOD_TRACE_VERSION,
      kind: CURRENT_MOD_TRACE_KIND_TRACE_SESSION_START,
      sessionId: frame.sessionId,
      seq: frame.seq,
      capturedAtMillis: frame.capturedAtMillis
    };
  }

  if (frame.kind === CURRENT_MOD_TRACE_KIND_TRACE_SESSION_END) {
    return {
      v: CURRENT_MOD_TRACE_VERSION,
      kind: CURRENT_MOD_TRACE_KIND_TRACE_SESSION_END,
      sessionId: frame.sessionId,
      seq: frame.seq,
      capturedAtMillis: frame.capturedAtMillis
    };
  }

  const payload = parsePayload(frame.payload);
  const base = {
    v: CURRENT_MOD_TRACE_VERSION,
    sessionId: frame.sessionId,
    seq: frame.seq,
    capturedAtMillis: frame.capturedAtMillis
  };

  return decodePayloadTraceEvent(frame.kind, payload, base);
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

function decodePayloadTraceEvent(
  kind: NonSessionTraceKind,
  payload: Record<string, unknown>,
  base: {
    readonly v: typeof CURRENT_MOD_TRACE_VERSION;
    readonly sessionId: string;
    readonly seq: number;
    readonly capturedAtMillis: number;
  }
): CanonicalTraceEvent {
  const context = parseCommonTraceContext(payload);

  switch (kind) {
    case CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE:
      return {
        ...base,
        kind,
        payload: {
          worldTick: context.worldTick,
          fps: parseIntegerNumber(payload.fps, "payload.fps must be an integer number"),
          dimensionKey: context.dimensionKey,
          x: parseFiniteNumber(payload.x, "payload position values must be finite numbers"),
          y: parseFiniteNumber(payload.y, "payload position values must be finite numbers"),
          z: parseFiniteNumber(payload.z, "payload position values must be finite numbers"),
          vx: parseFiniteNumber(payload.vx, "payload velocity values must be finite numbers"),
          vy: parseFiniteNumber(payload.vy, "payload velocity values must be finite numbers"),
          vz: parseFiniteNumber(payload.vz, "payload velocity values must be finite numbers"),
          targetDescription: parseString(
            payload.targetDescription,
            "payload.targetDescription must be a string"
          )
        }
      };

    case CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED:
      return {
        ...base,
        kind,
        payload: {
          worldTick: context.worldTick,
          dimensionKey: context.dimensionKey,
          target: parseLookTarget(payload.target, "payload.target")
        }
      };

    case CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED:
      return {
        ...base,
        kind,
        payload: {
          worldTick: context.worldTick,
          dimensionKey: context.dimensionKey,
          previousSelectedSlot: parseIntegerNumber(
            payload.previousSelectedSlot,
            "payload.previousSelectedSlot must be an integer number"
          ),
          selectedSlot: parseIntegerNumber(
            payload.selectedSlot,
            "payload.selectedSlot must be an integer number"
          ),
          mainHand: parseItemStackSnapshot(payload.mainHand, "payload.mainHand"),
          offHand: parseItemStackSnapshot(payload.offHand, "payload.offHand")
        }
      };

    case CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED:
      return {
        ...base,
        kind,
        payload: {
          worldTick: context.worldTick,
          dimensionKey: context.dimensionKey,
          selectedSlot: parseIntegerNumber(
            payload.selectedSlot,
            "payload.selectedSlot must be an integer number"
          ),
          mainHand: parseItemStackSnapshot(payload.mainHand, "payload.mainHand"),
          offHand: parseItemStackSnapshot(payload.offHand, "payload.offHand")
        }
      };

    case CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_ATTACK_ATTEMPT:
    case CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK_SUCCESS:
      return {
        ...base,
        kind,
        payload: {
          worldTick: context.worldTick,
          dimensionKey: context.dimensionKey,
          block: parseBlockReference(payload.block, "payload.block"),
          hand: parseTraceHandType(payload.hand),
          selectedSlot: parseIntegerNumber(
            payload.selectedSlot,
            "payload.selectedSlot must be an integer number"
          ),
          heldItem: parseItemStackSnapshot(payload.heldItem, "payload.heldItem")
        }
      };

    case CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION:
      return {
        ...base,
        kind,
        payload: {
          worldTick: context.worldTick,
          dimensionKey: context.dimensionKey,
          containerKind: parseNonEmptyString(
            payload.containerKind,
            "payload.containerKind must be a non-empty string"
          ),
          source: parseNonEmptyString(payload.source, "payload.source must be a non-empty string"),
          changedSlots: parseInventorySlotDeltaList(payload.changedSlots)
        }
      };
  }
}

function parseTraceFrame(input: unknown): ParsedTraceFrame {
  const frame = parseRecord(input, "frame must decode to an object");

  if (frame.v !== CURRENT_MOD_TRACE_VERSION) {
    fail("unsupported trace version");
  }

  return {
    kind: parseTraceKind(frame.kind),
    sessionId: parseNonEmptyString(frame.sessionId, "sessionId must be a non-empty string"),
    seq: parseIntegerNumber(frame.seq, "seq must be an integer number"),
    capturedAtMillis: parseIntegerNumber(
      frame.capturedAtMillis,
      "capturedAtMillis must be an integer number"
    ),
    payload: frame.payload
  };
}

function parsePayload(value: unknown): Record<string, unknown> {
  return parseRecord(value, "payload must be an object");
}

function parseCommonTraceContext(payload: Record<string, unknown>): CommonTraceContext {
  return {
    worldTick: parseIntegerNumber(payload.worldTick, "payload.worldTick must be an integer number"),
    dimensionKey: parseNonEmptyString(
      payload.dimensionKey,
      "payload.dimensionKey must be a non-empty string"
    )
  };
}

function parseLookTarget(value: unknown, path: string): TraceLookTarget {
  const target = parseRecord(value, `${path} must be an object`);
  const kind = parseTraceTargetKind(target.kind, `${path}.kind must be a supported target kind`);

  if (target.targetDescription != null && typeof target.targetDescription !== "string") {
    fail(`${path}.targetDescription must be a string when present`);
  }

  const base: TraceLookTarget = {
    kind,
    ...(typeof target.targetDescription === "string"
      ? {
          targetDescription: target.targetDescription
        }
      : {})
  };

  if (kind === "block") {
    return {
      ...base,
      block: parseBlockReference(target.block, `${path}.block`)
    };
  }

  if (kind === "entity") {
    return {
      ...base,
      entity: parseLookTargetEntity(target.entity, `${path}.entity`)
    };
  }

  return base;
}

function parseLookTargetEntity(value: unknown, path: string): TraceLookTargetEntityDetails {
  const entity = parseRecord(value, `${path} must be an object`);
  const entityId = entity.entityId;

  if (entityId != null && !isIntegerNumber(entityId)) {
    fail(`${path}.entityId must be an integer number when present`);
  }

  return {
    entityTypeId: parseNonEmptyString(
      entity.entityTypeId,
      `${path}.entityTypeId must be a non-empty string`
    ),
    ...(isIntegerNumber(entityId) ? { entityId } : {})
  };
}

function parseBlockReference(value: unknown, path: string): TraceBlockReference {
  const block = parseRecord(value, `${path} must be an object`);
  const hitFace = block.hitFace;

  if (hitFace != null && !isTraceBlockFace(hitFace)) {
    fail(`${path}.hitFace must be a supported block face when present`);
  }

  return {
    blockId: parseNonEmptyString(block.blockId, `${path}.blockId must be a non-empty string`),
    position: parseBlockPosition(block.position, `${path}.position`),
    ...(isTraceBlockFace(hitFace) ? { hitFace } : {})
  };
}

function parseBlockPosition(value: unknown, path: string): TraceBlockPosition {
  const position = parseRecord(value, `${path} must be an object`);
  const x = position.x;
  const y = position.y;
  const z = position.z;

  if (!isIntegerNumber(x) || !isIntegerNumber(y) || !isIntegerNumber(z)) {
    fail(`${path} coordinates must be integer numbers`);
  }

  return {
    x,
    y,
    z
  };
}

function parseItemStackSnapshot(value: unknown, path: string): TraceItemStackSnapshot {
  const item = parseRecord(value, `${path} must be an object`);

  if (item.itemId != null && (typeof item.itemId !== "string" || item.itemId.length === 0)) {
    fail(`${path}.itemId must be a non-empty string or null`);
  }

  return {
    itemId: item.itemId ?? null,
    count: parseNonNegativeInteger(item.count, `${path}.count must be a non-negative integer`),
    damage: parseNonNegativeInteger(item.damage, `${path}.damage must be a non-negative integer`),
    maxDamage: parseNonNegativeInteger(
      item.maxDamage,
      `${path}.maxDamage must be a non-negative integer`
    )
  };
}

function parseInventorySlotDeltaList(value: unknown): readonly TraceInventorySlotDelta[] {
  if (!Array.isArray(value)) {
    fail("payload.changedSlots must be an array");
  }

  return value.map((slot, index) => parseInventorySlotDelta(slot, `payload.changedSlots[${index}]`));
}

function parseInventorySlotDelta(value: unknown, path: string): TraceInventorySlotDelta {
  const slot = parseRecord(value, `${path} must be an object`);

  return {
    slot: parseIntegerNumber(slot.slot, `${path}.slot must be an integer number`),
    previous: parseItemStackSnapshot(slot.previous, `${path}.previous`),
    current: parseItemStackSnapshot(slot.current, `${path}.current`)
  };
}

function parseTraceKind(value: unknown): RawTraceKind {
  if (!isSupportedTraceKind(value)) {
    fail("unsupported trace kind");
  }

  return value;
}

function parseTraceHandType(value: unknown): TraceHandType {
  if (!isTraceHandType(value)) {
    fail("payload.hand must be a supported hand type");
  }

  return value;
}

function parseTraceTargetKind(value: unknown, reason: string): TraceTargetKind {
  if (!isTraceTargetKind(value)) {
    fail(reason);
  }

  return value;
}

function parseRecord(value: unknown, reason: string): Record<string, unknown> {
  if (!isRecord(value)) {
    fail(reason);
  }

  return value;
}

function parseString(value: unknown, reason: string): string {
  if (typeof value !== "string") {
    fail(reason);
  }

  return value;
}

function parseNonEmptyString(value: unknown, reason: string): string {
  if (typeof value !== "string" || value.length === 0) {
    fail(reason);
  }

  return value;
}

function parseFiniteNumber(value: unknown, reason: string): number {
  if (!isFiniteNumber(value)) {
    fail(reason);
  }

  return value;
}

function parseIntegerNumber(value: unknown, reason: string): number {
  if (!isIntegerNumber(value)) {
    fail(reason);
  }

  return value;
}

function parseNonNegativeInteger(value: unknown, reason: string): number {
  if (!isNonNegativeInteger(value)) {
    fail(reason);
  }

  return value;
}

function fail(reason: string): never {
  throw new Error(reason);
}

function isSupportedTraceKind(value: unknown): value is RawTraceKind {
  return (
    value === CURRENT_MOD_TRACE_KIND_TRACE_SESSION_START ||
    value === CURRENT_MOD_TRACE_KIND_TRACE_SESSION_END ||
    value === CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE ||
    value === CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED ||
    value === CURRENT_MOD_TRACE_KIND_PLAYER_SELECTED_SLOT_CHANGED ||
    value === CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED ||
    value === CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_ATTACK_ATTEMPT ||
    value === CURRENT_MOD_TRACE_KIND_INTERACTION_BLOCK_BREAK_SUCCESS ||
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

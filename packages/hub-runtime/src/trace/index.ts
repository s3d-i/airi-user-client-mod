export const CURRENT_MOD_TRACE_VERSION = 1 as const;
export const CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE = "observation.sample" as const;

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

export type CurrentModTraceEvent = CurrentModObservationSampleTraceEvent;
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

  if (value.kind !== CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE) {
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

  const payload = value.payload;

  if (!isIntegerNumber(payload.worldTick)) {
    return { ok: false, reason: "payload.worldTick must be an integer number" };
  }

  if (!isIntegerNumber(payload.fps)) {
    return { ok: false, reason: "payload.fps must be an integer number" };
  }

  if (typeof payload.dimensionKey !== "string" || payload.dimensionKey.length === 0) {
    return { ok: false, reason: "payload.dimensionKey must be a non-empty string" };
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

  const event: CurrentModObservationSampleTraceEvent = {
    v: CURRENT_MOD_TRACE_VERSION,
    kind: CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE,
    sessionId: value.sessionId,
    seq: value.seq,
    capturedAtMillis: value.capturedAtMillis,
    payload: {
      worldTick: payload.worldTick,
      fps: payload.fps,
      dimensionKey: payload.dimensionKey,
      x: payload.x,
      y: payload.y,
      z: payload.z,
      vx: payload.vx,
      vy: payload.vy,
      vz: payload.vz,
      targetDescription: payload.targetDescription
    }
  };

  return {
    ok: true,
    event
  };
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

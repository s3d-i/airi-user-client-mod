import type { ObservationSampleTraceEvent, RawTraceEvent } from "@airi-client-mod/hub-runtime";

export interface TraceDecodeSuccess {
  readonly ok: true;
  readonly event: RawTraceEvent;
}

export interface TraceDecodeFailure {
  readonly ok: false;
  readonly reason: string;
}

export type TraceDecodeResult = TraceDecodeSuccess | TraceDecodeFailure;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function isIntegerNumber(value: unknown): value is number {
  return isFiniteNumber(value) && Number.isInteger(value);
}

export function decodeRawTraceEvent(value: unknown): TraceDecodeResult {
  if (!isRecord(value)) {
    return { ok: false, reason: "frame must decode to an object" };
  }

  if (value.v !== 1) {
    return { ok: false, reason: "unsupported trace version" };
  }

  if (value.kind !== "observation.sample") {
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

  const event: ObservationSampleTraceEvent = {
    v: 1,
    kind: "observation.sample",
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

export function parseRawTraceEventMessage(text: string): TraceDecodeResult {
  try {
    return decodeRawTraceEvent(JSON.parse(text));
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    return {
      ok: false,
      reason: `invalid JSON: ${reason}`
    };
  }
}

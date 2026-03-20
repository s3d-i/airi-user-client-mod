export interface ObservationSampleTracePayload {
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

export interface ObservationSampleTraceEvent {
  readonly v: 1;
  readonly kind: "observation.sample";
  readonly sessionId: string;
  readonly seq: number;
  readonly capturedAtMillis: number;
  readonly payload: ObservationSampleTracePayload;
}

export type RawTraceEvent = ObservationSampleTraceEvent;

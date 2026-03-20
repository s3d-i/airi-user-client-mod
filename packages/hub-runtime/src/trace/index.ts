export interface RawTraceEvent {
  readonly kind: string;
  readonly timestamp: number;
  readonly payload?: Record<string, unknown>;
}

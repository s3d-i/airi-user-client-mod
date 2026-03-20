import type { HubLogger, HubTraceSink, RawTraceEvent } from "@airi-client-mod/hub-runtime";

const DEFAULT_TRACE_STORE_CAPACITY = 200;

export interface RetainedTraceRecord {
  readonly traceId: string;
  readonly retainedAtMillis: number;
  readonly event: RawTraceEvent;
}

export interface HubTraceStoreSnapshot {
  readonly capacity: number;
  readonly retainedCount: number;
  readonly droppedCount: number;
  readonly oldestTraceId?: string;
  readonly newestTraceId?: string;
  readonly lastRetainedAt?: number;
}

export interface HubTraceStoreQuery {
  readonly limit?: number;
}

export interface HubTraceStore extends HubTraceSink {
  listRecent(query?: HubTraceStoreQuery): readonly RetainedTraceRecord[];
  snapshot(): HubTraceStoreSnapshot;
}

export interface CreateHubTraceStoreOptions {
  readonly capacity?: number;
  readonly logger: HubLogger;
}

export function createHubTraceId(event: RawTraceEvent): string {
  return `${event.sessionId}:${event.seq}`;
}

export function createHubTraceStore(options: CreateHubTraceStoreOptions): HubTraceStore {
  const capacity = normalizeCapacity(options.capacity ?? DEFAULT_TRACE_STORE_CAPACITY);
  const { logger } = options;
  const retained: RetainedTraceRecord[] = [];
  let droppedCount = 0;
  let lastRetainedAt: number | undefined;

  return {
    acceptTrace(event) {
      const retainedAtMillis = Date.now();
      const record: RetainedTraceRecord = {
        traceId: createHubTraceId(event),
        retainedAtMillis,
        event
      };

      retained.push(record);
      lastRetainedAt = retainedAtMillis;

      if (retained.length > capacity) {
        const dropped = retained.shift();
        droppedCount += 1;

        if (dropped) {
          logger.debug("evicted retained trace", {
            droppedTraceId: dropped.traceId,
            droppedCount
          });
        }
      }

      logger.debug("retained trace", {
        retainedCount: retained.length,
        traceId: record.traceId
      });
    },
    listRecent(query = {}) {
      const limit = clampLimit(query.limit, retained.length);

      if (limit === 0) {
        return [];
      }

      return retained.slice(-limit).reverse();
    },
    snapshot() {
      return {
        capacity,
        retainedCount: retained.length,
        droppedCount,
        oldestTraceId: retained[0]?.traceId,
        newestTraceId: retained[retained.length - 1]?.traceId,
        lastRetainedAt
      };
    }
  };
}

function normalizeCapacity(value: number): number {
  if (!Number.isInteger(value) || value <= 0) {
    throw new RangeError(`invalid trace store capacity: ${value}`);
  }

  return value;
}

function clampLimit(limit: number | undefined, max: number): number {
  if (limit == null) {
    return max;
  }

  if (!Number.isInteger(limit) || limit < 0) {
    throw new RangeError(`invalid trace query limit: ${limit}`);
  }

  return Math.min(limit, max);
}

import type { HubLogReader, HubLogStoreSnapshot } from "@airi-client-mod/hub-debug-surface";
import type { HubLogEntry, HubLogSink } from "@airi-client-mod/hub-runtime";

const DEFAULT_LOG_BUFFER_CAPACITY = 400;

export interface MemoryHubLogBuffer extends HubLogReader, HubLogSink {}

export function createMemoryHubLogBuffer(capacity = DEFAULT_LOG_BUFFER_CAPACITY): MemoryHubLogBuffer {
  if (!Number.isInteger(capacity) || capacity <= 0) {
    throw new RangeError(`invalid log buffer capacity: ${capacity}`);
  }

  const retained: HubLogEntry[] = [];
  let droppedCount = 0;
  let lastEntryAt: number | undefined;

  return {
    write(entry) {
      retained.push(entry);
      lastEntryAt = entry.timestamp;

      if (retained.length > capacity) {
        retained.shift();
        droppedCount += 1;
      }
    },
    listRecent(limit) {
      if (limit == null) {
        return retained.slice().reverse();
      }

      if (!Number.isInteger(limit) || limit < 0) {
        throw new RangeError(`invalid log query limit: ${limit}`);
      }

      if (limit === 0) {
        return [];
      }

      return retained.slice(-limit).reverse();
    },
    snapshot(): HubLogStoreSnapshot {
      return {
        capacity,
        retainedCount: retained.length,
        droppedCount,
        lastEntryAt
      };
    }
  };
}

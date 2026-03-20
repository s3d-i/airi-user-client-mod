import type { HubLogReader, HubLogStoreSnapshot } from "@airi-client-mod/hub-debug-surface";
import type { HubLogEntry, HubLogFields, HubLogger, HubLogLevel } from "@airi-client-mod/hub-runtime";

const DEFAULT_LOG_BUFFER_CAPACITY = 400;

interface HubLogSink {
  write(entry: HubLogEntry): void;
}

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

export function createConsoleHubLogSink(
  output: Pick<Console, "debug" | "info" | "warn" | "error"> = console
): HubLogSink {
  return {
    write(entry) {
      const renderedFields = entry.fields == null ? "" : ` ${JSON.stringify(entry.fields)}`;
      const line = `[${entry.scope}] ${entry.message}${renderedFields}`;

      switch (entry.level) {
        case "debug":
          output.debug(line);
          return;
        case "info":
          output.info(line);
          return;
        case "warn":
          output.warn(line);
          return;
        case "error":
          output.error(line);
          return;
      }
    }
  };
}

export function createStructuredHubLogger(options: {
  readonly scope: string;
  readonly sinks: readonly HubLogSink[];
  readonly clock?: () => number;
}): HubLogger {
  const clock = options.clock ?? (() => Date.now());
  let nextId = 1;

  const write = (scope: string, level: HubLogLevel, message: string, fields?: HubLogFields) => {
    const entry: HubLogEntry = {
      id: nextId,
      timestamp: clock(),
      level,
      scope,
      message,
      fields
    };

    nextId += 1;

    for (const sink of options.sinks) {
      sink.write(entry);
    }
  };

  const createScopedLogger = (scope: string): HubLogger => ({
    scope,
    child(childScope) {
      return createScopedLogger(joinScope(scope, childScope));
    },
    debug(message, fields) {
      write(scope, "debug", message, fields);
    },
    info(message, fields) {
      write(scope, "info", message, fields);
    },
    warn(message, fields) {
      write(scope, "warn", message, fields);
    },
    error(message, fields) {
      write(scope, "error", message, fields);
    }
  });

  return createScopedLogger(options.scope);
}

function joinScope(parent: string, child: string): string {
  return parent.length === 0 ? child : `${parent}.${child}`;
}

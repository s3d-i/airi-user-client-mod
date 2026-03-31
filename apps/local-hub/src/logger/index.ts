import type { HubLogReader, HubLogStoreSnapshot } from "@airi-client-mod/hub-debug-surface";
import type {
  HubLogEntry,
  HubLogFields,
  HubLogLevel,
  HubLogger,
  HubLogSink
} from "@airi-client-mod/hub-runtime";
import {
  useGlobalLogger as useLoggLogger,
  type Logg
} from "@guiiai/logg";

const DEFAULT_LOG_BUFFER_CAPACITY = 400;
const DEFAULT_LOGGER_SCOPE = "hub";

export interface MemoryHubLogBuffer extends HubLogReader, HubLogSink {}

type LogOutput = Pick<Console, "debug" | "info" | "warn" | "error">;

export interface UseLoggerOptions {
  readonly sinks?: readonly HubLogSink[];
  readonly clock?: () => number;
  readonly writeToGlobalLogg?: boolean;
}

interface LoggerState {
  nextId: number;
  readonly sinks: readonly HubLogSink[];
  readonly clock: () => number;
  readonly writeToGlobalLogg: boolean;
}

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

export function createConsoleHubLogSink(output: LogOutput = console): HubLogSink {
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

export function createNoopHubLogger(scope = DEFAULT_LOGGER_SCOPE): HubLogger {
  return {
    scope,
    child(childScope) {
      return createNoopHubLogger(joinScope(scope, childScope));
    },
    debug() {
      return undefined;
    },
    info() {
      return undefined;
    },
    warn() {
      return undefined;
    },
    error() {
      return undefined;
    }
  };
}

export function useLogger(scope: string, options: UseLoggerOptions = {}): HubLogger {
  return createScopedHubLogger(scope, {
    nextId: 1,
    sinks: options.sinks ?? [],
    clock: options.clock ?? (() => Date.now()),
    writeToGlobalLogg: options.writeToGlobalLogg ?? true
  });
}

function createScopedHubLogger(scope: string, state: LoggerState): HubLogger {
  const logg = useLoggLogger(scope);

  const emit = (level: HubLogLevel, message: string, fields?: HubLogFields) => {
    if (state.writeToGlobalLogg) {
      writeWithLogg(logg, level, message, fields);
    }

    const entry: HubLogEntry = {
      id: state.nextId,
      timestamp: state.clock(),
      level,
      scope,
      message,
      fields
    };
    state.nextId += 1;

    for (const sink of state.sinks) {
      sink.write(entry);
    }
  };

  return {
    scope,
    child(childScope) {
      return createScopedHubLogger(joinScope(scope, childScope), state);
    },
    debug(message, fields) {
      emit("debug", message, fields);
    },
    info(message, fields) {
      emit("info", message, fields);
    },
    warn(message, fields) {
      emit("warn", message, fields);
    },
    error(message, fields) {
      emit("error", message, fields);
    }
  };
}

function writeWithLogg(
  logg: Logg,
  level: HubLogLevel,
  message: string,
  fields?: HubLogFields
): void {
  const scopedLogg = fields == null ? logg : logg.withFields({ ...fields });

  switch (level) {
    case "debug":
      scopedLogg.debug(message);
      return;
    case "info":
      scopedLogg.log(message);
      return;
    case "warn":
      scopedLogg.warn(message);
      return;
    case "error":
      scopedLogg.error(message);
      return;
  }
}

function joinScope(parent: string, child: string): string {
  return parent.length === 0 ? child : `${parent}.${child}`;
}

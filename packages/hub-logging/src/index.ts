import type { HubLogEntry, HubLogFields, HubLogLevel, HubLogSink, HubLogger } from "@airi-client-mod/hub-runtime";

export function createNoopHubLogger(scope = "hub"): HubLogger {
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

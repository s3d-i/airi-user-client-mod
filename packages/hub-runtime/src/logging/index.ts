export type HubLogLevel = "debug" | "info" | "warn" | "error";

export interface HubLogFields {
  readonly [key: string]: unknown;
}

export interface HubLogEntry {
  readonly id: number;
  readonly timestamp: number;
  readonly level: HubLogLevel;
  readonly scope: string;
  readonly message: string;
  readonly fields?: HubLogFields;
}

export interface HubLogger {
  readonly scope: string;
  child(scope: string): HubLogger;
  debug(message: string, fields?: HubLogFields): void;
  info(message: string, fields?: HubLogFields): void;
  warn(message: string, fields?: HubLogFields): void;
  error(message: string, fields?: HubLogFields): void;
}

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

function joinScope(parent: string, child: string): string {
  return parent.length === 0 ? child : `${parent}.${child}`;
}

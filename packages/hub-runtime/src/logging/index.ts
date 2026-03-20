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

export interface HubLogSink {
  write(entry: HubLogEntry): void;
}

export interface HubLogger {
  readonly scope: string;
  child(scope: string): HubLogger;
  debug(message: string, fields?: HubLogFields): void;
  info(message: string, fields?: HubLogFields): void;
  warn(message: string, fields?: HubLogFields): void;
  error(message: string, fields?: HubLogFields): void;
}

export interface HubLoggerFactory {
  create(scope: string): HubLogger;
}

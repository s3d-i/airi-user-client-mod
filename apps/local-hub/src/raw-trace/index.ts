import { appendFileSync, closeSync, fsyncSync, mkdirSync, openSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import type {
  HubLogger,
  RawTraceEvent,
  SessionEndTraceEvent,
  SessionStartTraceEvent
} from "@airi-client-mod/hub-runtime";

const RAW_TRACE_DATA_DIR_ENV = "AIRI_LOCAL_HUB_RAW_TRACE_DIR";
const DEFAULT_RAW_TRACE_DATA_DIR = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..", "data", "raw-traces");

interface ActiveSessionFile {
  readonly fileDescriptor: number;
  readonly filePath: string;
  readonly sessionId: string;
}

export interface RawTraceJsonlWriterOptions {
  readonly dataDir?: string;
  readonly logger: HubLogger;
}

export interface RawTraceJsonlWriter {
  readonly dataDir: string;
  acceptTrace(event: RawTraceEvent): void;
  close(): void;
}

export function resolveRawTraceDataDir(configuredDataDir?: string): string {
  const resolved = configuredDataDir ?? process.env[RAW_TRACE_DATA_DIR_ENV] ?? DEFAULT_RAW_TRACE_DATA_DIR;
  return resolve(resolved);
}

export function createRawTraceJsonlWriter(options: RawTraceJsonlWriterOptions): RawTraceJsonlWriter {
  const { logger } = options;
  const dataDir = resolveRawTraceDataDir(options.dataDir);
  let activeSessionFile: ActiveSessionFile | undefined;

  const ensureDataDir = () => {
    mkdirSync(dataDir, {
      recursive: true
    });
  };

  const closeActiveSessionFile = (reason: string) => {
    if (activeSessionFile == null) {
      return;
    }

    const current = activeSessionFile;
    activeSessionFile = undefined;

    try {
      fsyncSync(current.fileDescriptor);
    } catch (error) {
      logger.warn("failed to flush raw trace session file", {
        error: error instanceof Error ? error.message : String(error),
        filePath: current.filePath,
        reason,
        sessionId: current.sessionId
      });
    }

    try {
      closeSync(current.fileDescriptor);
    } catch (error) {
      logger.warn("failed to close raw trace session file", {
        error: error instanceof Error ? error.message : String(error),
        filePath: current.filePath,
        reason,
        sessionId: current.sessionId
      });
      return;
    }

    logger.info("closed raw trace session file", {
      filePath: current.filePath,
      reason,
      sessionId: current.sessionId
    });
  };

  const appendEventLine = (event: RawTraceEvent) => {
    if (activeSessionFile == null) {
      logger.warn("dropping raw trace without an active session file", {
        kind: event.kind,
        seq: event.seq,
        sessionId: event.sessionId
      });
      return;
    }

    if (activeSessionFile.sessionId !== event.sessionId) {
      logger.warn("dropping raw trace for a non-active session file", {
        activeSessionId: activeSessionFile.sessionId,
        kind: event.kind,
        seq: event.seq,
        sessionId: event.sessionId
      });
      return;
    }

    try {
      appendFileSync(activeSessionFile.fileDescriptor, `${JSON.stringify(event)}\n`, "utf8");
    } catch (error) {
      logger.warn("failed to append raw trace event", {
        error: error instanceof Error ? error.message : String(error),
        filePath: activeSessionFile.filePath,
        kind: event.kind,
        seq: event.seq,
        sessionId: event.sessionId
      });
    }
  };

  const handleSessionStart = (event: SessionStartTraceEvent) => {
    if (activeSessionFile != null) {
      if (activeSessionFile.sessionId === event.sessionId) {
        appendEventLine(event);
        logger.info("accepted replayed raw trace session start for active session", {
          filePath: activeSessionFile.filePath,
          sessionId: event.sessionId
        });
        return;
      }

      logger.warn("raw trace writer received session start with an open session file; forcing rotation", {
        nextSessionId: event.sessionId,
        openSessionId: activeSessionFile.sessionId
      });
      closeActiveSessionFile("forced rotation on session start");
    }

    ensureDataDir();
    const filePath = join(dataDir, `${event.capturedAtMillis}-${sanitizeSessionId(event.sessionId)}.jsonl`);
    let fileDescriptor: number;

    try {
      fileDescriptor = openSync(filePath, "a");
    } catch (error) {
      logger.warn("failed to open raw trace session file", {
        error: error instanceof Error ? error.message : String(error),
        filePath,
        sessionId: event.sessionId
      });
      activeSessionFile = undefined;
      return;
    }

    activeSessionFile = {
      fileDescriptor,
      filePath,
      sessionId: event.sessionId
    };
    appendEventLine(event);
    logger.info("opened raw trace session file", {
      filePath,
      sessionId: event.sessionId
    });
  };

  const handleSessionEnd = (event: SessionEndTraceEvent) => {
    if (activeSessionFile == null) {
      logger.warn("raw trace writer received session end without an open session file", {
        sessionId: event.sessionId
      });
      return;
    }

    if (activeSessionFile.sessionId != event.sessionId) {
      logger.warn("raw trace writer received session end for a non-active session file", {
        activeSessionId: activeSessionFile.sessionId,
        sessionId: event.sessionId
      });
      return;
    }

    appendEventLine(event);
    closeActiveSessionFile("session end");
  };

  return {
    dataDir,
    acceptTrace(event) {
      switch (event.kind) {
        case "trace.session.start":
          handleSessionStart(event);
          return;
        case "trace.session.end":
          handleSessionEnd(event);
          return;
        default:
          appendEventLine(event);
          return;
      }
    },
    close() {
      closeActiveSessionFile("local hub shutdown");
    }
  };
}

function sanitizeSessionId(sessionId: string): string {
  return sessionId.replace(/[^A-Za-z0-9._-]/g, "_");
}

import { startTransition, type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { Virtuoso, type VirtuosoHandle } from "react-virtuoso";

import type { HubDebugState } from "@airi-client-mod/hub-debug-surface";

import { fetchDebugState, openDebugStateFeed, resolveDebugSurfaceBaseUrl } from "./api.js";
import {
  describeTraceDetail,
  describeTraceSummary,
  toMotionSampleRows,
  toWoodEvidenceView,
  type DisplayValueRow,
  type StatRow
} from "./trace-contract-adapter.js";

type FeedStatus = "connecting" | "live" | "error";

interface AppModel {
  readonly status: FeedStatus;
  readonly state?: HubDebugState;
  readonly error?: string;
}

const timeFormatter = new Intl.DateTimeFormat(undefined, {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit"
});

type LogEntry = HubDebugState["logs"][number];
type LogLevel = LogEntry["level"];

const LOG_LEVELS: readonly LogLevel[] = ["debug", "info", "warn", "error"];
const LOG_AUTO_SCROLL_BOTTOM_THRESHOLD_PX = 24;

const LOG_LEVEL_BAR_CLASS: Record<LogLevel, string> = {
  debug: "border-l-[0.35rem] border-l-[rgba(41,119,104,0.65)]",
  info: "border-l-[0.35rem] border-l-[rgba(15,98,153,0.65)]",
  warn: "border-l-[0.35rem] border-l-[rgba(184,118,11,0.70)]",
  error: "border-l-[0.35rem] border-l-[rgba(173,32,32,0.75)]"
};

const PANEL_CLASS =
  "relative min-w-0 rounded-3xl border border-[#1c1a17]/10 bg-[rgba(255,251,245,0.84)] shadow-[0_1.25rem_3rem_rgba(84,64,38,0.12)] backdrop-blur-[18px] animate-[fade-up_520ms_ease_both]";
const MUTED_TEXT_CLASS = "m-0 text-[#1c1a17]/72";
const LIST_CLASS = "grid max-h-[32rem] gap-3 overflow-auto pr-1";
const LIST_ROW_CLASS = "grid min-w-0 gap-3 rounded-2xl bg-[#1c1a17]/4 px-4 py-4 md:grid-cols-[minmax(0,1fr)_auto]";
const LIST_META_CLASS = "min-w-0 flex flex-wrap gap-2 font-mono text-[0.8rem] text-[#1c1a17]/62";
const FIELD_LABEL_CLASS = "grid gap-1.5";
const FIELD_LABEL_TEXT_CLASS = "text-[0.72rem] font-semibold uppercase tracking-[0.08em] text-[#1c1a17]/62";
const FIELD_TEXT_INPUT_CLASS =
  "box-border w-full appearance-none rounded-xl border border-[#1c1a17]/14 bg-white/66 px-3 py-2 text-sm outline-none transition focus:border-[#8f3a1d]/45 focus:ring-2 focus:ring-[#8f3a1d]/20";
const FILTER_TOGGLE_CLASS = "inline-flex items-center gap-1.5 text-[0.88rem]";

const DEFAULT_LOG_LEVEL_FILTERS: Record<LogLevel, boolean> = {
  debug: true,
  info: true,
  warn: true,
  error: true
};

export function App() {
  const [model, setModel] = useState<AppModel>({
    status: "connecting"
  });
  const [logLevelFilters, setLogLevelFilters] = useState<Record<LogLevel, boolean>>(
    DEFAULT_LOG_LEVEL_FILTERS
  );
  const [logQuery, setLogQuery] = useState("");
  const [logScopePrefix, setLogScopePrefix] = useState("");
  const [isLogAutoScrollPaused, setIsLogAutoScrollPaused] = useState(false);
  const [isLogPinnedToBottom, setIsLogPinnedToBottom] = useState(true);
  const logListRef = useRef<VirtuosoHandle | null>(null);

  useEffect(() => {
    const abortController = new AbortController();

    void fetchDebugState(abortController.signal)
      .then(state => {
        startTransition(() => {
          setModel({
            status: "connecting",
            state
          });
        });
      })
      .catch(error => {
        if (abortController.signal.aborted) {
          return;
        }

        startTransition(() => {
          setModel({
            status: "error",
            error: error instanceof Error ? error.message : String(error)
          });
        });
      });

    const closeFeed = openDebugStateFeed({
      onState: state => {
        startTransition(() => {
          setModel({
            status: "live",
            state
          });
        });
      },
      onError: message => {
        startTransition(() => {
          setModel(previous => ({
            status: previous.state == null ? "error" : "connecting",
            state: previous.state,
            error: message
          }));
        });
      }
    });

    return () => {
      abortController.abort();
      closeFeed();
    };
  }, []);

  const state = model.state;
  const headline = useMemo(() => {
    if (state == null) {
      return "Waiting for local hub debug surface";
    }

    return state.ingress.listening
      ? `Ingress live on ${state.ingress.boundAddress?.url ?? "unknown address"}`
      : "Ingress not listening";
  }, [state]);
  const latestMotionSampleRows = useMemo(
    () => toMotionSampleRows(state?.runtime.latestMotionSample),
    [state?.runtime.latestMotionSample]
  );
  const woodEvidence = useMemo(
    () => (state == null ? undefined : toWoodEvidenceView(state.runtime)),
    [state]
  );
  const filteredLogs = useMemo(() => {
    const recentLogs = state?.logs;

    if (recentLogs == null) {
      return [];
    }

    const logs = recentLogs.length > 1 ? recentLogs.slice().reverse() : recentLogs;

    const normalizedQuery = logQuery.trim().toLowerCase();
    const normalizedScopePrefix = logScopePrefix.trim().toLowerCase();

    return logs.filter(entry => {
      if (!logLevelFilters[entry.level]) {
        return false;
      }

      if (
        normalizedScopePrefix.length > 0
        && !entry.scope.toLowerCase().startsWith(normalizedScopePrefix)
      ) {
        return false;
      }

      if (normalizedQuery.length === 0) {
        return true;
      }

      if (entry.message.toLowerCase().includes(normalizedQuery)) {
        return true;
      }

      if (entry.scope.toLowerCase().includes(normalizedQuery)) {
        return true;
      }

      if (entry.fields == null) {
        return false;
      }

      return JSON.stringify(entry.fields).toLowerCase().includes(normalizedQuery);
    });
  }, [state?.logs, logLevelFilters, logQuery, logScopePrefix]);
  useEffect(() => {
    if (isLogAutoScrollPaused || !isLogPinnedToBottom) {
      return;
    }

    if (filteredLogs.length === 0) {
      return;
    }

    logListRef.current?.scrollToIndex({
      index: filteredLogs.length - 1,
      align: "end",
      behavior: "auto"
    });
  }, [filteredLogs.length, isLogAutoScrollPaused, isLogPinnedToBottom]);

  const handleLogAutoScrollPauseChange = (nextPaused: boolean) => {
    setIsLogAutoScrollPaused(nextPaused);

    if (nextPaused) {
      setIsLogPinnedToBottom(false);
      return;
    }

    setIsLogPinnedToBottom(true);
  };

  return (
    <main className="relative min-h-screen overflow-hidden bg-gradient-to-b from-[#f7efe1] to-[#efe4d1] px-4 pb-12 pt-8 text-[#1c1a17] leading-relaxed [font-synthesis:none] [text-rendering:optimizeLegibility] sm:px-6 lg:px-10">
      <div className="pointer-events-none absolute -left-28 -top-24 -z-10 h-96 w-96 rounded-full bg-[#eb5e28]/18 blur-3xl" />
      <div className="pointer-events-none absolute -right-24 -top-20 -z-10 h-80 w-80 rounded-full bg-[#297768]/18 blur-3xl" />

      <section className={`${PANEL_CLASS} mx-auto mb-6 max-w-[90rem] p-6 sm:p-5`}>
        <p className="mb-3 text-[0.8rem] font-semibold uppercase tracking-[0.18em] text-[#8f3a1d]">
          Local Hub / Debug Surface
        </p>
        <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1.75fr)_minmax(18rem,1fr)]">
          <div>
            <h1 className="m-0 max-w-[12ch] font-display text-[clamp(2.3rem,5vw,4.8rem)] leading-[1.05]">
              {headline}
            </h1>
            <p className={`${MUTED_TEXT_CLASS} mt-3`}>
              Separate Vite UI over the read-only Node debug surface. The ingress contract stays
              thin, retention stays in-memory, and the UI only consumes debug APIs.
            </p>
          </div>
          <div className="grid gap-3">
            <StatusPill label="Feed" value={model.status} />
            <StatusPill
              label="Trace Store"
              value={state == null ? "pending" : `${state.traceStore.retainedCount} retained`}
            />
            <StatusPill
              label="Logs"
              value={state == null ? "pending" : `${state.logging.retainedCount} buffered`}
            />
          </div>
        </div>
        <div className="mt-4 grid gap-2 font-mono text-[0.82rem] text-[#1c1a17]/72 sm:grid-cols-2">
          <span>Debug surface: {resolveDebugSurfaceBaseUrl()}</span>
          <span>
            Last refresh: {state == null ? "pending" : timeFormatter.format(state.generatedAtMillis)}
          </span>
        </div>
        {model.error == null ? null : (
          <p className="mt-3 rounded-2xl bg-[#8e2f25]/10 px-3 py-2 text-sm text-[#8e2f25]">{model.error}</p>
        )}
      </section>

      <section className="relative mx-auto grid max-w-[90rem] grid-cols-1 gap-5 xl:grid-cols-2">
        <PanelCard delayMs={70}>
          <PanelTitle title="Ingress Status" subtitle="Transport-only adapter counters and bind state" />
          {state == null ? (
            <EmptyState />
          ) : (
            <dl className="grid grid-cols-1 gap-3 md:grid-cols-2">
              <Stat label="Listening" value={state.ingress.listening ? "yes" : "no"} />
              <Stat label="Peers" value={String(state.ingress.connectedPeers)} />
              <Stat label="Accepted" value={String(state.ingress.acceptedFrames)} />
              <Stat label="Rejected" value={String(state.ingress.rejectedFrames)} />
              <Stat label="Handoff Failures" value={String(state.ingress.handoffFailures)} />
              <Stat
                label="Last Rejection"
                value={state.ingress.lastRejectedReason ?? "none"}
                wide={true}
              />
              <Stat
                label="Ingress URL"
                value={state.ingress.boundAddress?.url ?? "not bound"}
                wide={true}
              />
            </dl>
          )}
        </PanelCard>

        <PanelCard delayMs={140}>
          <PanelTitle
            title="Runtime Snapshot"
            subtitle="Derived evidence, detector support, and episode state from hub-runtime"
          />
          {state == null ? (
            <EmptyState />
          ) : (
            <>
              <dl className="grid grid-cols-1 gap-3 md:grid-cols-2">
                <Stat label="Trace Count" value={String(state.runtime.traceCount)} />
                <Stat label="Last Accepted" value={formatTimestamp(state.runtime.lastAcceptedAt)} />
                <Stat label="Latest Trace" value={state.runtime.latestTrace?.kind ?? "n/a"} />
                <Stat label="Episode" value={state.runtime.episodes.woodGathering.state} wide={true} />
              </dl>
              {latestMotionSampleRows == null ? null : <SampleCardRows rows={latestMotionSampleRows} />}
            </>
          )}
        </PanelCard>

        <PanelCard delayMs={210}>
          <PanelTitle
            title="Wood Evidence"
            subtitle="Bounded reusable projections over the recent trace stream"
          />
          {state == null || woodEvidence == null ? (
            <EmptyState />
          ) : (
            <>
              <StatRows rows={woodEvidence.stats} />
              <SampleCardRows rows={woodEvidence.highlights} />
            </>
          )}
        </PanelCard>

        <PanelCard delayMs={280}>
          <PanelTitle
            title="Detector / Episode"
            subtitle="Explainable support plus the explicit wood-gathering state machine"
          />
          {state == null ? (
            <EmptyState />
          ) : (
            <div className={LIST_CLASS}>
              <div className={LIST_ROW_CLASS}>
                <div>
                  <strong>{state.runtime.detectors.composites.woodGatheringSupport.label}</strong>
                  <p className={`${MUTED_TEXT_CLASS} mt-1`}>
                    active: {state.runtime.detectors.composites.woodGatheringSupport.active ? "yes" : "no"}
                    {" · "}
                    score: {state.runtime.detectors.composites.woodGatheringSupport.score.toFixed(2)}
                  </p>
                </div>
                <div className={LIST_META_CLASS}>
                  <span>{state.runtime.episodes.woodGathering.kind}</span>
                  <span>{state.runtime.episodes.woodGathering.state}</span>
                </div>
              </div>
              {state.runtime.episodes.woodGathering.evidenceSummary.length === 0 ? (
                <EmptyState message="No wood-gathering evidence yet." />
              ) : (
                state.runtime.episodes.woodGathering.evidenceSummary.map(reason => (
                  <div className={LIST_ROW_CLASS} key={`${reason.code}-${reason.message}`}>
                    <div>
                      <strong>{reason.code}</strong>
                      <p className={`${MUTED_TEXT_CLASS} mt-1`}>{reason.message}</p>
                    </div>
                    <div className={LIST_META_CLASS}>
                      <span>{reason.contribution.toFixed(2)}</span>
                      <span>{reason.traceIds.length === 0 ? "derived" : reason.traceIds.join(", ")}</span>
                    </div>
                  </div>
                ))
              )}
            </div>
          )}
        </PanelCard>

        <PanelCard delayMs={350}>
          <PanelTitle title="Recent Traces" subtitle="Bounded in-memory retention owned by hub-trace-store" />
          {state == null ? (
            <EmptyState />
          ) : state.traces.length === 0 ? (
            <EmptyState message="No retained traces yet." />
          ) : (
            <div className={LIST_CLASS}>
              {state.traces.map((trace, index) => (
                <div className={LIST_ROW_CLASS} key={`${trace.traceId}:${trace.retainedAtMillis}:${index}`}>
                  <div>
                    <strong>{trace.traceId}</strong>
                    <p className={`${MUTED_TEXT_CLASS} mt-1`}>{describeTraceSummary(trace.event)}</p>
                  </div>
                  <div className={LIST_META_CLASS}>
                    <span>{formatTimestamp(trace.retainedAtMillis)}</span>
                    <span>{describeTraceDetail(trace.event)}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </PanelCard>

        <PanelCard delayMs={420}>
          <PanelTitle title="Logger Output" subtitle="Structured entries fanned out to console and debug surface" />
          {state == null ? (
            <EmptyState />
          ) : (
            <>
              <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-2 2xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,1fr)_auto] 2xl:items-end">
                <fieldset className="m-0 flex flex-wrap gap-x-3.5 gap-y-2 rounded-2xl border border-[#1c1a17]/14 px-3.5 py-3">
                  <legend className="px-1 text-[0.72rem] font-semibold uppercase tracking-[0.08em] text-[#1c1a17]/62">
                    Level
                  </legend>
                  {LOG_LEVELS.map(level => (
                    <InlineCheckbox
                      checked={logLevelFilters[level]}
                      className={FILTER_TOGGLE_CLASS}
                      key={level}
                      label={level}
                      onChange={checked => {
                        setLogLevelFilters(previous => ({
                          ...previous,
                          [level]: checked
                        }));
                      }}
                    />
                  ))}
                </fieldset>
                <LabeledTextInput
                  label="Keyword"
                  onChange={setLogQuery}
                  placeholder="message / scope / fields"
                  value={logQuery}
                />
                <LabeledTextInput
                  label="Scope Prefix"
                  onChange={setLogScopePrefix}
                  placeholder="hub.runtime"
                  value={logScopePrefix}
                />
                <InlineCheckbox
                  checked={isLogAutoScrollPaused}
                  className="inline-flex items-center gap-1.5 self-start whitespace-nowrap rounded-xl border border-[#1c1a17]/14 bg-white/66 px-3 py-2 text-[0.88rem] 2xl:self-auto"
                  label="Pause Scroll"
                  onChange={handleLogAutoScrollPauseChange}
                />
              </div>

              {state.logs.length === 0 ? (
                <EmptyState message="No buffered log entries yet." />
              ) : filteredLogs.length === 0 ? (
                <EmptyState message="No log entries match current filters." />
              ) : (
                <div className="relative h-[32rem] max-w-full min-w-0 overflow-hidden rounded-2xl border border-[#1c1a17]/10">
                  <Virtuoso
                    atBottomStateChange={isAtBottom => {
                      if (isLogAutoScrollPaused) {
                        return;
                      }

                      setIsLogPinnedToBottom(isAtBottom);
                    }}
                    atBottomThreshold={LOG_AUTO_SCROLL_BOTTOM_THRESHOLD_PX}
                    className="h-full w-full min-w-0 max-w-full overflow-x-hidden"
                    data={filteredLogs}
                    followOutput={false}
                    increaseViewportBy={320}
                    itemContent={(_, entry) => {
                      return (
                        <div className="w-full min-w-0 pb-3 pr-1">
                          <LogEntryCard entry={entry} />
                        </div>
                      );
                    }}
                    ref={logListRef}
                    style={{
                      height: "100%",
                      width: "100%"
                    }}
                  />
                </div>
              )}
            </>
          )}
        </PanelCard>
      </section>
    </main>
  );
}

function PanelCard(props: { readonly delayMs: number; readonly children: ReactNode }) {
  return (
    <article
      className={`${PANEL_CLASS} min-h-0 p-5`}
      style={{
        animationDelay: `${props.delayMs}ms`
      }}
    >
      {props.children}
    </article>
  );
}

function LabeledTextInput(props: {
  readonly label: string;
  readonly value: string;
  readonly placeholder: string;
  readonly onChange: (value: string) => void;
}) {
  return (
    <label className={FIELD_LABEL_CLASS}>
      <span className={FIELD_LABEL_TEXT_CLASS}>{props.label}</span>
      <input
        className={FIELD_TEXT_INPUT_CLASS}
        onChange={event => {
          props.onChange(event.currentTarget.value);
        }}
        placeholder={props.placeholder}
        type="text"
        value={props.value}
      />
    </label>
  );
}

function InlineCheckbox(props: {
  readonly label: string;
  readonly checked: boolean;
  readonly onChange: (checked: boolean) => void;
  readonly className: string;
}) {
  return (
    <label className={props.className}>
      <input
        checked={props.checked}
        onChange={event => {
          props.onChange(event.currentTarget.checked);
        }}
        type="checkbox"
      />
      <span>{props.label}</span>
    </label>
  );
}

function PanelTitle(props: { readonly title: string; readonly subtitle: string }) {
  return (
    <header className="mb-4">
      <h2 className="m-0 font-display text-[1.55rem] leading-[1.05]">{props.title}</h2>
      <p className="m-0 text-[#1c1a17]/72">{props.subtitle}</p>
    </header>
  );
}

function StatusPill(props: { readonly label: string; readonly value: string }) {
  return (
    <div className="grid gap-1 rounded-2xl bg-white/68 px-4 py-3">
      <span className="text-[0.75rem] font-semibold uppercase tracking-[0.08em] text-[#1c1a17]/55">
        {props.label}
      </span>
      <strong className="font-mono text-[0.9rem]">{props.value}</strong>
    </div>
  );
}

function Stat(props: { readonly label: string; readonly value: string; readonly wide?: boolean }) {
  return (
    <div className={`rounded-2xl bg-[#1c1a17]/4 px-4 py-3.5 ${props.wide ? "md:col-span-2" : ""}`}>
      <dt className="mb-1 text-[0.78rem] font-semibold uppercase tracking-[0.08em] text-[#1c1a17]/58">
        {props.label}
      </dt>
      <dd className="m-0 break-words font-mono text-[0.9rem]">{props.value}</dd>
    </div>
  );
}

function StatRows(props: { readonly rows: readonly StatRow[] }) {
  return (
    <dl className="grid grid-cols-1 gap-3 md:grid-cols-2">
      {props.rows.map(row => (
        <Stat key={row.label} label={row.label} value={row.value} wide={row.wide} />
      ))}
    </dl>
  );
}

function SampleCardRows(props: { readonly rows: readonly DisplayValueRow[] }) {
  return (
    <div className="mt-4 grid gap-3 border-t border-[#1c1a17]/10 pt-4 sm:grid-cols-3">
      {props.rows.map(row => (
        <div key={row.label}>
          <span className="mb-1 block text-[0.76rem] font-semibold uppercase tracking-[0.08em] text-[#1c1a17]/58">
            {row.label}
          </span>
          <strong>{row.value}</strong>
        </div>
      ))}
    </div>
  );
}

function EmptyState(props: { readonly message?: string }) {
  return (
    <p className="m-0 rounded-2xl bg-[#1c1a17]/4 px-4 py-4 text-[#1c1a17]/72">
      {props.message ?? "Waiting for debug data."}
    </p>
  );
}

function LogEntryCard(props: { readonly entry: LogEntry }) {
  return (
    <div
      className={`box-border w-full min-w-0 overflow-hidden rounded-2xl bg-[#1c1a17]/4 px-4 py-4 ${LOG_LEVEL_BAR_CLASS[props.entry.level]}`}
    >
      <div className="mb-2 flex flex-wrap gap-2 font-mono text-[0.8rem] text-[#1c1a17]/62">
        <span>{props.entry.level}</span>
        <span>{props.entry.scope}</span>
        <span>{timeFormatter.format(props.entry.timestamp)}</span>
      </div>
      <p className="m-0 break-words text-[#1c1a17]/72">{props.entry.message}</p>
      {props.entry.fields == null ? null : (
        <pre className="mt-3 max-w-full overflow-x-auto whitespace-pre-wrap break-words rounded-xl bg-white/62 p-3 font-mono text-[0.78rem]">
          {JSON.stringify(props.entry.fields, null, 2)}
        </pre>
      )}
    </div>
  );
}

function formatTimestamp(value: number | undefined): string {
  return value == null ? "n/a" : timeFormatter.format(value);
}

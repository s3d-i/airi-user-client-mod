import { startTransition, useEffect, useMemo, useState } from "react";

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

export function App() {
  const [model, setModel] = useState<AppModel>({
    status: "connecting"
  });

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

  return (
    <main className="shell">
      <div className="shell__backdrop" />
      <section className="hero panel panel--hero">
        <p className="eyebrow">Local Hub / Debug Surface</p>
        <div className="hero__row">
          <div>
            <h1>{headline}</h1>
            <p className="lede">
              Separate Vite UI over the read-only Node debug surface. The ingress contract stays thin,
              retention stays in-memory, and the UI only consumes debug APIs.
            </p>
          </div>
          <div className="hero__meta">
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
        <div className="hero__footer">
          <span>Debug surface: {resolveDebugSurfaceBaseUrl()}</span>
          <span>
            Last refresh: {state == null ? "pending" : timeFormatter.format(state.generatedAtMillis)}
          </span>
        </div>
        {model.error == null ? null : <p className="hero__error">{model.error}</p>}
      </section>

      <section className="grid">
        <article className="panel panel--ingress">
          <PanelTitle title="Ingress Status" subtitle="Transport-only adapter counters and bind state" />
          {state == null ? (
            <EmptyState />
          ) : (
            <dl className="stats">
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
        </article>

        <article className="panel panel--snapshot">
          <PanelTitle title="Runtime Snapshot" subtitle="Derived evidence, detector support, and episode state from hub-runtime" />
          {state == null ? (
            <EmptyState />
          ) : (
            <>
              <dl className="stats">
                <Stat label="Trace Count" value={String(state.runtime.traceCount)} />
                <Stat
                  label="Last Accepted"
                  value={formatTimestamp(state.runtime.lastAcceptedAt)}
                />
                <Stat
                  label="Latest Trace"
                  value={state.runtime.latestTrace?.kind ?? "n/a"}
                />
                <Stat
                  label="Episode"
                  value={state.runtime.episodes.woodGathering.state}
                  wide={true}
                />
              </dl>
              {latestMotionSampleRows == null ? null : <SampleCardRows rows={latestMotionSampleRows} />}
            </>
          )}
        </article>

        <article className="panel panel--wood">
          <PanelTitle title="Wood Evidence" subtitle="Bounded reusable projections over the recent trace stream" />
          {state == null || woodEvidence == null ? (
            <EmptyState />
          ) : (
            <>
              <StatRows rows={woodEvidence.stats} />
              <SampleCardRows rows={woodEvidence.highlights} />
            </>
          )}
        </article>

        <article className="panel panel--detectors">
          <PanelTitle title="Detector / Episode" subtitle="Explainable support plus the explicit wood-gathering state machine" />
          {state == null ? (
            <EmptyState />
          ) : (
            <div className="list">
              <div className="list__row">
                <div>
                  <strong>{state.runtime.detectors.composites.woodGatheringSupport.label}</strong>
                  <p>
                    active: {state.runtime.detectors.composites.woodGatheringSupport.active ? "yes" : "no"} · score:{" "}
                    {state.runtime.detectors.composites.woodGatheringSupport.score.toFixed(2)}
                  </p>
                </div>
                <div className="list__meta">
                  <span>{state.runtime.episodes.woodGathering.kind}</span>
                  <span>{state.runtime.episodes.woodGathering.state}</span>
                </div>
              </div>
              {state.runtime.episodes.woodGathering.evidenceSummary.length === 0 ? (
                <EmptyState message="No wood-gathering evidence yet." />
              ) : (
                state.runtime.episodes.woodGathering.evidenceSummary.map(reason => (
                  <div className="list__row" key={`${reason.code}-${reason.message}`}>
                    <div>
                      <strong>{reason.code}</strong>
                      <p>{reason.message}</p>
                    </div>
                    <div className="list__meta">
                      <span>{reason.contribution.toFixed(2)}</span>
                      <span>{reason.traceIds.length === 0 ? "derived" : reason.traceIds.join(", ")}</span>
                    </div>
                  </div>
                ))
              )}
            </div>
          )}
        </article>

        <article className="panel panel--traces">
          <PanelTitle title="Recent Traces" subtitle="Bounded in-memory retention owned by hub-trace-store" />
          {state == null ? (
            <EmptyState />
          ) : state.traces.length === 0 ? (
            <EmptyState message="No retained traces yet." />
          ) : (
            <div className="list">
              {state.traces.map((trace, index) => (
                <div className="list__row" key={`${trace.traceId}:${trace.retainedAtMillis}:${index}`}>
                  <div>
                    <strong>{trace.traceId}</strong>
                    <p>
                      {describeTraceSummary(trace.event)}
                    </p>
                  </div>
                  <div className="list__meta">
                    <span>{formatTimestamp(trace.retainedAtMillis)}</span>
                    <span>{describeTraceDetail(trace.event)}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </article>

        <article className="panel panel--logs">
          <PanelTitle title="Logger Output" subtitle="Structured entries fanned out to console and debug surface" />
          {state == null ? (
            <EmptyState />
          ) : state.logs.length === 0 ? (
            <EmptyState message="No buffered log entries yet." />
          ) : (
            <div className="log-list">
              {state.logs.map(entry => (
                <div className={`log-entry log-entry--${entry.level}`} key={entry.id}>
                  <div className="log-entry__meta">
                    <span>{entry.level}</span>
                    <span>{entry.scope}</span>
                    <span>{timeFormatter.format(entry.timestamp)}</span>
                  </div>
                  <p>{entry.message}</p>
                  {entry.fields == null ? null : (
                    <pre>{JSON.stringify(entry.fields, null, 2)}</pre>
                  )}
                </div>
              ))}
            </div>
          )}
        </article>
      </section>
    </main>
  );
}

function PanelTitle(props: { readonly title: string; readonly subtitle: string }) {
  return (
    <header className="panel__header">
      <h2>{props.title}</h2>
      <p>{props.subtitle}</p>
    </header>
  );
}

function StatusPill(props: { readonly label: string; readonly value: string }) {
  return (
    <div className="status-pill">
      <span>{props.label}</span>
      <strong>{props.value}</strong>
    </div>
  );
}

function Stat(props: { readonly label: string; readonly value: string; readonly wide?: boolean }) {
  return (
    <div className={props.wide ? "stat stat--wide" : "stat"}>
      <dt>{props.label}</dt>
      <dd>{props.value}</dd>
    </div>
  );
}

function StatRows(props: { readonly rows: readonly StatRow[] }) {
  return (
    <dl className="stats">
      {props.rows.map(row => (
        <Stat key={row.label} label={row.label} value={row.value} wide={row.wide} />
      ))}
    </dl>
  );
}

function SampleCardRows(props: { readonly rows: readonly DisplayValueRow[] }) {
  return (
    <div className="sample-card">
      {props.rows.map(row => (
        <div key={row.label}>
          <span className="sample-card__label">{row.label}</span>
          <strong>{row.value}</strong>
        </div>
      ))}
    </div>
  );
}

function EmptyState(props: { readonly message?: string }) {
  return <p className="empty-state">{props.message ?? "Waiting for debug data."}</p>;
}

function formatTimestamp(value: number | undefined): string {
  return value == null ? "n/a" : timeFormatter.format(value);
}

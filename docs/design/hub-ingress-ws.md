# Hub Ingress WS

## Purpose

This document defines the Java-side boundary between the Minecraft mod and the local TypeScript hub.

The boundary is intentionally narrow:

`Minecraft mod -> one raw trace stream -> one local TypeScript hub ingress`

## Default Endpoint

The initial local websocket ingress endpoint is:

`ws://127.0.0.1:8787/ws`

The mod may override that endpoint with:

1. `airi.hub.ingress.ws.uri`
2. `airi.transport.ws.uri` as a legacy fallback
3. the default above when neither property is present

## Message Contract

One `ObservationSample` becomes one raw websocket message.

The initial/current-state raw trace message shape is:

- `v`
- `kind = "observation.sample"`
- `sessionId`
- `seq`
- `capturedAtMillis`
- `payload.worldTick`
- `payload.fps`
- `payload.dimensionKey`
- `payload.x`
- `payload.y`
- `payload.z`
- `payload.vx`
- `payload.vy`
- `payload.vz`
- `payload.targetDescription`

The Java transport should not batch multiple samples into one message or derive higher-level semantics inside the transport adapter.

## Current Implementation Shape

The current in-repo implementation keeps the boundary split across five packages/apps:

- `packages/hub-runtime` owns the initial/current-state typed raw ingress contract, minimal hand-written contract gating, and the minimal runtime snapshot proving ingestion
- `packages/hub-ingress-ws` owns websocket listener lifecycle, text-frame receive, plain JSON decode, transport counters, and handoff into the composed trace sink
- `packages/hub-trace-store` owns bounded in-memory trace retention and recent-trace queries
- `packages/hub-debug-surface` owns the read-only debug HTTP/SSE surface for local inspection
- `apps/local-hub` owns process composition, startup, shutdown, defaults, and the stable local bind targets

`apps/local-hub-ui` is a separate Vite web app that consumes only the debug surface.

The Node-side websocket server uses `crossws` with `h3` so the ingress stack stays aligned with the upstream Node websocket family used in `../airi`.

## What Stays In The Mod

The Minecraft mod owns:

- capture of local gameplay facts
- shaping one `ObservationSample` into one raw trace event message
- publishing that message to one local websocket ingress endpoint
- minimal local debug visibility for ingress health

## What Belongs To The TypeScript Hub

The TypeScript hub owns:

- websocket ingress handling
- websocket server lifecycle on the local bind target
- plain JSON message decode and minimal structural validation
- internal fanout and routing
- runtime snapshot evolution
- bounded trace retention
- read-only debug surface exposure
- detector and scorer execution later
- episode lifecycle logic later
- AIRI bridge logic and downstream protocol mapping
- richer observability and downstream composition

## Explicit Non-Goals

This ingress boundary does not include:

- detector, scorer, or episode logic in Java transport
- AIRI bridge logic in Java transport
- annotation write APIs or annotation persistence
- AIRI host envelopes, auth, announce, heartbeat, or local ingress framing that mimics that protocol
- queue/backoff orchestration as a first-class Java transport subsystem

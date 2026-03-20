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

The initial raw trace message shape is:

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

## What Stays In The Mod

The Minecraft mod owns:

- capture of local gameplay facts
- shaping one `ObservationSample` into one raw trace event message
- publishing that message to one local websocket ingress endpoint
- minimal local debug visibility for ingress health

## What Belongs To The TypeScript Hub

The TypeScript hub owns:

- websocket ingress handling
- internal fanout and routing
- projection rebuilding
- detector and scorer execution
- episode lifecycle logic
- AIRI bridge logic and downstream protocol mapping
- richer observability and downstream composition

## Explicit Non-Goals

This ingress boundary does not include:

- detector, scorer, or episode logic in Java transport
- AIRI bridge logic in Java transport
- AIRI host envelopes, auth, announce, heartbeat, or local ingress framing that mimics that protocol
- queue/backoff orchestration as a first-class Java transport subsystem

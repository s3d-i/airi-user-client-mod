# Current Implementation State

## Purpose

This document records what `dev/refactor-node` implements today.

Read this alongside the design docs in [`../design/`](../design/README.md). The design docs describe the intended system shape; this document describes current implementation reality.

## Repository Shape

Today the repository is:

- one Gradle/Fabric project
- one Minecraft target: `1.21.1`
- one Java toolchain target: `21`
- one mod artifact

Today the repository does not contain:

- a `package.json`
- a Node or pnpm workspace
- a local TypeScript websocket hub
- a shared core module split out from version-specific Fabric adapters
- a multi-version release layout

## How To Run It

Run the current experiment with:

```sh
./gradlew runClient
```

To forward websocket transport and console-exported transport metrics into the client runtime:

```sh
./gradlew runClient \
  -Dairi.transport.ws.uri=ws://127.0.0.1:8787/ws \
  -Dairi.otel.enabled=true \
  -Dairi.otel.metrics.exporter=console \
  -Dairi.otel.metrics.export.interval.millis=5000
```

Once the client is in a world, press `F3`. The left debug panel should show an `[AIRI] observation emit` block with the latest sampled values and transport state.

## Implemented Pieces

### 1. Client Capture Loop

The client mod samples a small observation payload on a bounded tick cadence.

That payload currently includes coarse movement and target-facing data such as:

- position
- velocity
- dimension
- fps
- target description

This is still a minimal sampling experiment, not the fuller event taxonomy described in [`../design/capture-event-taxonomy.md`](../design/capture-event-taxonomy.md).

### 2. Local Debug Surface

`dev/refactor-node` already has a useful local inspection path:

- recent observations are stored in-memory
- the latest state is rendered into the vanilla debug HUD

This is the most concrete feedback surface currently implemented in-repo.

### 3. Java-Side Websocket Transport

The Fabric client currently owns websocket publishing directly.

That Java transport layer currently handles:

- endpoint resolution
- connection lifecycle
- reconnect backoff
- bounded queueing
- payload serialization
- send latency and failure tracking

This is the main implementation gap relative to the transport boundary described in [`../design/transport-hub.md`](../design/transport-hub.md).

### 4. Transport Observability

`dev/refactor-node` also includes transport-focused runtime visibility:

- local transport status state for debug display
- OTel transport metrics for queue depth, reconnects, drops, send latency, and failures

That observability is real and useful, but it is still scoped to runtime transport health rather than gameplay semantics.

## What Is Missing

The following architecture pieces are not implemented in `dev/refactor-node` yet:

- a local TypeScript trace hub in this repository
- blackboard materialization behind that hub
- detector and scorer execution behind that hub
- stable behavior episode publication
- replay artifacts and replay-driven debugging workflow
- shared core plus per-version adapter module split
- simultaneous multi-version release flow

## Practical Reading Rule

When a doc says `should` or `target`, treat it as design direction rather than a statement about current implementation.

When you need to understand the code in `dev/refactor-node`, assume:

- `dev/refactor-node` is single-version
- `dev/refactor-node` is Java-first
- `dev/refactor-node` is still using a direct websocket client in the mod
- `dev/refactor-node` is a capture and transport prototype, not the final repository shape

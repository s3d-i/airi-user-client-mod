# Current Implementation State

## Purpose

This document records what `dev/refactor-node` implements today.

Read this alongside the design docs in [`../design/`](../design/README.md). The design docs describe the intended system shape; this document describes current implementation reality.

## Repository Shape

Today the repository is:

- one Gradle multi-project build
- one pnpm workspace rooted at the repository root
- one Minecraft target: `1.21.1`
- one Java toolchain target: `21`
- one thin shared Java module: `:core` in `core/`
- one real version module today: `:v1_21_1` in `versions/1.21.1/`
- one mod artifact

Today the repository does not contain:

- a multi-version release layout

The root project is now an aggregator and shared-conventions layer.

`core/` is intentionally thin shared Java capture/trace infrastructure. It currently holds only shared observation contracts and does not contain detector logic, Minecraft/Fabric adapters, OTel wiring, or websocket transport state.

## How To Check It

For non-interactive validation such as CI or coding-agent checks, prefer static commands:

```sh
pnpm typecheck
./gradlew compileClientJava
```

If you need a broader Gradle validation pass, run:

```sh
./gradlew build
```

`./gradlew runClient` is not part of the default check flow. It launches an interactive Minecraft client and is intended for manual runtime validation.

## How To Run It Manually

Use three terminals for manual runtime validation.

In terminal 1, start the local TypeScript hub composition root first:

```sh
pnpm --filter @airi-client-mod/local-hub build
pnpm --filter @airi-client-mod/local-hub start
```

That process binds the default ingress and debug endpoints:

- `ws://127.0.0.1:8787/ws`
- `http://127.0.0.1:8788/api/debug`

In terminal 2, start the separate Vite UI:

```sh
pnpm --filter @airi-client-mod/local-hub-ui dev
```

Open `http://127.0.0.1:5174`.

In terminal 3, run the current Minecraft experiment with:

```sh
./gradlew runClient
```

To forward websocket transport and console-exported transport metrics into the client runtime:

```sh
./gradlew runClient \
  -Dairi.hub.ingress.ws.uri=ws://127.0.0.1:8787/ws \
  -Dairi.otel.enabled=true \
  -Dairi.otel.metrics.exporter=console \
  -Dairi.otel.metrics.export.interval.millis=5000
```

`airi.transport.ws.uri` still works as a legacy override. If both properties are present, `airi.hub.ingress.ws.uri` wins.

Once the client is in a world, press `F3`. The left debug panel should show an `[AIRI] observation emit` block with the latest sampled values and transport state.

While moving in-world and generating live samples, confirm the browser UI shows:

- `Ingress Status` accepted frame count increasing
- `Runtime Snapshot` updating
- `Recent Traces` filling with `observation.sample`
- `Logger Output` showing ingress/runtime/store activity

For a direct server-side read-only check, you can also query:

```sh
curl -s http://127.0.0.1:8788/api/debug/state
curl -s 'http://127.0.0.1:8788/api/debug/traces?limit=5'
curl -s 'http://127.0.0.1:8788/api/debug/logs?limit=10'
```

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

That transport code still lives in the version module under `versions/1.21.1/`. It has not been promoted into `core/`.

That Java transport layer is now a thin local-hub ingress adapter. It owns:

- endpoint resolution
- payload serialization
- one websocket target
- best-effort connection and send attempts
- minimal ingress status tracking

Each `ObservationSample` becomes one raw websocket message with `kind = "observation.sample"` and the sampled payload fields.

The Java transport no longer owns queueing, reconnect backoff, multi-consumer fanout, or AIRI host-protocol semantics.

### 4. Local TypeScript Hub Ingress

`dev/refactor-node` now also contains a real local TypeScript ingress process.

That path is composed as:

- `apps/local-hub` as the executable Node composition root
- `packages/hub-ingress-ws` as the websocket ingress server
- `packages/hub-runtime` as the initial/current-state typed runtime ingestion surface
- `packages/hub-trace-store` as the bounded in-memory retention boundary
- `packages/hub-debug-surface` as the read-only local inspection surface
- `apps/local-hub-ui` as the separate Vite debug UI

Today that TypeScript side owns:

- a websocket server on `ws://127.0.0.1:8787/ws`
- a read-only debug surface on `http://127.0.0.1:8788/api/debug`
- one-message-per-event plain JSON ingress
- minimal structural validation for `observation.sample`
- runtime ingestion into a minimal snapshot with `traceCount`, `latestObservation`, and `lastAcceptedAt`
- bounded in-memory trace retention behind explicit retained trace IDs
- structured logs fanned out to console and the debug surface
- a separate Vite UI that consumes only the debug surface

That TypeScript side still does not own detector execution, scorer execution, episode lifecycle logic, annotation writes, durable replay storage, or active AIRI bridge behavior.

### 5. Transport Observability

`dev/refactor-node` also includes transport-focused runtime visibility:

- local ingress status state for debug display
- OTel transport metrics for connect attempts, state changes, opens/closes, and send success/failure

That observability is real and useful, but it is still scoped to runtime transport health rather than gameplay semantics.

## What Is Missing

The following architecture pieces are not implemented in `dev/refactor-node` yet:

- blackboard materialization behind the new local hub ingress
- detector and scorer execution behind that hub
- stable behavior episode publication
- AIRI bridge publishing behavior and inbound event handling
- annotation write APIs and annotation persistence model
- replay artifacts beyond bounded in-memory trace retention and replay-driven debugging workflow
- broader shared-core extraction beyond the current thin capture/trace contracts
- simultaneous multi-version release flow

## Practical Reading Rule

When a doc says `should` or `target`, treat it as design direction rather than a statement about current implementation.

When you need to understand the code in `dev/refactor-node`, assume:

- `dev/refactor-node` is single-version
- `dev/refactor-node` is Java-first for the mod, with a pnpm workspace also present in-repo
- `dev/refactor-node` is still using a direct websocket client in the mod
- `dev/refactor-node` keeps the websocket transport in the Java version module for now
- `dev/refactor-node` now has a real local TypeScript hub ingress, trace store, debug surface, and Vite debug UI, but it is still only a transport/runtime/debug slice rather than the full reasoning pipeline

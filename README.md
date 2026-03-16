# airi-user-client-mod

`airi-user-client-mod` is the local gameplay-observation experiment around AIRI.

This branch is a capture-first Fabric mod prototype. It is useful as a concrete reference for what has already been implemented, but it is not yet the full multi-version, TypeScript-hub-centered architecture intended for main.

## Current Branch Status

Today this repository is:

- a single Gradle/Fabric project
- targeted at Minecraft `1.21.1`
- Java `21` only
- centered on a Fabric client mod experiment

Today this repository is not:

- a multi-version workspace
- split into shared core plus version-specific adapters
- a mixed Java and TypeScript monorepo
- backed by a local TypeScript websocket hub in-repo

The most important current branch fact is simple:

the Java client mod still owns capture, local debug output, websocket transport, reconnect policy, queueing, and transport telemetry.

## What Works Today

The current experiment does four concrete things:

- samples a small observation payload every 10 client ticks
- stores recent samples for local inspection
- renders the latest observation state into the vanilla debug HUD
- optionally publishes those samples over websocket and exports transport OTel metrics

To try it:

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

## Docs

The docs are split between current branch reality and target architecture:

- [Current Branch State](./docs/dev/current-state.md)
- [Architecture](./docs/dev/architecture.md)
- [Transport Hub](./docs/dev/transport-hub.md)
- [Capture Event Taxonomy](./docs/dev/capture-event-taxonomy.md)
- [Guardrails](./docs/dev/guardrails.md)

Read `current-state.md` first if you want to understand the code that actually exists in this branch.

## Direction For Main

The intended main-branch direction is:

- add a local TypeScript trace hub to this repository
- move blackboard, scorer, episode, replay, and AIRI bridge logic behind that hub boundary
- keep the Minecraft mod focused on evidence capture and trace publish
- split Java into a shared version-neutral core plus thin per-version Fabric adapters
- support coordinated releases for multiple Minecraft versions from one repository

# AGENTS.md

## Purpose

This file is for coding agents and automated contributors working in this repository.

Use static, non-interactive checks by default. Do not treat `./gradlew runClient` as the standard verification step.

## Default Check Flow

For agent-driven validation, prefer the smallest relevant non-interactive command set:

```sh
pnpm typecheck
./gradlew compileClientJava
```

Use `./gradlew build` when you need a broader Gradle validation pass.

`./gradlew runClient` launches an interactive Minecraft client. It blocks automation and is not appropriate as the default "check" command for Codex-style agents, CI, or sandboxed verification flows.

## Manual Runtime Validation

Use `./gradlew runClient` only when you explicitly need in-game runtime behavior, for example:

- verifying the debug HUD output
- checking live websocket transport behavior
- confirming behavior after joining a world

To forward websocket transport and console-exported transport metrics into the client runtime:

```sh
./gradlew runClient \
  -Dairi.transport.ws.uri=ws://127.0.0.1:8787/ws \
  -Dairi.otel.enabled=true \
  -Dairi.otel.metrics.exporter=console \
  -Dairi.otel.metrics.export.interval.millis=5000
```

Once the client is in a world, press `F3`. The left debug panel should show an `[AIRI] observation emit` block with the latest sampled values and transport state.

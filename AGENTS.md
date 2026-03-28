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
- checking the local debug surface and Vite UI against live traces

Agents should not execute manual runtime validations

## Coding style

Prefer minimal changes each step instead of batch/massive rewrite.

## Extra notive

You're in a refactor, rewriting mc client side trace event and canonical raw trace.

Neglect all docs under docs/

Do not add docs either

Mutate the contract according to user prompt, and change all related shapes, run typecheck and compile check after changes.
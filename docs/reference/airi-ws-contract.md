---
title: AIRI WS Contract Reference
topic: airi websocket contract for local hub bridge work
date: 2026-03-20
context:
  consumer: packages/airi-ws-bridge
  current_repo:
    name: airi-user-client-mod
    path: /home/l/airi-user-client-mod
    branch: dev/refactor-node
    commit: 5e434dc87b11fc0d25c1001c85998cd8da69b3b1
    worktree_status: clean
  compared_repo:
    name: "@moeru-ai/airi"
    path: /home/l/airi
    branch: main
    commit: 311d150d868ba5444e093b7e5b80968f2bee5e1d
    worktree_status: clean
  method:
    - local code inspection
    - multi-agent repository research
status: reference
---

# AIRI WS Contract Reference

## Purpose

This document records the AIRI websocket contract details that matter for implementing [`packages/airi-ws-bridge`](../../packages/airi-ws-bridge/src/index.ts).

The goal is not to mirror all of `@moeru-ai/airi`. The goal is to pin the specific host protocol, client behavior, and Minecraft-service usage that affect bridge design in this repository.

## Scope

This reference covers:

- the AIRI main-process websocket host contract
- the shared websocket envelope and protocol event families
- how `services/minecraft` uses that contract today
- the implications for translating local hub outputs into AIRI messages

This reference does not define the final event mapping for `EpisodeOutput`. That remains an implementation choice in this repository.

## Canonical Upstream Files

- [`packages/server-sdk/src/client.ts`](/home/l/airi/packages/server-sdk/src/client.ts)
- [`packages/server-runtime/src/index.ts`](/home/l/airi/packages/server-runtime/src/index.ts)
- [`packages/server-shared/src/types/websocket/events.ts`](/home/l/airi/packages/server-shared/src/types/websocket/events.ts)
- [`packages/plugin-protocol/src/types/events.ts`](/home/l/airi/packages/plugin-protocol/src/types/events.ts)
- [`services/minecraft/src/main.ts`](/home/l/airi/services/minecraft/src/main.ts)
- [`services/minecraft/src/composables/config.ts`](/home/l/airi/services/minecraft/src/composables/config.ts)

## Main Findings

### 1. AIRI exposes a generic event bus, not an episode-specific websocket contract

The host protocol is a typed websocket event bus with an envelope shaped like:

- `type`
- `data`
- optional `route`
- `metadata.source`
- `metadata.event.id`
- optional `metadata.event.parentId`

See [`events.ts#L5`](/home/l/airi/packages/server-shared/src/types/websocket/events.ts#L5).

This matters because the local bridge API in this repository is currently:

- `publishEpisode(output: EpisodeOutput): Promise<void>`

See [`index.ts#L3`](/home/l/airi-user-client-mod/packages/airi-ws-bridge/src/index.ts#L3).

There is no upstream AIRI websocket event family named around episodes. `packages/airi-ws-bridge` will need an explicit translation from local hub concepts into AIRI protocol events.

### 2. The server endpoint is `GET /ws`, with default local development address `ws://localhost:6121/ws`

The runtime registers its websocket handler on `/ws` in [`index.ts#L251`](/home/l/airi/packages/server-runtime/src/index.ts#L251).

The Minecraft service also defaults to `ws://localhost:6121/ws` through `AIRI_WS_BASEURL` in [`config.ts#L81`](/home/l/airi/services/minecraft/src/composables/config.ts#L81).

For local-hub work in this repository, that means the outbound AIRI bridge should be modeled as a websocket client to a single host endpoint, not as a custom embedded protocol.

### 3. The native client behavior lives in `@proj-airi/server-sdk`

The AIRI client implementation in [`client.ts#L15`](/home/l/airi/packages/server-sdk/src/client.ts#L15) provides:

- auto-connect by default
- auto-reconnect with exponential backoff
- heartbeat ping/pong support
- optional token-based authentication
- `send` and `onEvent` against typed protocol events

It serializes outbound messages with `superjson` in [`client.ts#L348`](/home/l/airi/packages/server-sdk/src/client.ts#L348).

The server parses with `superjson` first and falls back to plain JSON in [`index.ts#L266`](/home/l/airi/packages/server-runtime/src/index.ts#L266).

This is the strongest implementation signal for `packages/airi-ws-bridge`: if we want AIRI-compatible behavior, we should align with `server-sdk` transport semantics rather than inventing a separate client shape.

### 4. Handshake and registration are module-oriented

The important lifecycle is:

1. Connect to `/ws`.
2. If a token is configured, send `module:authenticate`.
3. Receive `module:authenticated`.
4. Send `module:announce` with module metadata.
5. Start normal event exchange.

Relevant files:

- auth and announce from client: [`client.ts#L226`](/home/l/airi/packages/server-sdk/src/client.ts#L226)
- auth handling on server: [`index.ts#L337`](/home/l/airi/packages/server-runtime/src/index.ts#L337)
- announce validation and broadcast: [`index.ts#L356`](/home/l/airi/packages/server-runtime/src/index.ts#L356)
- `module:announce` payload shape: [`events.ts#L533`](/home/l/airi/packages/plugin-protocol/src/types/events.ts#L533)

`module:announce` includes:

- `name`
- `identity`
- `possibleEvents`
- optional `configSchema`
- optional `dependencies`

That means the bridge implementation is not just a socket writer. It should decide what module identity it wants AIRI to see and which event families it claims to publish or consume.

### 5. Routing is event-driven, not channel-specific

The server broadcasts by default but can route by explicit destinations from either:

- `event.route.destinations`
- `event.data.destinations`

See [`index.ts#L486`](/home/l/airi/packages/server-runtime/src/index.ts#L486).

This is especially relevant for `spark:*` events, which are designed for directed inter-agent communication rather than raw telemetry.

### 6. Heartbeat is part of the contract

The client periodically sends `transport:connection:heartbeat` with default read timeout `30_000` ms in [`client.ts#L84`](/home/l/airi/packages/server-sdk/src/client.ts#L84) and [`client.ts#L408`](/home/l/airi/packages/server-sdk/src/client.ts#L408).

The server tracks liveness and emits health transitions in [`index.ts#L125`](/home/l/airi/packages/server-runtime/src/index.ts#L125) and [`index.ts#L312`](/home/l/airi/packages/server-runtime/src/index.ts#L312).

If we want a production-grade bridge package, connection lifecycle should include heartbeat awareness instead of treating the socket as fire-and-forget.

## Event Families That Matter Most For Bridge Design

The upstream protocol event families most likely to matter for local-hub integration are:

- `context:update`
- `spark:notify`
- `spark:emit`
- `spark:command`
- `output:gen-ai:chat:message`
- `output:gen-ai:chat:tool-call`
- `output:gen-ai:chat:complete`

See [`events.ts#L742`](/home/l/airi/packages/plugin-protocol/src/types/events.ts#L742) and [`events.ts#L888`](/home/l/airi/packages/plugin-protocol/src/types/events.ts#L888).

These are better candidates than inventing new opaque payloads because they already fit AIRI's host/runtime expectations.

## What `services/minecraft` Actually Does Today

### 1. It uses the generic AIRI client, but not much beyond connection bootstrap

The Minecraft service constructs:

```ts
const airiClient = new Client({
  name: config.airi.clientName,
  url: config.airi.wsBaseUrl,
})
```

See [`main.ts#L68`](/home/l/airi/services/minecraft/src/main.ts#L68).

That client is then passed into `CognitiveEngine`, but the current code keeps it effectively unused:

- [`cognitive/index.ts#L16`](/home/l/airi/services/minecraft/src/cognitive/index.ts#L16)

So there is no strong Minecraft-domain event mapping in `@moeru-ai/airi` that we should copy yet. The useful contract lives in the shared host protocol, not in existing Minecraft bridge logic.

### 2. The Minecraft debug websocket is a separate protocol

`services/minecraft` also has a debug dashboard websocket server, but it is not the AIRI host protocol.

Relevant files:

- [`debug/debug-service.ts#L44`](/home/l/airi/services/minecraft/src/debug/debug-service.ts#L44)
- [`debug/server.ts#L83`](/home/l/airi/services/minecraft/src/debug/server.ts#L83)

That path uses a local plain-JSON debug protocol and should not be treated as the AIRI integration contract for `packages/airi-ws-bridge`.

## Implications For `packages/airi-ws-bridge`

### Design constraints

- The bridge should translate from local hub semantics to AIRI `ProtocolEvents`.
- The bridge should not assume AIRI has a native concept matching local `EpisodeOutput`.
- The bridge should probably expose configuration for:
  - websocket URL
  - optional auth token
  - module name and identity
  - declared `possibleEvents`
- The bridge should align with AIRI's envelope and lifecycle semantics:
  - `superjson` compatibility
  - metadata population
  - reconnect behavior
  - heartbeat behavior

### Immediate implementation decision still needed

The unresolved design choice is:

- what AIRI event or event sequence should `publishEpisode(output)` emit

The best current candidates are:

- `context:update` if the goal is to enrich AIRI context state
- `spark:notify` or `spark:emit` if the goal is agent-to-agent signaling
- `output:gen-ai:chat:*` only if the local hub is acting like a chat-producing upstream

This repository should decide that mapping explicitly instead of hiding it inside a transport client.

## Open Questions

- Whether the Electron main-process deployment path always enables token auth, or whether local in-process usage usually runs unauthenticated.
- Whether `packages/airi-ws-bridge` should be a thin `server-sdk` wrapper or a slightly higher-level translator with its own mapping policy.
- Whether inbound AIRI events also need to be bridged back into local hub runtime types, or whether the first implementation is outbound-only.

## Practical Reading Rule

If `@moeru-ai/airi` changes in any of these files, especially `server-sdk`, `server-runtime`, `server-shared`, or `plugin-protocol`, re-check this reference before treating it as current.

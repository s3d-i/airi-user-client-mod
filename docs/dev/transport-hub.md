# Transport Hub

## Direction

The Minecraft client mod should publish a raw gameplay trace stream to one local websocket endpoint.

That endpoint should be a TypeScript hub, not a pile of downstream integrations embedded in the Java capture layer.

The practical shape is:

`Minecraft client mod -> local TypeScript websocket service -> blackboard/scorer/episode/observability -> AIRI protocol bridge -> AIRI desktop client and related services`

## Why This Boundary

The mod is good at capture.

It is not the right place to own:

- multi-consumer fanout
- blackboard materialization
- scorer execution
- episode lifecycle logic
- observability export wiring
- AIRI service-specific websocket contracts

Keeping those concerns in the hub preserves faster iteration on schemas and downstream reasoning without forcing repeated Java-side transport rewrites.

## Responsibilities

### Minecraft Client Mod

The mod should:

- capture high-fidelity local facts
- publish a replay-friendly trace stream
- expose local transport/debug health
- stay ignorant of downstream module topology

The mod should not:

- speak scorer or episode-specific protocols directly
- maintain multiple websocket clients for multiple consumers
- encode AIRI brain-specific semantics

### TypeScript Trace Hub

The hub should:

- accept one ingress websocket stream from the mod
- persist or relay the raw trace stream
- materialize blackboard-like projections from the trace
- run scorer and detector pipelines
- emit stable episode outputs
- expose OTel and other operational surfaces
- bridge selected derived state into AIRI's main service protocol stack

This is the right place to stay close to the TypeScript service protocol stack already present in `../airi`, especially `packages/plugin-protocol`, `packages/server-sdk`, and `packages/server-runtime`.

### AIRI Minecraft Brain Bridge

`../airi/services/minecraft` should be treated as a downstream consumer or bridge target of the hub, not as the direct first-hop transport peer of the Fabric mod.

That keeps the bot brain isolated from capture transport concerns and lets the hub decide what slice of raw trace, blackboard state, detector support, or episodes is worth forwarding.

The important alignment point is not a hypothetical Minecraft-specific hub service. It is AIRI's main service protocol stack. The bridge out of this repository should speak the same module/channel protocol shape that AIRI already uses elsewhere.

## Current Wire Shape

The current mod transport is moving toward this boundary by publishing two envelope kinds over websocket:

- `trace.session.start`
- `trace.event`

`trace.event` currently carries `observation.sample` payloads. The envelope is intentionally generic so more trace event kinds can be added without changing the transport role of the mod.

## Recommended Next Step

The next implementation step should be a dedicated local TypeScript websocket service in this repository that:

1. accepts the mod's trace websocket stream
2. republishes or routes it to internal consumers
3. adds blackboard/scorer/episode modules behind the hub boundary
4. exposes an outbound bridge aligned with AIRI's main service protocol stack

That outbound bridge should stay close to:

- `../airi/packages/plugin-protocol`
- `../airi/packages/server-sdk`
- `../airi/packages/server-runtime`

That is the path away from "Java mod with hand-written websocket logic" and toward a hub-centered system where Java only captures, local TypeScript composes, and AIRI receives only the stable downstream products it actually needs.

# Transport Hub

## Status In This Branch

This branch does not contain the local TypeScript hub yet.

Today the Java client mod still owns:

- the websocket client
- queueing
- reconnect policy
- payload serialization
- transport telemetry wiring

That is acceptable for this experiment branch, but it is not the long-term boundary.

## Target Boundary

The Minecraft client mod should publish one raw trace stream to one local websocket endpoint.

That endpoint should be a TypeScript hub inside this repository.

The target flow is:

`Minecraft client mod -> local TypeScript hub -> blackboard/scorer/episode/observability -> AIRI bridge`

## What Stays In The Minecraft Mod

The mod should own:

- capture of local gameplay facts
- shaping those facts into a replay-friendly raw trace stream
- a single ingress publish path
- local debug visibility for capture and transport health

The mod should not own:

- multi-consumer fanout
- blackboard materialization
- detector or scorer execution
- episode lifecycle logic
- AIRI service-specific bridge logic

## What Moves To The TypeScript Hub

The hub should own:

- websocket ingress from the mod
- internal fanout and routing
- projection rebuilding from the raw trace
- detector and scorer execution
- stable episode publication
- replay-oriented persistence or trace handling
- outbound bridge alignment with AIRI's service protocol stack
- composition of runtime observability surfaces

## Current Gap

The current branch already points toward this boundary, but it has not crossed it.

Right now the mod publishes a coarse websocket payload directly from Java. The next architectural step is not "add more downstream logic to Java transport." The next step is "move that responsibility behind the local TypeScript hub."

## Integration Direction

The outbound bridge from the hub should stay close to the AIRI protocol/runtime packages in `../airi`, especially:

- `packages/plugin-protocol`
- `packages/server-sdk`
- `packages/server-runtime`

That keeps the capture layer generic and lets the hub decide which raw trace, projection state, detector support, or episodes are worth forwarding.

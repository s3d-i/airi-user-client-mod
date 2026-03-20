# Architecture

## Purpose

This document describes the target architecture for `airi-user-client-mod`.

For the current implementation state, read [Current Implementation State](../dev/current-state.md).

## One-Sentence Shape

The target shape is:

`Minecraft client mod -> raw trace stream -> local TypeScript hub -> blackboard/scorer/episode pipeline -> AIRI bridge and debug surfaces`

## System Boundary

The Minecraft client mod should stay closest to capture.

The local hub should own the evolving local reasoning and downstream composition work.

The AIRI desktop client and related services should consume selected derived outputs rather than forcing the mod to speak every downstream protocol directly.

## What The Architecture Should Optimize For

- evidence fidelity
- bounded local reasoning
- semantic flexibility
- replayability
- inspectability
- real gameplay performance

## What The Architecture Should Avoid

- turning the Minecraft client mod into a local AIRI brain
- hiding semantics inside transport adapters
- coupling capture code to one downstream service topology
- mixing gameplay semantics with operational telemetry
- locking the repository into one Minecraft version forever

## Main Layers

### 1. Interaction Event Stream

This is the evidence foundation.

It should publish raw gameplay-facing facts such as movement samples, look-target changes, world interactions, UI transitions, and inventory changes without embedding intent labels.

### 2. Typed Projections

These are bounded materialized views rebuilt from the trace stream.

They hold reusable local evidence such as current tool state, UI state, recent dwell windows, focus continuity, and other short-horizon context that many detectors can share.

### 3. Detectors And Online Scoring

This layer runs bounded temporal reasoning over projections and short recent windows.

Its outputs are tentative local support values and transitions, not final truth.

### 4. Behavior Episodes

Episodes are the stable local semantic outputs.

They are time-bounded, human-labelable claims such as gathering, mining, fighting, organizing inventory, or traveling. They should emerge from temporal evidence rather than one-off triggers.

### 5. Transport Boundary

Transport moves trace and derived outputs outward, but it should not decide gameplay semantics.

Capture code should not know downstream fanout policy, retry policy, scorer topology, or AIRI-specific protocol structure.

### 6. Debug And Observability

The system should expose:

- raw observation visibility
- projection visibility
- detector and episode transition visibility
- replay-friendly debugging
- runtime health telemetry

Debugging and observability are part of the architecture, not optional extras.

## Version Strategy

The default long-term repository shape should support multiple Minecraft versions from one repository.

That means:

- one shared version-neutral core module
- one thin Fabric adapter module per supported version
- one coordinated release flow that produces one artifact per supported version

Put in shared core:

- domain event and trace types
- projection and detector logic that depends only on shared types
- replay-related logic
- small transport-independent interfaces

Keep version-specific:

- Loom coordinates and dependency versions
- mixins and entrypoints
- direct `net.minecraft.*` and Fabric API usage
- adapters that translate live game state into shared core types

## Domain Semantics Versus Runtime Telemetry

Keep these as two different planes:

- domain semantics: interaction events, projections, detector support, episodes
- runtime telemetry: queue depth, reconnects, drops, latency, exporter health

Operational telemetry may use OTel. Gameplay semantics should remain in the project's own domain model.

## Replay Requirement

The architecture should support replay from recorded traces.

That matters for:

- detector tuning
- regression testing
- debugging disagreement and ambiguity
- explaining why a local episode opened or closed

If a design makes replayability worse, that should be treated as architectural cost, not a minor inconvenience.

## Performance Direction

The architecture should prefer:

- bounded state
- compact rolling windows
- limited hot-path allocation
- shared projections reused by multiple detectors
- clear separation between hot capture paths and slower debug or export work

## Summary

The target center of gravity is:

- capture in the mod
- reasoning in the local hub
- stable outputs for AIRI
- replay and inspectability throughout

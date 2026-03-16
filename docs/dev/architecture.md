# Architecture

## Purpose

`airi-user-client-mod` exists to capture high-fidelity signals from the real Minecraft client session, feed them through a local telemetry and episode service, and turn them into reliable evidence and derived outputs for the AIRI desktop client.

The Minecraft client mod is the best place to observe moment-to-moment facts:

- what the player looked at
- what they broke, placed, used, opened, or attacked
- what tool they held
- what UI they entered
- what local world context surrounded the action

The Minecraft client mod is not the only place where meaning should live. The project should preserve room for the local telemetry and episode service and the AIRI desktop client to evolve interpretation over time.

## Conceptual, Not Contractual

The names in this document describe architectural roles, not fixed class names or frozen payload schemas.

Terms such as "interaction events," "blackboard projections," and "behavior episodes" describe the intended shape of the system. Their exact representation may evolve.

## Architecture In One Sentence

The intended shape is:

`interaction events -> typed blackboard projections -> temporal detectors and online scoring -> behavior episodes -> transport and debug surfaces`

## Core Intent

This project should optimize for:

- evidence fidelity
- low-latency streaming
- semantic flexibility
- replayability
- inspectability
- performance under real gameplay conditions

This project should avoid becoming:

- a full AIRI brain inside the Minecraft client
- a transport-only shim with no local context
- a large pile of ad hoc heuristics with no replay path
- a black box that cannot explain why it inferred something

## System Boundary

At a high level, the Minecraft client mod and the local telemetry and episode service should separate responsibilities like this:

1. The Minecraft client mod captures what the player actually did and saw.
2. The local telemetry and episode service ingests that stream and materializes compact local working state from it.
3. The local telemetry and episode service runs short-horizon temporal reasoning and produces stable episode-like outputs when the local evidence is strong enough.
4. The Minecraft client mod and the local telemetry and episode service expose enough debug and telemetry surface to make the system understandable.
5. The local telemetry and episode service forwards evidence and derived outputs to the AIRI desktop client.

Broader, slower, and more evolvable interpretation should remain outside the capture layer rather than being forced into the Minecraft client mod.

## Version Support

The repository can start single-version without committing to staying that way.

If it later supports multiple Minecraft versions such as `1.20.1` and `1.21.1`, the default direction is:

- monorepo
- one coordinated release that publishes one artifact per supported Minecraft version
- one shared version-neutral core module plus one thin Fabric adapter module per supported version

Put in shared core:

- domain event, sample, projection, detector, replay types, etc
- logic that only depends on shared domain types
- transport-independent shaping and small adapter interfaces

Keep per-version:

- Loom coordinates and dependency versions
- `fabric.mod.json` and similar metadata
- mixins, entrypoints, and direct `net.minecraft.*` or Fabric API usage
- adapters that translate live Minecraft state into shared core types

Do not let shared core depend on Yarn-named classes, mixin targets, or other version-locked game APIs.

## Main Layers

### 1. Interaction Event Stream

The event stream is the closest thing to local truth.

It should represent observable session facts such as:

- block breaks
- block places
- item uses
- attacks
- UI transitions
- look target changes
- movement and pose samples
- inventory transitions
- environment and context changes that are meaningful to later reasoning

This stream should remain close to observation. It should not be polluted with premature semantic conclusions.

### 2. Typed Blackboard Projections

The blackboard is a set of bounded, typed projections rebuilt from the interaction stream.

It is the online working state used by detectors and debug tools. It should hold:

- current player state
- current tool and held-item state
- current UI state
- current focus target state
- local spatial and environmental context
- recent rolling counters and dwell windows
- recent interaction continuity, such as streaks and short-term context

The blackboard is not the source of truth. The event stream is. The blackboard is a materialized view over that stream.

It should remain evidence-shaped. It should not quietly turn into a store of final intent or long unbounded history.

### 3. Temporal Detectors And Online Scoring

Temporal detectors should consume blackboard projections and short rolling windows.

They exist to answer short-horizon questions that benefit from local timing and local context, for example:

- is the player likely cutting wood right now?
- is this block breaking part of resource gathering, path clearing, or construction?
- is the player focused, interrupted, or idle?

These detectors should be:

- bounded in scope
- inspectable
- replayable
- tolerant of ambiguity

They should not pretend to be globally final truth.

Scores and candidate states in this layer are tentative support for hypotheses. They may change quickly as local evidence changes.

A detector score such as "wood gathering support = 0.82" is internal reasoning state, not yet a committed semantic output.

### 4. Behavior Episodes

Behavior episodes are the local telemetry and episode service's stable semantic outputs.

An episode is a committed interpretation over time, not a single-event label. More concretely, an episode is a time-bounded, human-labelable claim laid over a trace. It exists to smooth noisy evidence and give the AIRI desktop client a more stable picture of what is going on.

Episodes are not:

- raw interaction events
- blackboard facts or projections
- detector scores or candidate states

Examples include:

- wood gathering
- tunnel mining
- inventory organization
- combat engagement
- navigation or travel

Episodes should emerge from temporal evidence and hysteresis, not from one-off event triggers.

They are best understood as local commitments made under bounded evidence, not as the final global truth of what the player "really meant."

Saved traces should be able to carry episode annotations on top of the raw interaction trace. Those annotations may come from detector outputs or from manual labeling during replay analysis and detector tuning. In both cases, the raw trace remains the evidence foundation rather than being rewritten to contain semantics.

The exact replay artifact model, file layout, and label storage strategy are still intentionally underspecified in these docs.

The architecture does not require a single global current episode, a strict span tree, or a mandatory track taxonomy. Different episode kinds may overlap when their meanings can coexist. If some episode kinds are mutually exclusive, that constraint should be defined locally by those kinds rather than assumed as a global rule.

What makes an episode worth publishing downstream is that it is sparse, stable, and explainable. The exact export representation and transport contract are intentionally left out of scope here.

### 5. Transport

Transport moves evidence out of the Minecraft client mod, through the local telemetry and episode service, and into the AIRI desktop client and related debug surfaces.

Transport should be reliable and observable, but it should stay separate from capture and reasoning.

The protocol and integration details can evolve. The main architectural principle is stable:

capture code should not know transport policy, retry policy, or downstream semantics.

### 6. Debug And Observability Surfaces

Debugging and observability are first-class, not optional extras.

The project should support:

- a local debug surface for inspecting what the Minecraft client mod captured
- visibility into blackboard state
- visibility into detector scores and episode transitions
- replay-driven debugging
- system-level telemetry for runtime health

The debug surface should make both domain behavior and runtime health inspectable without forcing contributors to read code just to understand a transition.

The local debug surface should help answer:

- what was observed?
- what local context was materialized?
- which detectors changed?
- why did an episode open or close?
- did the transport path succeed, delay, drop, or back up?

## Data Semantics

The project's core semantics are not request/response or span-shaped business operations.

The core semantics are closer to:

- player interaction trace
- event stream
- local context projections
- behavior episodes

System-level telemetry still matters, but it should remain a different plane from gameplay semantics.

That distinction matters because gameplay meaning is often ambiguous, overlapping, and revisable in ways that classic span trees are not.

## Two Different Telemetry Planes

The system should treat these as distinct:

### Domain Semantics Plane

This is where gameplay-facing meaning lives:

- interaction events
- blackboard projections
- detector scores and transitions
- behavior episodes

This plane should be replayable and contributor-friendly.

### System Observability Plane

This is where operational health lives:

- queue depth
- event drops
- reconnects
- flush latency
- detector loop timing
- exporter health

This plane may use OTel and related tooling, but it should not replace the project's domain model.

## Why Blackboard Instead Of Only Raw Events

Raw events are necessary but not sufficient.

Many useful local facts are stateful or temporal:

- whether the player currently holds an axe
- whether the player is underground
- whether a chest screen has been open for the last second
- whether the player has been looking at logs for a sustained dwell period

These are best expressed as typed projections and rolling windows rather than by forcing every detector to rescan the raw event stream.

## Why Episodes Instead Of Instant Labels

Gameplay is noisy and ambiguous.

A single block break usually does not reveal enough meaning by itself. Meaning often appears only after nearby context and short recent history are taken into account.

Episodes provide:

- temporal stability
- more explainable behavior outputs
- less jitter
- a cleaner handoff to the AIRI desktop client

## Replayability As A Design Requirement

The architecture should support deterministic replay from recorded interaction traces.

That matters because:

- detector tuning should not require live Minecraft every time
- regressions should be testable offline
- contributors should be able to inspect failures after the fact
- design debates should be grounded in recorded evidence instead of memory

If a piece of logic cannot be understood or tested through replay, it should be treated with suspicion.

When a proposed design makes replayability or inspectability worse, that should be treated as architectural pressure, not as a minor testing inconvenience.

## Performance Direction

Performance is part of the architecture, not a later optimization pass.

The intended direction is:

- bounded state
- limited allocations on hot paths
- shared projections reused by multiple detectors
- compact temporal windows
- clear separation between hot local reasoning and slower debug or export work

The project should prefer designs that keep the Minecraft client mod predictable during real gameplay over designs that are elegant on paper but allocation-heavy in practice.

## Design Summary

Contributors should think about the project like this:

- the event stream is the evidence foundation
- the blackboard is the local working state
- detectors express short-horizon reasoning
- episodes are stable local semantic outputs
- transport moves outputs outward
- debug and telemetry keep the system inspectable

That is the architectural center of gravity for this repository.

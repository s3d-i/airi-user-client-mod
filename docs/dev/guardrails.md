# Guardrails

## Purpose

This document captures the golden contribution rules for `airi-user-client-mod`.

It is meant to keep the project aligned with its design intent even as implementation details evolve.

## Golden Direction

Build the local gameplay-observation pipeline as a high-fidelity evidence system with bounded local reasoning and strong inspectability.

Do not build it as a monolithic local AIRI behavior brain.

Contributors should bias toward evidence, replayability, and bounded local reasoning over clever but opaque inference.

## What To Do

### Keep Raw Observation Close To Raw Observation

Prefer event designs that stay close to what the Minecraft client mod actually observed.

Good direction:

- "the player broke a spruce log"
- "the current look target is a log"
- "the main hand currently holds an axe"
- "the chest screen has been open for 700 ms"

### Put Shared Local Evidence In Typed Projections

Materialize reusable local state that many detectors can depend on:

- current tool state
- current UI state
- recent dwell timers
- recent break counts
- local nearby block density
- local continuity such as streaks and short recent context

Shared projections should read like evidence, not like verdicts.

### Keep Detectors Temporal, Local, And Explainable

Detectors should express short-horizon reasoning that benefits from local timing and context.

They should be easy to inspect in a replay or debug surface.

Prefer detector logic that can be explained by shared evidence and visible transitions over logic that depends on implicit, scattered game-state coupling.

### Use Episodes As Stable Outputs

Prefer opening and closing time-bounded behavior episodes over emitting large numbers of unstable instant labels.

Let the local telemetry and episode service commit to a stable local reading only when the evidence has enough temporal support.

Reserve episodes for stable, human-labelable behavior claims over an interval.

Use blackboard projections for evidence-shaped facts and detector scores for tentative support. Do not promote every timed fact or every scorer output into an episode just because it spans time.

Saved traces should support episode annotations layered on top of raw traces, including manual labels used for replay analysis and detector tuning.

### Treat Replay As First-Class

Add logic that can be replayed, inspected, and tuned offline.

Recorded traces should be a normal way to understand and improve the system.

### Keep OTel And System Telemetry In Their Place

Use system telemetry to understand runtime health:

- queue pressure
- drops
- reconnects
- latency
- detector timing

Keep gameplay semantics in the project's own domain model.

Treat OTel as a runtime observability tool, not as the main vocabulary for player behavior.

### Design For Real Gameplay Performance

Choose designs that respect:

- hot-path cost
- bounded memory
- allocation discipline
- clear ownership of state

Assume the mod will run during normal play, not in a synthetic benchmark world.

## What Not To Do

### Do Not Treat The Minecraft Client Mod Or Local Telemetry Service As The Final Source Of Meaning

The Minecraft client mod and the local telemetry and episode service are close to evidence, not necessarily closest to final semantics.

Do not hard-code the assumption that all interpretation must terminate inside this repository's local pipeline.

### Do Not Turn The Blackboard Into A Dumping Ground

The blackboard should not become:

- a string-keyed bag of arbitrary state
- a store of long unbounded history
- a place to stash full Minecraft objects indefinitely
- a place to write speculative intent as if it were fact

If a blackboard entry reads like "the player intends to gather wood," it is probably in the wrong layer.

### Do Not Hide Meaning Inside Transport

Transport code should not secretly decide gameplay semantics.

Transport exists to move data reliably, not to infer behavior.

### Do Not Skip The Middle Layer

Avoid designs that jump directly from raw event to strong semantic label with no reusable context projection and no temporal smoothing.

That path usually becomes brittle and hard to debug.

The right intermediate layer is shared local evidence, not scattered detector-private guesses.

### Do Not Blur Domain Semantics With Operational Telemetry

Gameplay events and behavior episodes are not the same thing as spans, retries, exporters, or queue metrics.

Keep those concerns distinct.

### Do Not Use Episodes As A Second Blackboard

An episode should not be used for:

- currently observed state such as held tool or open UI
- rolling counters, dwell timers, or other reusable evidence projections
- tentative detector support values
- arbitrary intervalized facts that are not publishable behavior claims

If a label is not something a human could reasonably annotate on replay as a behavior interval, it probably does not belong in the episode layer.

### Do Not Force A Premature Episode Taxonomy

The architecture does not require every episode kind to fit into a universal track system or a single strict hierarchy.

Only add extra categorization when it improves replay, debugging, or transport clarity. Avoid taxonomy that looks elegant on paper but does not help contributors explain or tune behavior.

### Do Not Add Opaque Heuristics Without Replay Visibility

If a new rule cannot be understood through replay, local debugging, or score inspection, it probably does not belong yet.

Detector logic should be debuggable by evidence and transition history, not by intuition alone.

### Do Not Optimize Only For The Happy Path

Design for:

- intermittent transport failure
- noisy gameplay
- partial ambiguity
- detector disagreement
- state transitions that are not cleanly nested

## Good Architectural Smells

These are signs the project is moving in the right direction:

- a detector reads shared blackboard state instead of rescanning ad hoc logic everywhere
- a replay can explain why a behavior episode opened
- a contributor can inspect a score transition without guessing
- the protocol can change without rewriting capture logic
- local reasoning stays bounded and purposeful

## Bad Architectural Smells

These are warning signs:

- a growing number of one-off semantic flags attached directly to raw events
- large amounts of meaning hidden in transport adapters
- detector logic that depends on scattered Minecraft objects instead of typed projections
- inability to replay or explain a classification decision
- blackboard entries that read like final intent rather than observed or derived evidence
- features that require live manual testing because there is no replay path

## Final Rule

When in doubt, prefer the design that is:

- closer to observable evidence
- easier to replay
- easier to inspect
- easier to evolve
- less likely to lock the project into premature semantics

That bias will keep the repository aligned with its purpose.

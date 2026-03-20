# Guardrails

## Purpose

These are the contribution rules that should remain stable even while implementation details change.

## Keep Moving Toward

- raw capture that stays close to observable gameplay facts
- typed local projections that hold reusable evidence instead of verdicts
- detector logic that is temporal, bounded, and explainable
- sparse, stable episodes instead of jittery instant labels
- replay-driven debugging and tuning
- bounded hot-path cost during real gameplay

## Avoid

- turning the mod or local pipeline into a full AIRI behavior brain
- hiding gameplay semantics inside transport code
- writing speculative intent into raw events or blackboard state
- using episodes as a second blackboard
- blurring gameplay semantics with OTel and runtime telemetry
- coupling capture logic to one downstream websocket contract
- adding logic that cannot be explained through replay or visible state transitions

## Layering Rules

- Raw events should describe what was observed.
- Projections should describe reusable local evidence.
- Detector outputs should describe tentative support and local transitions.
- Episodes should describe stable, human-labelable behavior intervals.
- Operational telemetry should describe runtime health, not gameplay meaning.

If a change does not fit cleanly into one of those layers, stop and fix the design before adding more code.

## Review Checklist

Before adding a feature, ask:

1. Is this closer to evidence or to a verdict?
2. Can a contributor replay it, inspect it, and explain it after the fact?
3. Does it belong in capture, projection, detector, episode, or bridge code?
4. If it belongs in shared core, is it free of version-locked Minecraft APIs?
5. Does it preserve bounded cost during ordinary gameplay?

## Final Bias

When in doubt, prefer the design that is:

- closer to observed evidence
- easier to replay
- easier to inspect
- easier to evolve
- less coupled to one runtime topology

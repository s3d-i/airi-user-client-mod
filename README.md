# airi-user-client-mod

airi-user-client-mod is a client-side Fabric mod that captures high-fidelity player context from the real user session and forwards structured signals to Airi Hub for downstream behavior understanding, intent recognition, memory, and companion interaction.

Unlike a pure bot-side integration, this project is designed around a simple observation: the user client sees the world earlier, more directly, and with less ambiguity than an external agent ever can.

That makes it a better place to collect the raw signals needed to answer questions like:
- What is the player doing right now?
- Are they mining, navigating, building, fighting, looting, or just looking around?
- Is a block break part of path clearing, resource gathering, or construction?
- Is the player focused, idle, interrupted, under attack, or transitioning between goals?

This mod does not try to solve all semantics inside Minecraft itself. Instead, it provides a reliable, evolvable bridge from raw user-side events to higher-level reasoning in Airi.

## Project goals
1. High-fidelity event streaming

Provide a low-latency, structured, reliable stream of user-side gameplay signals.

2. Better activity / intent inference

Enable Airi Hub to distinguish between superficially similar actions with different meanings.

Examples:

- breaking a log while gathering wood

- breaking a block to clear path obstruction

- breaking a block as part of building replacement

- opening inventory to craft vs reorganize vs inspect resources

3. Preserve semantic flexibility

Keep the mod focused on evidence collection and light local interpretation, while allowing most semantics to evolve on the Hub side.

## Roadmap
### Phase 1 — Minimal Reliable signal bridge
- Fabric mod bootstrap
- basic event capture
- session model & transport protocol
- instrumentation
### Phase 2 — Structured activity evidence
- derived features
- event filtering model
- context propagation model
### Phase 3 - Hub-side behavior understanding
- temporal aggregation model
- online scorer & hysteresis model
- iterate & evolve

## Open questions

This repository exists partly because these questions are interesting and still not fully solved:

Where should semantics live: client, Hub, or both?

What is the minimal raw event set that still supports useful inference?

How much local feature extraction is worth the complexity?

Which events need exact fidelity, and which can be sampled?

How should inferred activities be represented: rules, scores, sequence models, hybrids?

How do we distinguish intention from mere motion?

How do we keep the system inspectable as it becomes smarter?
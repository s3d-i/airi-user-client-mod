# airi-user-client-mod

airi-user-client-mod is the local gameplay-observation pipeline around AIRI. Its intended runtime includes a Minecraft client mod for high-fidelity capture, a local telemetry and episode service for ingestion and derived outputs, and the AIRI desktop client as the downstream consumer.

Unlike a pure bot-side integration, this project is designed around a simple observation: the Minecraft client mod sees the world earlier, more directly, and with less ambiguity than an external agent ever can.

That makes it a better place to collect the raw signals needed to answer questions like:
- What is the player doing right now?
- Are they mining, navigating, building, fighting, looting, or just looking around?
- Is a block break part of path clearing, resource gathering, or construction?
- Is the player focused, idle, interrupted, under attack, or transitioning between goals?

This repository does not try to solve all semantics inside the Minecraft client mod itself. Instead, it provides a reliable, evolvable bridge from raw user-side events to higher-level reasoning in the local telemetry and episode service and the AIRI desktop client.

## Current experiment

This repository now includes a minimal Fabric client mod experiment targeting Minecraft `1.21.1`.

Current stack:

- Gradle `9.2.1`
- Fabric Loom `1.15.4`
- Fabric Loader `0.18.4`
- Yarn mappings `1.21.1+build.3`
- Fabric API `0.116.9+1.21.1`
- Java `21`

What it does right now:

- samples a small set of client-side observation data every 10 client ticks
- emits those samples into a tiny bounded in-memory store
- appends the latest emitted values directly into the vanilla debug HUD panel

To try it:

```sh
./gradlew runClient
```

Once the client is in a world, press `F3`. You should see an `[AIRI] observation emit` block in the left debug panel with the latest sampled position, velocity, dimension, target, FPS, and buffer state.

## Project goals
1. High-fidelity event streaming

Provide a low-latency, structured, reliable stream of user-side gameplay signals.

2. Better activity / intent inference

Enable the local telemetry and episode service and the AIRI desktop client to distinguish between superficially similar actions with different meanings.

Examples:

- breaking a log while gathering wood

- breaking a block to clear path obstruction

- breaking a block as part of building replacement

- opening inventory to craft vs reorganize vs inspect resources

3. Preserve semantic flexibility

Keep the Minecraft client mod focused on evidence collection and light local interpretation, while allowing broader semantics to evolve in the local telemetry and episode service and the AIRI desktop client.

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
### Phase 3 - Temporal behavior understanding
- temporal aggregation model
- online scorer & hysteresis model
- iterate & evolve

## Open questions

This repository exists partly because these questions are interesting and still not fully solved:

Where should semantics live: Minecraft client mod, local telemetry and episode service, AIRI desktop client, or some combination?

What is the minimal raw event set that still supports useful inference?

How much local feature extraction is worth the complexity?

Which events need exact fidelity, and which can be sampled?

How should inferred activities be represented: rules, scores, sequence models, hybrids?

How do we distinguish intention from mere motion?

How do we keep the system inspectable as it becomes smarter?

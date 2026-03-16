# Developer Guidance

This directory contains the high-level design guidance for `airi-user-client-mod`.

These documents are intentionally about design intent, boundaries, and contribution direction. They are not detailed contracts, wire-format specifications, or low-level implementation notes.

They should be read as architectural guidance and contribution norms.

The main idea to keep in mind is simple:

`airi-user-client-mod` is the local gameplay-observation pipeline around AIRI. It includes a Minecraft client mod for high-fidelity capture and a local telemetry and episode service for downstream ingestion and derived outputs. It is not the full AIRI behavior engine.

## Documents

- [Architecture](./architecture.md)
  Explains the intended runtime shape of the project, the major layers, and where different kinds of logic should live.
- [Guardrails](./guardrails.md)
  Captures the golden "what to do" and "what not to do" rules for contributors.
- [Transport Hub](./transport-hub.md)
  Explains why the mod should publish a trace stream into a local TypeScript websocket service instead of owning downstream fanout and service integration itself.
- [Core Capture Event Taxonomy](./capture-event-taxonomy.md)
  Defines the working raw event family list the client mod should grow toward for gameplay-facing evidence capture.

If you only read two documents before making architectural changes, read `architecture.md` first and `guardrails.md` second.

## Intended Audience

These docs are for contributors who need to answer questions such as:

- What kind of project is this mod supposed to become?
- What belongs in the Minecraft client mod, what belongs in the local telemetry and episode service, and what should remain in the AIRI desktop client?
- How should event capture, blackboard state, detectors, episodes, transport, and debugging fit together?
- What kinds of contributions move the project in the right direction, and what kinds create long-term confusion?

## Scope

These docs focus on:

- architecture direction
- reasoning boundaries
- semantics boundaries
- replay and inspectability expectations
- performance and observability intent

These docs do not try to define:

- an exact frozen payload schema for every event
- the final replay artifact model
- final protocol contracts
- the final episode export representation
- final package names
- exact implementation classes

Those details can evolve. The design intent in this directory should remain much more stable.

`capture-event-taxonomy.md` is the working exception: it defines the core raw event families the client should capture, but it still does not freeze the wire contract field by field.

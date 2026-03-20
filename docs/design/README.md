# Design Docs

This directory contains the target architecture and stable design constraints.

## Read Order

1. [Architecture](./architecture.md)
2. [Transport Hub](./transport-hub.md)
3. [Capture Event Taxonomy](./capture-event-taxonomy.md)
4. [Guardrails](./guardrails.md)

## What Each Doc Is For

- [Architecture](./architecture.md)
  Defines the intended system shape and major layers.

- [Transport Hub](./transport-hub.md)
  Defines the intended boundary between the Minecraft mod and the local TypeScript hub.

- [Capture Event Taxonomy](./capture-event-taxonomy.md)
  Defines the raw gameplay-facing event families the capture layer should grow toward.

- [Guardrails](./guardrails.md)
  Lists the stable contribution rules that should continue to hold while the implementation evolves.

## Scope

These docs are design guidance, not frozen contracts.

They should be precise about boundaries and responsibilities, but they do not need to lock every class name, payload field, or package layout ahead of implementation.

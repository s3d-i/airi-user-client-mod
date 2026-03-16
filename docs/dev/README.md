# Developer Docs

This directory contains the design guidance for `airi-user-client-mod`.

The docs are intentionally split into two kinds:

- current branch reality
- target architecture direction

That split matters because this branch is still a Java-first Fabric experiment, while the intended main-branch direction is a broader capture-plus-hub architecture.

## Read Order

Read these in this order:

1. [Current Branch State](./current-state.md)
2. [Architecture](./architecture.md)
3. [Transport Hub](./transport-hub.md)
4. [Capture Event Taxonomy](./capture-event-taxonomy.md)
5. [Guardrails](./guardrails.md)

## What Each Doc Is For

- [Current Branch State](./current-state.md)
  Describes what is actually implemented in this branch today.

- [Architecture](./architecture.md)
  Describes the target system shape and the major layers.

- [Transport Hub](./transport-hub.md)
  Defines the intended boundary between the Minecraft mod and the local TypeScript hub.

- [Capture Event Taxonomy](./capture-event-taxonomy.md)
  Defines the raw gameplay-facing event families the capture layer should grow toward.

- [Guardrails](./guardrails.md)
  Lists the contribution rules that keep the project aligned while implementation changes.

## Scope

These docs are guidance, not frozen contracts.

They should be precise about boundaries and responsibilities, but they do not attempt to freeze every class name, payload field, or package layout ahead of implementation.

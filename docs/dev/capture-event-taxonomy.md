# Core Capture Event Taxonomy

## Purpose

This document defines the working core taxonomy for raw gameplay-facing capture events emitted by the Minecraft client mod.

It exists to answer one question:

What facts should the mod publish into the trace stream before blackboard projections, detectors, and episodes are applied?

This is a design document, not a frozen wire contract. Event names, payload fields, batching choices, and transport details may evolve. The evidence boundary should stay stable.

## Scope

This taxonomy covers gameplay-facing raw capture events carried inside `trace.event`.

It does not define:

- transport control envelopes such as `trace.session.start`
- OTel and runtime health metrics
- blackboard projections
- detector scores
- behavior episodes

## Taxonomy Rules

- Prefer exact interaction and state-transition events over coarse periodic summaries.
- Keep events close to observation. Do not embed intent labels or long-window rollups.
- Use typed payloads. Avoid free-form strings when structured ids and coordinates are available.
- Keep hot-path capture bounded. Continuous state may be sampled; discrete interactions should usually be exact.
- Attach compact local context when it materially helps downstream inference.

## Common Event Shape

Each gameplay event should carry a common base such as:

- `seq`
- `capturedAtMillis`
- `worldTick`
- `dimensionKey`
- a typed `payload`

Depending on the event family, the payload may also include:

- player movement or pose state
- typed block or entity references
- hand, selected-slot, or held-item references
- current screen kind
- compact local context snapshot

All event names in this document refer to the domain event name inside the `trace.event` envelope, not to transport envelope kinds.

## Capture Modes

The core taxonomy uses three capture modes:

### Exact Interaction Events

Emit once per observed player interaction.

Use this mode for actions such as breaking, placing, using, attacking, or performing inventory interactions.

### Transition Events

Emit when a relevant state changes.

Use this mode for look target changes, selected-slot changes, held-item changes, or UI transitions.

### Sampled Continuous State

Emit at a bounded rate for movement, camera, or context state that changes continuously.

Use this mode where exact per-tick fidelity is less valuable than bounded cost and stable replay.

## Core Event Families

### 1. Continuous Player State

#### `player.motion.sample`

Capture mode: sampled continuous state

This event carries the bounded periodic snapshot of how the player is moving through the world.

It should capture facts such as:

- position
- velocity
- yaw and pitch
- pose
- on-ground state
- sprint, sneak, swim, glide, or similar movement-affecting booleans when they materially change interpretation

This event exists so downstream systems can reason about navigation, interruption, and movement continuity without forcing every detector to read live Minecraft state directly.

### 2. Focus And Tool State

#### `player.look.target.changed`

Capture mode: transition event

This event should fire when the crosshair target meaningfully changes.

It should capture facts such as:

- target kind: `block`, `entity`, `miss`, or `none`
- block id or entity type id
- block position and hit face when targeting a block
- stable entity reference when targeting an entity, if available
- hit distance and hit vector when available

This event exists so dwell windows and target continuity can be derived downstream from typed evidence instead of string parsing.

#### `player.selected_slot.changed`

Capture mode: transition event

This event should fire when the player's active hotbar slot changes.

It should capture:

- previous selected slot
- new selected slot

#### `player.hand_state.changed`

Capture mode: transition event

This event should fire when the player's main-hand or off-hand item state changes.

It should capture facts such as:

- main-hand item id
- main-hand count
- main-hand durability or damage when relevant
- off-hand item id
- off-hand count
- off-hand durability or damage when relevant

This event exists so downstream systems can distinguish axe, pickaxe, empty-hand, food, weapon, or block-placement contexts without re-reading opaque game state.

### 3. World Interactions

#### `interaction.block.break`

Capture mode: exact interaction event

This event should fire when the player breaks a block.

It should capture facts such as:

- block id
- block position
- hit face or direction when available
- current hand or held-item state
- compact local context snapshot if it is cheap and clearly useful

Do not attach semantic labels such as "resource gathering" or "path clearing" to this event.

#### `interaction.block.place`

Capture mode: exact interaction event

This event should fire when the player places a block.

It should capture facts such as:

- placed block id or source item id
- target position
- hit face
- replaced block id when meaningful
- hand used

#### `interaction.item.use`

Capture mode: exact interaction event

This event should fire when the player uses an item in a way that is locally observable.

It should capture facts such as:

- item id
- hand used
- target kind
- typed target reference
- local result or outcome if that fact is already observable at capture time

#### `interaction.entity.attack`

Capture mode: exact interaction event

This event should fire when the player attacks an entity.

It should capture facts such as:

- target entity type id
- stable target reference if locally available
- hand used or held-item state
- target distance when available

### 4. UI And Inventory

#### `ui.screen.transition`

Capture mode: transition event

This event should fire when the current screen changes.

It should capture facts such as:

- previous screen kind
- new screen kind
- whether this was effectively an open or close transition

This event exists because crafting, looting, organization, and inspection behavior are difficult to infer from world motion alone.

#### `inventory.transaction`

Capture mode: exact interaction event or bounded transition event at the UI boundary

This event should capture the observable inventory change produced by a local player action or a visible inventory diff.

It should capture facts such as:

- screen kind or container kind
- action type if locally observable
- changed slots as a bounded list
- per-slot before and after item deltas

If exact UI action type is not available, emit the slot deltas anyway. The raw trace should still describe what visibly changed.

### 5. Local Context

#### `context.local.snapshot`

Capture mode: sampled at low rate or attached to nearby interaction events

This event carries compact environmental facts that materially change how nearby interactions should be interpreted.

It may capture facts such as:

- light level
- sky visibility
- fluid or solid footing state
- nearby block category counts
- biome id or other compact environment classifier ids

Use this event sparingly. Prefer attaching compact context fields to nearby interaction events over emitting high-frequency standalone context spam.

## Recommended Minimum First Cut

The minimum first useful taxonomy should include:

- `player.motion.sample`
- `player.look.target.changed`
- `player.selected_slot.changed`
- `player.hand_state.changed`
- `interaction.block.break`
- `interaction.block.place`
- `interaction.item.use`
- `interaction.entity.attack`
- `ui.screen.transition`
- `inventory.transaction`

`context.local.snapshot` is useful, but it can start as an attached compact snapshot on nearby interaction events rather than as a fully separate high-rate stream.

## What Stays Out Of Raw Capture

The following do not belong in the raw client capture taxonomy:

- dwell timers
- streaks
- recent break counters
- "currently underground for 3 seconds" style rollups
- local activity scores
- labels such as "wood gathering", "building", or "inventory organization"
- episode open or close markers

Those belong in blackboard projections, detectors, or episode layers.

## Naming Guidance

- Use dot-separated, evidence-shaped names.
- Prefer `interaction.block.break` over overly generic names such as `action`.
- Prefer `...changed` or `...transition` for state changes.
- Prefer `...sample` only for bounded periodic snapshots.
- Add new event kinds only when they improve replay, debugging, or downstream projection quality.

## Relation To The Current Experiment

The current implementation is intentionally narrower than this taxonomy. Today the mod emits a coarse `observation.sample` payload with position, velocity, dimension, fps, and target description.

This document describes the target core capture taxonomy the project should grow toward as it moves from minimal sampling into high-fidelity evidence capture.

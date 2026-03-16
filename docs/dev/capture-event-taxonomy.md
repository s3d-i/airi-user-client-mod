# Capture Event Taxonomy

## Purpose

This document defines the target raw gameplay-facing event families emitted by the Minecraft client mod.

It is intentionally about evidence shape, not frozen wire details.

## Scope

This taxonomy covers gameplay-facing events carried inside `trace.event`.

It does not define:

- transport control envelopes such as `trace.session.start`
- OTel and runtime health metrics
- projections or blackboard state
- detector scores
- behavior episodes

## Event Design Rules

- Keep events close to local observation.
- Prefer typed fields over free-form strings.
- Emit exact events for discrete interactions when possible.
- Use sampling only for genuinely continuous state.
- Keep hot-path capture bounded.
- Attach compact context only when it materially helps downstream inference.

## Common Base Fields

Each event should carry a compact common base such as:

- `seq`
- `capturedAtMillis`
- `worldTick`
- `dimensionKey`
- typed `payload`

## Capture Modes

- exact interaction event: one event per observed player interaction
- transition event: one event when relevant state changes
- sampled state event: bounded periodic snapshots for continuous state

## Minimum First Cut

The first useful target event set is:

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

`context.local.snapshot` is useful, but it can start as compact context attached to nearby interaction events rather than as a standalone high-rate stream.

## Event Families

### Continuous Player State

#### `player.motion.sample`

Capture mode: sampled state event

Should include compact movement-facing facts such as:

- position
- velocity
- yaw and pitch
- pose
- on-ground state
- relevant movement booleans when they materially change interpretation

### Focus And Tool State

#### `player.look.target.changed`

Capture mode: transition event

Should describe a meaningful crosshair target change, including:

- target kind
- block id or entity type id when relevant
- block position and hit face for block targets
- stable entity reference when available

#### `player.selected_slot.changed`

Capture mode: transition event

Should include:

- previous selected slot
- new selected slot

#### `player.hand_state.changed`

Capture mode: transition event

Should include compact main-hand and off-hand state such as:

- item ids
- counts
- durability or damage when relevant

### World Interactions

#### `interaction.block.break`

Capture mode: exact interaction event

Should include:

- block id
- block position
- hit face when available
- current hand or held-item state

#### `interaction.block.place`

Capture mode: exact interaction event

Should include:

- placed block or source item id
- target position
- hit face
- replaced block id when meaningful
- hand used

#### `interaction.item.use`

Capture mode: exact interaction event

Should include:

- item id
- hand used
- target kind
- typed target reference
- locally observable result when already known at capture time

#### `interaction.entity.attack`

Capture mode: exact interaction event

Should include:

- target entity type id
- stable entity reference when available
- hand or held-item state
- target distance when available

### UI And Inventory

#### `ui.screen.transition`

Capture mode: transition event

Should include:

- previous screen kind
- new screen kind
- whether the change was effectively an open or close

#### `inventory.transaction`

Capture mode: exact interaction event or bounded transition event

Should include:

- screen or container kind
- action type when locally observable
- changed slots as a bounded list
- per-slot before and after item deltas

### Local Context

#### `context.local.snapshot`

Capture mode: low-rate sampled state event or attached compact context

May include:

- light level
- sky visibility
- footing or fluid state
- nearby block category counts
- biome or compact environment classifier ids

Use this sparingly. Prefer attached compact context over high-rate standalone context spam.

## What Stays Out Of Raw Capture

The following do not belong in raw client capture:

- dwell timers
- streaks and recent counters
- long-window rollups
- local activity scores
- labels such as `wood_gathering` or `building`
- episode open or close markers

Those belong in projection, detector, or episode layers.

## Naming Guidance

- use dot-separated evidence-shaped names
- reserve `...changed` or `...transition` for state changes
- reserve `...sample` for bounded periodic snapshots
- add new event kinds only when they improve replay, debugging, or downstream projection quality

## Relation To This Branch

The current implementation is intentionally much narrower than this taxonomy.

Today the branch emits a coarse `observation.sample` payload with movement, dimension, fps, and target description. This document describes the event model the project should grow toward once capture moves beyond the current experiment.

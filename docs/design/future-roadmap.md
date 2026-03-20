---
title: Future Roadmap
code_state_as_of_commit: ce9b05d593cac781dde3614a5bc69506987b6d8f
code_state_summary: >
  The current codebase still uses hand-written projection-to-detector logic and a
  fixed wood-gathering episode state machine.
---

# Future Roadmap

## Purpose

This document summarizes the intended evolution of the local reasoning stack beyond
the current hand-written detector and fixed episode implementation.

The goal is not to jump directly from raw traces to a large opaque model.
The goal is to build a replayable, inspectable, trainable pipeline that can improve
iteratively while remaining suitable for low-latency online inference.

## Target Shape

The intended long-term reasoning path is:

`trace -> causal temporal projection/featureization -> trainable scorer -> constrained intent hypotheses -> episode/span output -> agent consumption`

This keeps replay, inspectability, and bounded online cost at the center of the design.

## 1. Projection Becomes Temporal Featureization

The current projection layer should evolve from a small collection of hand-written
state snapshots into a deterministic causal temporal featureization layer.

This layer should still be:

- causal
- bounded
- replayable
- explainable
- efficient enough for online inference

Its job is to expand recent traces into reusable temporal basis features such as:

- multi-window counts
- time-since-last-event features
- dwell and streak features
- exponential decay features
- transition features
- short ordered motifs

Examples include:

- wood break count in 2 seconds, 10 seconds, and 30 seconds
- milliseconds since the last relevant target switch
- dwell on the current target
- low-motion streak duration
- exponentially decayed evidence of recent resource gain
- break-then-gain motifs within a bounded window

Time sensitivity should be expressed primarily in this deterministic feature layer,
not delegated to a raw-trace online sequence model in the first production iteration.

## 2. Scoring Becomes Trainable

The projection-to-detector step should become a trainable scorer layer.

The first production-ready model family can be simple:

- linear models
- logistic heads
- multi-head scoring over structured outputs

This is sufficient because the main temporal structure should already be represented
in the featureization layer.

In other words, the first model does not need to learn time from scratch.
It only needs to assign weights to a replayable temporal basis.

That gives the system:

- low online cost
- easier offline tuning
- versionable model artifacts
- better inspectability than the current hand-written weighted logic

More expressive model families may become useful later, but they should only be
considered after the feature schema, replay flow, and evaluation loop are stable.

## 3. Episode Output Should Be Reframed As Hypotheses

The current episode structure should eventually be replaced.

Instead of a fixed large-enum episode output, the online system should emit
intent hypotheses with confidence rather than one hard semantic claim.

The recommended representation is a factorized intent space such as:

`intent_hypothesis = (family, target, phase)`

For example:

- `family`: gathering, mining, combat, navigation, inventory, crafting, ui, idle
- `target`: wood, ore, hostile, chest, crafting_table, none, unknown
- `phase`: seek, approach, act, collect, organize, transition, abort

This representation is preferable to a single large flat label set because:

- it uses data more efficiently
- it supports compositional generalization
- it avoids label-space explosion
- it is easier to train as a structured prediction problem
- it allows the system to emit top-k alternatives and uncertainty

However, this factorization must not be treated as a fully free Cartesian product.
The system should include legality constraints or constrained decoding so that only
sensible combinations are surfaced.

The online output should therefore be closer to:

- constrained intent hypotheses
- confidence or score per hypothesis
- uncertainty mass or abstention support
- optional evidence summary

This remains an operational trace-only intent layer.
It should not be confused with a complete model of a human player's hidden long-term goals.

## 4. Training Data Should Follow Two Complementary Tracks

The training loop should grow along two tracks in parallel.

### Track A: Trace-Only Silver Labels

One track should rely on `trace jsonl` alone.

These labels can be proposed by:

- heuristics
- existing detectors
- high-quality LLM-assisted annotation over trace summaries

These outputs should be treated as silver labels rather than final truth.

This track is useful for:

- scaling data volume quickly
- bootstrapping early models
- generating candidate spans
- pretraining or weakly supervised tuning

### Track B: Human-Reviewed Video Plus Trace Labels

The second track should use:

- gameplay video
- trace jsonl
- an explicit alignment mechanism
- a dedicated annotation UI

The purpose of this track is not frame-perfect intent labeling.
The purpose is human-reviewed span or episode labeling with coarse boundaries.

Humans should label bounded windows or episodes rather than every exact frame.
The UI should present structured choices and allow:

- unknown
- ambiguous
- candidate selection
- coarse start and end adjustment

This is the practical path to higher-quality evaluation data and ontology refinement.

## 5. Replay And Dataset Implications

Replay needs to be understood in two different senses.

First, logical replay from trace jsonl should be enough to rebuild features, scores,
and hypothesis outputs offline.

Second, human annotation replay usually requires video because current traces do not
contain enough information to visually reconstruct the original play session.

A future dataset bundle should therefore likely contain:

- trace jsonl
- optional or recommended gameplay video
- metadata and schema version
- alignment metadata
- annotation spans and labels

This also suggests a future data-collection path involving a separate research or
recorder-oriented mod build for real player sessions, rather than folding all
research capture requirements directly into the main production inference build.

## 6. Practical Design Principles

The roadmap can be summarized by a few stable principles:

- keep temporal reasoning explicit in deterministic featureization
- keep online inference cheap and inspectable
- avoid large flat intent enums
- use structured hypotheses with constrained combinations
- treat trace-only labels as operational intent evidence, not as direct mind reading
- rely on both silver labels and human-reviewed episode labels
- optimize annotation around spans and episodes, not exact frames

## Summary

The near-term direction is clear:

- formalize temporal featureization over traces
- replace hand-written detector scoring with trainable models
- replace fixed episode outputs with constrained structured intent hypotheses
- build a dual-track training data pipeline with silver labels and human-reviewed labels

That path preserves replayability and inspection while creating a realistic route
toward an iterative ML-based online reasoning system.

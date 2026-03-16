# Current Branch State

## Purpose

This document records what this branch actually implements today.

Read this before reading the target architecture docs. The target docs describe where the project should go; this document describes what contributors are really standing on right now.

## Repository Shape

Today the repository is:

- one Gradle/Fabric project
- one Minecraft target: `1.21.1`
- one Java toolchain target: `21`
- one mod artifact

Today the repository does not contain:

- a `package.json`
- a Node or pnpm workspace
- a local TypeScript websocket hub
- a shared core module split out from version-specific Fabric adapters
- a multi-version release layout

## Implemented Pieces

### 1. Client Capture Loop

The client mod samples a small observation payload on a bounded tick cadence.

That payload currently includes coarse movement and target-facing data such as:

- position
- velocity
- dimension
- fps
- target description

This is still a minimal sampling experiment, not the fuller event taxonomy described elsewhere in the docs.

### 2. Local Debug Surface

The current branch already has a useful local inspection path:

- recent observations are stored in-memory
- the latest state is rendered into the vanilla debug HUD

This is the most concrete feedback surface currently implemented in-repo.

### 3. Java-Side Websocket Transport

The Fabric client currently owns websocket publishing directly.

That Java transport layer currently handles:

- endpoint resolution
- connection lifecycle
- reconnect backoff
- bounded queueing
- payload serialization
- send latency and failure tracking

This means the current branch is still transport-heavy on the Java side.

### 4. Transport Observability

The current branch also includes transport-focused runtime visibility:

- local transport status state for debug display
- OTel transport metrics for queue depth, reconnects, drops, send latency, and failures

That observability is real and useful, but it is still scoped to runtime transport health rather than gameplay semantics.

## What Is Missing

The following architecture pieces are not implemented in this branch yet:

- a local TypeScript trace hub in this repository
- blackboard materialization behind that hub
- detector and scorer execution behind that hub
- stable behavior episode publication
- replay artifacts and replay-driven debugging workflow
- shared core plus per-version adapter module split
- simultaneous multi-version release flow

## Practical Reading Rule

When a doc says "should" or "target," treat it as design direction for main.

When you need to understand the code in this branch, assume:

- the branch is single-version
- the branch is Java-first
- the branch is still using a direct websocket client in the mod
- the branch is a capture and transport prototype, not the final repository shape

## Why This Document Exists

Without an explicit current-state doc, it is too easy to read the target architecture docs and assume the repository already contains:

- a TypeScript hub
- a clean core and adapter split
- multi-version release support
- downstream boundary separation that is not actually present yet

This document is meant to prevent that confusion.

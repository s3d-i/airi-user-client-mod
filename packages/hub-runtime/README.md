# `@airi-client-mod/hub-runtime`

Domain package for the local hub reasoning pipeline.

- Owns the initial/current-state raw mod ingress contract, runtime state evolution, detector support, and episode outputs.
- Keeps the raw trace contract aligned to the Java mod's current emit shape and exposes minimal hand-written contract gating.
- Exposes only injectable logging contracts, not concrete logger implementations.
- Does not own websocket listener code, trace retention, debug-surface DTOs, or AIRI-facing bridge code.

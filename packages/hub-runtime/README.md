# `@airi-client-mod/hub-runtime`

Domain package for the local hub reasoning pipeline.

- Owns raw trace types, projection state, detector support, and episode outputs.
- Accepts typed trace events from adapters and keeps those domain types transport-agnostic.
- Does not own websocket listener code, websocket framing, or AIRI-facing bridge code.

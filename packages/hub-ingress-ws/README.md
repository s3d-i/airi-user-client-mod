# `@airi-client-mod/hub-ingress-ws`

Websocket transport adapter for local hub ingress.

- Owns websocket listener lifecycle, text-frame receive, JSON parse, transport counters, and trace handoff.
- Uses the raw contract and minimal contract gating exported by `hub-runtime` instead of owning long-lived contract boilerplate itself.
- Does not own trace retention, debug-surface DTO shaping, gameplay semantics, detector/scorer logic, or episode lifecycle rules.

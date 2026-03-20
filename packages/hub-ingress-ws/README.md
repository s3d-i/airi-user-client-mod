# `@airi-client-mod/hub-ingress-ws`

Websocket transport adapter for local hub ingress.

- Owns websocket listener concerns and adapter-level wire framing for traffic from the mod.
- Converts incoming frames into typed `hub-runtime` inputs.
- Does not define gameplay semantics, projections, detector/scorer logic, or episode lifecycle rules.

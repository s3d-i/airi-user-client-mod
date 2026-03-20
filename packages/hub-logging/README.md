# `@airi-client-mod/hub-logging`

Concrete logger implementations for local hub packages and apps.

- Owns noop, structured fan-out, and console-backed logger implementations behind the `hub-runtime` logging contracts.
- Depends on `hub-runtime` logging types, but does not pull runtime state or transport concerns into logger consumers.
- Does not own trace retention, websocket ingress, or debug-surface DTOs.

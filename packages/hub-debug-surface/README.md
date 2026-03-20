# `@airi-client-mod/hub-debug-surface`

Read-only server-side inspection surface for the local hub.

- Exposes runtime snapshot, ingress status, retained traces, and buffered logs for local tooling and UI consumers.
- Defines the debug-facing DTOs and HTTP/SSE surface instead of pushing that shaping into runtime or ingress.
- Does not own transport ingress, trace retention logic, or annotation write APIs.

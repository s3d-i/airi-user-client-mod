# `@airi-client-mod/local-hub-ui`

Separate Vite web UI for the local hub debug surface.

- Consumes only the read-only HTTP/SSE debug surface exported by `apps/local-hub`.
- Shows ingress status, runtime snapshot, retained traces, and buffered logs for local inspection.
- Does not import runtime or trace-store internals directly and does not implement annotation writes.

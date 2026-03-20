# `@airi-client-mod/local-hub`

Node composition root for the local TypeScript hub.

- Wires websocket ingress, runtime, trace retention, structured logging, debug surface, and the inactive AIRI bridge boundary together.
- Owns process-level composition, defaults, startup, and shutdown, not gameplay semantics or detector logic.
- Serves as the executable entrypoint for the local hub process.

# `@airi-client-mod/local-hub`

Node composition root for the local TypeScript hub.

- Wires ingress adapters, `hub-runtime`, outbound AIRI bridges, and hub-facing observability together.
- Owns process-level composition, not gameplay semantics or detector logic.
- Serves as the executable entrypoint for the local hub process.

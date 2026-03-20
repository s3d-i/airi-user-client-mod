# `@airi-client-mod/hub-trace-store`

Bounded in-memory trace retention for the local hub.

- Owns recent trace retention and read-side queries, not runtime reasoning.
- Stores raw ingress traces behind stable retained-trace identifiers for future annotation-adjacent work.
- Does not own websocket ingress, debug-surface DTO shaping, or durable persistence.

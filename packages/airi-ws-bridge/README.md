# `@airi-client-mod/airi-ws-bridge`

Outbound AIRI adapter for selected derived hub outputs.

- Consumes outputs chosen by `hub-runtime` and hub composition rather than raw ingress frames.
- Stays isolated from websocket ingress concerns and does not depend on ingress internals.
- Forms the boundary from the local hub to AIRI-facing protocol or runtime packages.

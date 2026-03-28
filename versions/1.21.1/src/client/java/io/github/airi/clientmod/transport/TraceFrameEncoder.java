package io.github.airi.clientmod.transport;

import java.util.Objects;

import io.github.airi.clientmod.core.trace.TraceEvent;

public interface TraceFrameEncoder {
	EncodedTraceFrame encodeTraceEvent(String sessionId, TraceEvent event);

	EncodedTraceFrame encodeSessionStart(String sessionId, long sequence, long capturedAtMillis, SessionStartPayload payload);

	EncodedTraceFrame encodeSessionEnd(String sessionId, long sequence, long capturedAtMillis);

	record EncodedTraceFrame(
		String kind,
		String payload
	) {
		public EncodedTraceFrame {
			kind = Objects.requireNonNull(kind, "kind");
			payload = Objects.requireNonNull(payload, "payload");
		}
	}
}

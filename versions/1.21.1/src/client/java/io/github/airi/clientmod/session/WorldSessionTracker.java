package io.github.airi.clientmod.session;

import java.util.UUID;

import io.github.airi.clientmod.AiriUserClientMod;

public final class WorldSessionTracker {
	private ActiveSessionState activeSession;
	private long nextTraceSequence = 1L;

	public synchronized SessionControlFrame startWorldSession() {
		if (activeSession != null) {
			AiriUserClientMod.LOGGER.warn(
				"Starting new world session before previous session {} ended; rotating session ownership",
				activeSession.sessionId()
			);
		}

		ActiveSessionState nextSession = new ActiveSessionState(UUID.randomUUID().toString(), System.currentTimeMillis());
		activeSession = nextSession;
		nextTraceSequence = 2L;

		return new SessionControlFrame(nextSession.sessionId(), 1L, nextSession.startedAtMillis());
	}

	public synchronized SessionControlFrame endWorldSession() {
		if (activeSession == null) {
			AiriUserClientMod.LOGGER.warn("Received world session end without an active session");
			return null;
		}

		SessionControlFrame frame = new SessionControlFrame(
			activeSession.sessionId(),
			nextTraceSequence++,
			System.currentTimeMillis()
		);
		activeSession = null;
		nextTraceSequence = 1L;
		return frame;
	}

	public synchronized boolean hasActiveSession() {
		return activeSession != null;
	}

	public synchronized ActiveSessionState getActiveSession() {
		return activeSession;
	}

	public synchronized SampleTraceContext beginObservationTrace() {
		if (activeSession == null) {
			return null;
		}

		return new SampleTraceContext(activeSession.sessionId(), nextTraceSequence++, System.currentTimeMillis());
	}

	public record ActiveSessionState(
		String sessionId,
		long startedAtMillis
	) {
	}

	public record SessionControlFrame(
		String sessionId,
		long sequence,
		long capturedAtMillis
	) {
	}

	public record SampleTraceContext(
		String sessionId,
		long sequence,
		long capturedAtMillis
	) {
	}
}

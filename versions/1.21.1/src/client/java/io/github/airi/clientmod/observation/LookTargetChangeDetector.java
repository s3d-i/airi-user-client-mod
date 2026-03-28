package io.github.airi.clientmod.observation;

import java.util.Objects;

import io.github.airi.clientmod.core.trace.TraceEvent;

public final class LookTargetChangeDetector {
	private TraceEvent.LookTarget lastLookTarget;

	public LookTargetChangeDraft detect(ClientSnapshot snapshot) {
		if (Objects.equals(lastLookTarget, snapshot.lookTarget())) {
			return null;
		}

		return new LookTargetChangeDraft(snapshot.lookTarget());
	}

	public void commit(LookTargetChangeDraft draft) {
		lastLookTarget = draft.target();
	}

	public void reset() {
		lastLookTarget = null;
	}

	public record LookTargetChangeDraft(TraceEvent.LookTarget target) {
	}
}

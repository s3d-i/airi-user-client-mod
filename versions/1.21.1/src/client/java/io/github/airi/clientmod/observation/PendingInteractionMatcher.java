package io.github.airi.clientmod.observation;

public interface PendingInteractionMatcher<Attempt, SuccessCandidate, MatchResult> {
	void registerAttempt(Attempt attempt);

	MatchResult matchSuccess(SuccessCandidate successCandidate);

	void reset();
}

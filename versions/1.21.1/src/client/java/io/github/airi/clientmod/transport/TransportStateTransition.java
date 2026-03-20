package io.github.airi.clientmod.transport;

public record TransportStateTransition(
	TransportConnectionState previousState,
	TransportConnectionState currentState,
	long enteredAtMillis,
	boolean changed
) {
}

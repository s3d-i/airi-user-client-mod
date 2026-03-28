package io.github.airi.clientmod.transport;

@FunctionalInterface
public interface SessionStartPayloadSupplier {
	SessionStartPayload createPayload();
}

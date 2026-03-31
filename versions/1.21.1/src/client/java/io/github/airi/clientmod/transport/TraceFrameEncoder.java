package io.github.airi.clientmod.transport;

import java.util.List;
import java.util.Objects;

import io.github.airi.clientmod.transport.contract.SessionStartPayload;
import io.github.airi.clientmod.transport.contract.TraceEventKinds;
import io.github.airi.clientmod.core.trace.InteractionBlockAttackAttemptTraceEvent;
import io.github.airi.clientmod.core.trace.InteractionBlockUseAttemptTraceEvent;
import io.github.airi.clientmod.core.trace.InteractionBlockBreakSuccessTraceEvent;
import io.github.airi.clientmod.core.trace.InteractionEntityAttackAttemptTraceEvent;
import io.github.airi.clientmod.core.trace.InteractionEntityUseAttemptTraceEvent;
import io.github.airi.clientmod.core.trace.InteractionItemUseAttemptTraceEvent;
import io.github.airi.clientmod.core.trace.InventoryTransactionTraceEvent;
import io.github.airi.clientmod.core.trace.PlayerHandStateChangedTraceEvent;
import io.github.airi.clientmod.core.trace.PlayerLookTargetChangedTraceEvent;
import io.github.airi.clientmod.core.trace.PlayerMotionSampleTraceEvent;
import io.github.airi.clientmod.core.trace.PlayerSelectedSlotChangedTraceEvent;
import io.github.airi.clientmod.core.trace.TraceEvent;

public final class TraceFrameEncoder {
	private static final int WS_PROTOCOL_VERSION = 1;

	public EncodedTraceFrame encodeTraceEvent(String sessionId, TraceEvent event) {
		if (event instanceof PlayerMotionSampleTraceEvent sample) {
			return new EncodedTraceFrame(
				TraceEventKinds.PLAYER_MOTION_SAMPLE,
				serializePlayerMotionSample(sessionId, sample)
			);
		}

		if (event instanceof PlayerLookTargetChangedTraceEvent lookTargetChanged) {
			return new EncodedTraceFrame(
				TraceEventKinds.PLAYER_LOOK_TARGET_CHANGED,
				serializePlayerLookTargetChangedTraceEvent(sessionId, lookTargetChanged)
			);
		}

		if (event instanceof PlayerSelectedSlotChangedTraceEvent selectedSlotChanged) {
			return new EncodedTraceFrame(
				TraceEventKinds.PLAYER_SELECTED_SLOT_CHANGED,
				serializePlayerSelectedSlotChangedTraceEvent(sessionId, selectedSlotChanged)
			);
		}

		if (event instanceof PlayerHandStateChangedTraceEvent handStateChanged) {
			return new EncodedTraceFrame(
				TraceEventKinds.PLAYER_HAND_STATE_CHANGED,
				serializePlayerHandStateChangedTraceEvent(sessionId, handStateChanged)
			);
		}

		if (event instanceof InteractionBlockAttackAttemptTraceEvent blockAttackAttempt) {
			return new EncodedTraceFrame(
				TraceEventKinds.INTERACTION_BLOCK_ATTACK_ATTEMPT,
				serializeInteractionBlockAttackAttemptTraceEvent(sessionId, blockAttackAttempt)
			);
		}

		if (event instanceof InteractionItemUseAttemptTraceEvent itemUseAttempt) {
			return new EncodedTraceFrame(
				TraceEventKinds.INTERACTION_ITEM_USE_ATTEMPT,
				serializeInteractionItemUseAttemptTraceEvent(sessionId, itemUseAttempt)
			);
		}

		if (event instanceof InteractionBlockUseAttemptTraceEvent blockUseAttempt) {
			return new EncodedTraceFrame(
				TraceEventKinds.INTERACTION_BLOCK_USE_ATTEMPT,
				serializeInteractionBlockUseAttemptTraceEvent(sessionId, blockUseAttempt)
			);
		}

		if (event instanceof InteractionEntityUseAttemptTraceEvent entityUseAttempt) {
			return new EncodedTraceFrame(
				TraceEventKinds.INTERACTION_ENTITY_USE_ATTEMPT,
				serializeInteractionEntityUseAttemptTraceEvent(sessionId, entityUseAttempt)
			);
		}

		if (event instanceof InteractionEntityAttackAttemptTraceEvent entityAttackAttempt) {
			return new EncodedTraceFrame(
				TraceEventKinds.INTERACTION_ENTITY_ATTACK_ATTEMPT,
				serializeInteractionEntityAttackAttemptTraceEvent(sessionId, entityAttackAttempt)
			);
		}

		if (event instanceof InteractionBlockBreakSuccessTraceEvent blockBreakSuccess) {
			return new EncodedTraceFrame(
				TraceEventKinds.INTERACTION_BLOCK_BREAK_SUCCESS,
				serializeInteractionBlockBreakSuccessTraceEvent(sessionId, blockBreakSuccess)
			);
		}

		if (event instanceof InventoryTransactionTraceEvent inventoryTransaction) {
			return new EncodedTraceFrame(
				TraceEventKinds.INVENTORY_TRANSACTION,
				serializeInventoryTransactionTraceEvent(sessionId, inventoryTransaction)
			);
		}

		throw new IllegalArgumentException("Unsupported trace event: " + event.getClass().getName());
	}

	public EncodedTraceFrame encodeSessionStart(String sessionId, long sequence, long capturedAtMillis, SessionStartPayload payload) {
		StringBuilder json = new StringBuilder(768);
		json.append('{');
		json.append("\"wsProtocolVersion\":").append(WS_PROTOCOL_VERSION).append(',');
		json.append("\"kind\":\"").append(TraceEventKinds.SESSION_START).append("\",");
		json.append("\"sessionId\":\"").append(escapeJson(sessionId)).append("\",");
		json.append("\"seq\":").append(sequence).append(',');
		json.append("\"capturedAtMillis\":").append(capturedAtMillis).append(',');
		json.append("\"payload\":{");
		appendSessionStartPayload(json, payload);
		json.append("}}");
		return new EncodedTraceFrame(TraceEventKinds.SESSION_START, json.toString());
	}

	public EncodedTraceFrame encodeSessionEnd(String sessionId, long sequence, long capturedAtMillis) {
		StringBuilder json = new StringBuilder(160);
		json.append('{');
		json.append("\"wsProtocolVersion\":").append(WS_PROTOCOL_VERSION).append(',');
		json.append("\"kind\":\"").append(TraceEventKinds.SESSION_END).append("\",");
		json.append("\"sessionId\":\"").append(escapeJson(sessionId)).append("\",");
		json.append("\"seq\":").append(sequence).append(',');
		json.append("\"capturedAtMillis\":").append(capturedAtMillis);
		json.append('}');
		return new EncodedTraceFrame(TraceEventKinds.SESSION_END, json.toString());
	}

	private String serializePlayerMotionSample(String sessionId, PlayerMotionSampleTraceEvent sample) {
		StringBuilder json = new StringBuilder(320);
		appendTraceEnvelopeStart(json, sessionId, TraceEventKinds.PLAYER_MOTION_SAMPLE, sample);
		appendCommonPayloadStart(json, sample);
		json.append("\"x\":").append(sample.x()).append(',');
		json.append("\"y\":").append(sample.y()).append(',');
		json.append("\"z\":").append(sample.z()).append(',');
		json.append("\"vx\":").append(sample.vx()).append(',');
		json.append("\"vy\":").append(sample.vy()).append(',');
		json.append("\"vz\":").append(sample.vz()).append(',');
		json.append("\"health\":").append(sample.health()).append(',');
		json.append("\"maxHealth\":").append(sample.maxHealth()).append(',');
		json.append("\"absorption\":").append(sample.absorption()).append(',');
		json.append("\"onGround\":").append(sample.onGround()).append(',');
		json.append("\"touchingWater\":").append(sample.touchingWater()).append(',');
		json.append("\"submergedInWater\":").append(sample.submergedInWater());
		json.append("}}");
		return json.toString();
	}

	private String serializePlayerLookTargetChangedTraceEvent(String sessionId, PlayerLookTargetChangedTraceEvent event) {
		StringBuilder json = new StringBuilder(320);
		appendTraceEnvelopeStart(json, sessionId, TraceEventKinds.PLAYER_LOOK_TARGET_CHANGED, event);
		appendCommonPayloadStart(json, event);
		json.append("\"target\":");
		appendLookTarget(json, event.target());
		json.append("}}");
		return json.toString();
	}

	private String serializePlayerSelectedSlotChangedTraceEvent(String sessionId, PlayerSelectedSlotChangedTraceEvent event) {
		StringBuilder json = new StringBuilder(320);
		appendTraceEnvelopeStart(json, sessionId, TraceEventKinds.PLAYER_SELECTED_SLOT_CHANGED, event);
		appendCommonPayloadStart(json, event);
		json.append("\"previousSelectedSlot\":").append(event.previousSelectedSlot()).append(',');
		json.append("\"selectedSlot\":").append(event.selectedSlot()).append(',');
		json.append("\"mainHand\":");
		appendItemStackSnapshot(json, event.mainHand());
		json.append(',');
		json.append("\"offHand\":");
		appendItemStackSnapshot(json, event.offHand());
		json.append("}}");
		return json.toString();
	}

	private String serializePlayerHandStateChangedTraceEvent(String sessionId, PlayerHandStateChangedTraceEvent event) {
		StringBuilder json = new StringBuilder(320);
		appendTraceEnvelopeStart(json, sessionId, TraceEventKinds.PLAYER_HAND_STATE_CHANGED, event);
		appendCommonPayloadStart(json, event);
		json.append("\"selectedSlot\":").append(event.selectedSlot()).append(',');
		json.append("\"mainHand\":");
		appendItemStackSnapshot(json, event.mainHand());
		json.append(',');
		json.append("\"offHand\":");
		appendItemStackSnapshot(json, event.offHand());
		json.append("}}");
		return json.toString();
	}

	private String serializeInteractionBlockAttackAttemptTraceEvent(
		String sessionId,
		InteractionBlockAttackAttemptTraceEvent event
	) {
		StringBuilder json = new StringBuilder(320);
		appendTraceEnvelopeStart(json, sessionId, TraceEventKinds.INTERACTION_BLOCK_ATTACK_ATTEMPT, event);
		appendCommonPayloadStart(json, event);
		json.append("\"block\":");
		appendBlockReference(json, event.block());
		json.append(',');
		json.append("\"hand\":\"").append(escapeJson(event.hand())).append("\",");
		json.append("\"selectedSlot\":").append(event.selectedSlot()).append(',');
		json.append("\"heldItem\":");
		appendItemStackSnapshot(json, event.heldItem());
		json.append("}}");
		return json.toString();
	}

	private String serializeInteractionItemUseAttemptTraceEvent(
		String sessionId,
		InteractionItemUseAttemptTraceEvent event
	) {
		StringBuilder json = new StringBuilder(320);
		appendTraceEnvelopeStart(json, sessionId, TraceEventKinds.INTERACTION_ITEM_USE_ATTEMPT, event);
		appendCommonPayloadStart(json, event);
		json.append("\"hand\":\"").append(escapeJson(event.hand())).append("\",");
		json.append("\"selectedSlot\":").append(event.selectedSlot()).append(',');
		json.append("\"heldItem\":");
		appendItemStackSnapshot(json, event.heldItem());
		json.append("}}");
		return json.toString();
	}

	private String serializeInteractionBlockUseAttemptTraceEvent(
		String sessionId,
		InteractionBlockUseAttemptTraceEvent event
	) {
		StringBuilder json = new StringBuilder(320);
		appendTraceEnvelopeStart(json, sessionId, TraceEventKinds.INTERACTION_BLOCK_USE_ATTEMPT, event);
		appendCommonPayloadStart(json, event);
		json.append("\"block\":");
		appendBlockReference(json, event.block());
		json.append(',');
		json.append("\"hand\":\"").append(escapeJson(event.hand())).append("\",");
		json.append("\"selectedSlot\":").append(event.selectedSlot()).append(',');
		json.append("\"heldItem\":");
		appendItemStackSnapshot(json, event.heldItem());
		json.append("}}");
		return json.toString();
	}

	private String serializeInteractionEntityUseAttemptTraceEvent(
		String sessionId,
		InteractionEntityUseAttemptTraceEvent event
	) {
		StringBuilder json = new StringBuilder(320);
		appendTraceEnvelopeStart(json, sessionId, TraceEventKinds.INTERACTION_ENTITY_USE_ATTEMPT, event);
		appendCommonPayloadStart(json, event);
		json.append("\"entity\":");
		appendLookTargetEntity(json, event.entity());
		json.append(',');
		json.append("\"hand\":\"").append(escapeJson(event.hand())).append("\",");
		json.append("\"selectedSlot\":").append(event.selectedSlot()).append(',');
		json.append("\"heldItem\":");
		appendItemStackSnapshot(json, event.heldItem());
		json.append("}}");
		return json.toString();
	}

	private String serializeInteractionEntityAttackAttemptTraceEvent(
		String sessionId,
		InteractionEntityAttackAttemptTraceEvent event
	) {
		StringBuilder json = new StringBuilder(320);
		appendTraceEnvelopeStart(json, sessionId, TraceEventKinds.INTERACTION_ENTITY_ATTACK_ATTEMPT, event);
		appendCommonPayloadStart(json, event);
		json.append("\"entity\":");
		appendLookTargetEntity(json, event.entity());
		json.append(',');
		json.append("\"hand\":\"").append(escapeJson(event.hand())).append("\",");
		json.append("\"selectedSlot\":").append(event.selectedSlot()).append(',');
		json.append("\"heldItem\":");
		appendItemStackSnapshot(json, event.heldItem());
		json.append("}}");
		return json.toString();
	}

	private String serializeInteractionBlockBreakSuccessTraceEvent(
		String sessionId,
		InteractionBlockBreakSuccessTraceEvent event
	) {
		StringBuilder json = new StringBuilder(320);
		appendTraceEnvelopeStart(json, sessionId, TraceEventKinds.INTERACTION_BLOCK_BREAK_SUCCESS, event);
		appendCommonPayloadStart(json, event);
		json.append("\"block\":");
		appendBlockReference(json, event.block());
		json.append(',');
		json.append("\"hand\":\"").append(escapeJson(event.hand())).append("\",");
		json.append("\"selectedSlot\":").append(event.selectedSlot()).append(',');
		json.append("\"heldItem\":");
		appendItemStackSnapshot(json, event.heldItem());
		json.append("}}");
		return json.toString();
	}

	private String serializeInventoryTransactionTraceEvent(String sessionId, InventoryTransactionTraceEvent event) {
		StringBuilder json = new StringBuilder(512);
		appendTraceEnvelopeStart(json, sessionId, TraceEventKinds.INVENTORY_TRANSACTION, event);
		appendCommonPayloadStart(json, event);
		json.append("\"containerKind\":\"").append(escapeJson(event.containerKind())).append("\",");
		json.append("\"source\":\"").append(escapeJson(event.source())).append("\",");
		json.append("\"changedSlots\":[");
		appendInventorySlotDeltas(json, event.changedSlots());
		json.append("]}");
		json.append('}');
		return json.toString();
	}

	private void appendSessionStartPayload(StringBuilder json, SessionStartPayload payload) {
		SessionStartPayload.Metadata metadata = payload.metadata();
		SessionStartPayload.Producer producer = metadata.producer();
		SessionStartPayload.Schema schema = metadata.schema();
		SessionStartPayload.Capabilities capabilities = payload.capabilities();
		SessionStartPayload.Sampling sampling = payload.sampling();

		json.append("\"metadata\":{");
		json.append("\"producer\":{");
		json.append("\"modId\":\"").append(escapeJson(producer.modId())).append("\",");
		json.append("\"modVersion\":\"").append(escapeJson(producer.modVersion())).append("\",");
		json.append("\"minecraftVersion\":\"").append(escapeJson(producer.minecraftVersion())).append("\",");
		json.append("\"loader\":\"").append(escapeJson(producer.loader())).append('"');
		json.append("},");
		json.append("\"schema\":{");
		json.append("\"traceVersion\":").append(schema.traceVersion());
		json.append('}');
		json.append("},");
		json.append("\"capabilities\":{");
		json.append("\"eventKinds\":[");
		appendStringArray(json, capabilities.eventKinds());
		json.append("]}");
		json.append(',');

		json.append("\"sampling\":{");
		json.append("\"player.motion.samplingMode\":\"").append(escapeJson(sampling.playerMotionSamplingMode())).append("\",");
		json.append("\"player.motion.sampleIntervalTicks\":").append(sampling.playerMotionSampleIntervalTicks()).append(',');
		json.append("\"player.motion.sampleIntervalMillis\":").append(sampling.playerMotionSampleIntervalMillis()).append(',');
		json.append("\"inventoryScanMode\":\"").append(escapeJson(sampling.inventoryScanMode())).append("\",");
		json.append("\"inventoryMaxChangedSlots\":").append(sampling.inventoryMaxChangedSlots());
		json.append('}');
	}

	private void appendTraceEnvelopeStart(StringBuilder json, String sessionId, String kind, TraceEvent event) {
		json.append('{');
		json.append("\"wsProtocolVersion\":").append(WS_PROTOCOL_VERSION).append(',');
		json.append("\"kind\":\"").append(kind).append("\",");
		json.append("\"sessionId\":\"").append(escapeJson(sessionId)).append("\",");
		json.append("\"seq\":").append(event.sequence()).append(',');
		json.append("\"capturedAtMillis\":").append(event.capturedAtMillis()).append(',');
		json.append("\"payload\":{");
	}

	private void appendCommonPayloadStart(StringBuilder json, TraceEvent event) {
		json.append("\"worldTick\":").append(event.worldTick()).append(',');
		json.append("\"dimensionKey\":\"").append(escapeJson(event.dimensionKey())).append("\",");
	}

	private void appendLookTarget(StringBuilder json, TraceEvent.LookTarget target) {
		json.append('{');
		json.append("\"kind\":\"").append(escapeJson(target.kind())).append('"');
		if (target.targetDescription() != null) {
			json.append(",\"targetDescription\":\"").append(escapeJson(target.targetDescription())).append('"');
		}
		if (target.block() != null) {
			json.append(",\"block\":");
			appendBlockReference(json, target.block());
		}
		if (target.entity() != null) {
			json.append(",\"entity\":");
			appendLookTargetEntity(json, target.entity());
		}
		json.append('}');
	}

	private void appendLookTargetEntity(StringBuilder json, TraceEvent.LookTargetEntity entity) {
		json.append('{');
		json.append("\"entityTypeId\":\"").append(escapeJson(entity.entityTypeId())).append('"');
		if (entity.entityId() != null) {
			json.append(",\"entityId\":").append(entity.entityId());
		}
		json.append('}');
	}

	private void appendBlockReference(StringBuilder json, TraceEvent.BlockReference block) {
		json.append('{');
		json.append("\"blockId\":\"").append(escapeJson(block.blockId())).append("\",");
		json.append("\"position\":");
		appendBlockPosition(json, block.position());
		if (block.hitFace() != null) {
			json.append(",\"hitFace\":\"").append(escapeJson(block.hitFace())).append('"');
		}
		json.append('}');
	}

	private void appendBlockPosition(StringBuilder json, TraceEvent.BlockPosition position) {
		json.append('{');
		json.append("\"x\":").append(position.x()).append(',');
		json.append("\"y\":").append(position.y()).append(',');
		json.append("\"z\":").append(position.z());
		json.append('}');
	}

	private void appendItemStackSnapshot(StringBuilder json, TraceEvent.ItemStackSnapshot item) {
		json.append('{');
		json.append("\"itemId\":");
		if (item.itemId() == null) {
			json.append("null");
		} else {
			json.append('"').append(escapeJson(item.itemId())).append('"');
		}
		json.append(",\"count\":").append(item.count());
		json.append(",\"damage\":").append(item.damage());
		json.append(",\"maxDamage\":").append(item.maxDamage());
		json.append('}');
	}

	private void appendInventorySlotDeltas(StringBuilder json, List<TraceEvent.InventorySlotDelta> changedSlots) {
		for (int index = 0; index < changedSlots.size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			TraceEvent.InventorySlotDelta delta = changedSlots.get(index);
			json.append('{');
			json.append("\"slot\":").append(delta.slot()).append(',');
			json.append("\"previous\":");
			appendItemStackSnapshot(json, delta.previous());
			json.append(',');
			json.append("\"current\":");
			appendItemStackSnapshot(json, delta.current());
			json.append('}');
		}
	}

	private void appendStringArray(StringBuilder json, List<String> values) {
		for (int index = 0; index < values.size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append('"').append(escapeJson(values.get(index))).append('"');
		}
	}

	private static String escapeJson(String value) {
		StringBuilder escaped = new StringBuilder(value.length() + 8);

		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '\\' -> escaped.append("\\\\");
				case '"' -> escaped.append("\\\"");
				case '\n' -> escaped.append("\\n");
				case '\r' -> escaped.append("\\r");
				case '\t' -> escaped.append("\\t");
				default -> escaped.append(character);
			}
		}

		return escaped.toString();
	}

	public record EncodedTraceFrame(
		String kind,
		String payload
	) {
		public EncodedTraceFrame {
			kind = Objects.requireNonNull(kind, "kind");
			payload = Objects.requireNonNull(payload, "payload");
		}
	}
}

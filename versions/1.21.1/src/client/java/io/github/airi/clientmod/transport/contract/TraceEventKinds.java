package io.github.airi.clientmod.transport.contract;

import java.util.List;

public final class TraceEventKinds {
	public static final String SESSION_START = "trace.session.start";
	public static final String SESSION_END = "trace.session.end";
	public static final String PLAYER_MOTION_SAMPLE = "player.motion.sample";
	public static final String PLAYER_LOOK_TARGET_CHANGED = "player.look.target.changed";
	public static final String PLAYER_SELECTED_SLOT_CHANGED = "player.selected_slot.changed";
	public static final String PLAYER_HAND_STATE_CHANGED = "player.hand_state.changed";
	public static final String INTERACTION_BLOCK_ATTACK_ATTEMPT = "interaction.block.attack.attempt";
	public static final String INTERACTION_BLOCK_BREAK_SUCCESS = "interaction.block.break.success";
	public static final String INVENTORY_TRANSACTION = "inventory.transaction";

	public static final List<String> CAPABILITY_EVENT_KINDS = List.of(
		PLAYER_MOTION_SAMPLE,
		PLAYER_LOOK_TARGET_CHANGED,
		PLAYER_SELECTED_SLOT_CHANGED,
		PLAYER_HAND_STATE_CHANGED,
		INTERACTION_BLOCK_ATTACK_ATTEMPT,
		INTERACTION_BLOCK_BREAK_SUCCESS,
		INVENTORY_TRANSACTION
	);

	private TraceEventKinds() {
	}
}

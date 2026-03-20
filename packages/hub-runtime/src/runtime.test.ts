import test from "node:test";
import assert from "node:assert/strict";

import {
  createInitialEpisodeMachineState,
  stepEpisodeMachine
} from "./episode/index.js";
import { evaluateDetectors } from "./detector/index.js";
import { createEmptyProjectionSnapshot, reduceProjectionSnapshot } from "./projection/index.js";
import {
  CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION,
  CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE,
  CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED,
  CURRENT_MOD_TRACE_KIND_PLAYER_LOOK_TARGET_CHANGED,
  decodeCurrentModTraceEvent,
  type InteractionBlockBreakTraceEvent,
  type InventoryTransactionTraceEvent,
  type ObservationSampleTraceEvent,
  type PlayerHandStateChangedTraceEvent,
  type PlayerLookTargetChangedTraceEvent,
  type RawTraceEvent
} from "./trace/index.js";

test("decodeCurrentModTraceEvent accepts inventory.transaction", () => {
  const result = decodeCurrentModTraceEvent({
    v: 1,
    kind: CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION,
    sessionId: "session-a",
    seq: 7,
    capturedAtMillis: 7_000,
    payload: {
      worldTick: 400,
      dimensionKey: "minecraft:overworld",
      containerKind: "player_inventory",
      source: "player_inventory.scan",
      changedSlots: [
        {
          slot: 3,
          previous: {
            itemId: null,
            count: 0,
            damage: 0,
            maxDamage: 0
          },
          current: {
            itemId: "minecraft:oak_log",
            count: 2,
            damage: 0,
            maxDamage: 0
          }
        }
      ]
    }
  });

  assert.equal(result.ok, true);
  if (!result.ok) {
    return;
  }

  assert.equal(result.event.kind, CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION);
  assert.equal(result.event.payload.changedSlots[0]?.current.itemId, "minecraft:oak_log");
});

test("projection reducers retain wood-facing evidence", () => {
  const traces = createWoodTraceSequence();
  const projection = traces.reduce(
    (current, event) => reduceProjectionSnapshot(current, event),
    createEmptyProjectionSnapshot()
  );

  assert.equal(projection.focus.currentTarget?.block?.blockId, "minecraft:oak_log");
  assert.equal(projection.hand.mainHandToolCategory, "axe");
  assert.equal(projection.motion.movementState, "low_motion");
  assert.equal(projection.interactionWindow.recentBreaksByResourceCategory.wood, 2);
  assert.equal(projection.inventoryDelta.recentGainsByResourceCategory.wood, 2);
});

test("inventory projection keeps recent wood gains after later wood losses", () => {
  const projection = [
    createInventoryTransactionTraceEvent(1_000, 1_000, "minecraft:oak_log", 2),
    createInventoryTransactionSlotChangeTraceEvent(
      2_000,
      1_001,
      [createInventorySlotDelta(2, "minecraft:oak_log", 2, null, 0)]
    )
  ].reduce(
    (current, event) => reduceProjectionSnapshot(current, event),
    createEmptyProjectionSnapshot()
  );

  assert.deepEqual(
    projection.inventoryDelta.recentGainedItems.map(item => [item.itemId, item.count]),
    [["minecraft:oak_log", 2]]
  );
  assert.deepEqual(
    projection.inventoryDelta.recentLostItems.map(item => [item.itemId, item.count]),
    [["minecraft:oak_log", 2]]
  );
  assert.equal(projection.inventoryDelta.recentGainsByResourceCategory.wood, 2);
  assert.equal(projection.inventoryDelta.recentLossesByResourceCategory.wood, 2);

  const signals = evaluateDetectors(projection);
  assert.equal(signals.primitives.recentWoodGain.active, true);
  assert.equal(signals.primitives.recentWoodGain.score, 0.5);
});

test("inventory projection ignores within-transaction slot moves", () => {
  const projection = [
    createInventoryTransactionSlotChangeTraceEvent(
      1_000,
      1_000,
      [
        createInventorySlotDelta(2, "minecraft:oak_log", 2, null, 0),
        createInventorySlotDelta(5, null, 0, "minecraft:oak_log", 2)
      ]
    )
  ].reduce(
    (current, event) => reduceProjectionSnapshot(current, event),
    createEmptyProjectionSnapshot()
  );

  assert.deepEqual(projection.inventoryDelta.recentGainedItems, []);
  assert.deepEqual(projection.inventoryDelta.recentLostItems, []);
  assert.equal(projection.inventoryDelta.recentGainsByResourceCategory.wood, undefined);
  assert.equal(projection.inventoryDelta.recentLossesByResourceCategory.wood, undefined);
});

test("wood gathering detector needs more than weak context alone", () => {
  const weakContextProjection = [
    createLookTargetChangedTraceEvent(1_000, 1_000),
    createHandStateChangedTraceEvent(1_050, 1_001, "minecraft:stone_axe"),
    createObservationSampleTraceEvent(1_150, 1_002)
  ].reduce(
    (current, event) => reduceProjectionSnapshot(current, event),
    createEmptyProjectionSnapshot()
  );
  const weakContextSignals = evaluateDetectors(weakContextProjection);

  assert.equal(weakContextSignals.primitives.lookingAtWood.active, true);
  assert.equal(weakContextSignals.primitives.holdingAxe.active, true);
  assert.equal(weakContextSignals.composites.woodGatheringSupport.active, false);

  const strongSignals = evaluateDetectors(
    createWoodTraceSequence().reduce(
      (current, event) => reduceProjectionSnapshot(current, event),
      createEmptyProjectionSnapshot()
    )
  );

  assert.equal(strongSignals.primitives.recentWoodGain.active, true);
  assert.equal(strongSignals.composites.woodGatheringSupport.active, true);
});

test("wood gathering episode opens after sustained support and closes on reset", () => {
  let projection = createEmptyProjectionSnapshot();
  let episodeState = createInitialEpisodeMachineState();

  for (const event of createWoodTraceSequence()) {
    projection = reduceProjectionSnapshot(projection, event);
    episodeState = stepEpisodeMachine(episodeState, {
      event,
      projections: projection,
      detectors: evaluateDetectors(projection)
    });
  }

  const sustainingObservation = createObservationSampleTraceEvent(3_000, 2_100);
  projection = reduceProjectionSnapshot(projection, sustainingObservation);
  episodeState = stepEpisodeMachine(episodeState, {
    event: sustainingObservation,
    projections: projection,
    detectors: evaluateDetectors(projection)
  });

  assert.equal(episodeState.output.woodGathering.state, "active");
  assert.equal(episodeState.output.woodGathering.kind, "resource.gathering.wood");

  const resetEvent = createLookTargetChangedTraceEvent(3_400, 2_200, "minecraft:the_nether");
  projection = reduceProjectionSnapshot(projection, resetEvent);
  episodeState = stepEpisodeMachine(episodeState, {
    event: resetEvent,
    projections: projection,
    detectors: evaluateDetectors(projection)
  });

  assert.equal(episodeState.output.woodGathering.state, "idle");
  assert.equal(episodeState.output.recent.length, 1);
  assert.equal(episodeState.output.recent[0]?.closeReason, "dimension.changed");
});

function createWoodTraceSequence(): readonly RawTraceEvent[] {
  return [
    createLookTargetChangedTraceEvent(1_000, 1_000),
    createHandStateChangedTraceEvent(1_050, 1_001, "minecraft:stone_axe"),
    createObservationSampleTraceEvent(1_100, 1_002),
    createBlockBreakTraceEvent(1_200, 1_003, "minecraft:oak_log"),
    createBlockBreakTraceEvent(1_500, 1_004, "minecraft:oak_log"),
    createInventoryTransactionTraceEvent(1_700, 1_005, "minecraft:oak_log", 2)
  ];
}

function createObservationSampleTraceEvent(
  capturedAtMillis: number,
  seq: number,
  dimensionKey = "minecraft:overworld"
): ObservationSampleTraceEvent {
  return {
    v: 1,
    kind: CURRENT_MOD_TRACE_KIND_OBSERVATION_SAMPLE,
    sessionId: "session-a",
    seq,
    capturedAtMillis,
    payload: {
      worldTick: 300 + seq,
      fps: 144,
      dimensionKey,
      x: 12,
      y: 64,
      z: 4,
      vx: 0.01,
      vy: 0,
      vz: 0.01,
      targetDescription: "block minecraft:oak_log @ 12 64 4"
    }
  };
}

function createLookTargetChangedTraceEvent(
  capturedAtMillis: number,
  seq: number,
  dimensionKey = "minecraft:overworld"
): PlayerLookTargetChangedTraceEvent {
  return {
    v: 1,
    kind: "player.look.target.changed",
    sessionId: "session-a",
    seq,
    capturedAtMillis,
    payload: {
      worldTick: 300 + seq,
      dimensionKey,
      target: {
        kind: "block",
        targetDescription: "block minecraft:oak_log @ 12 64 4",
        block: {
          blockId: "minecraft:oak_log",
          position: {
            x: 12,
            y: 64,
            z: 4
          },
          hitFace: "north"
        }
      }
    }
  };
}

function createHandStateChangedTraceEvent(
  capturedAtMillis: number,
  seq: number,
  mainHandItemId: string,
  dimensionKey = "minecraft:overworld"
): PlayerHandStateChangedTraceEvent {
  return {
    v: 1,
    kind: CURRENT_MOD_TRACE_KIND_PLAYER_HAND_STATE_CHANGED,
    sessionId: "session-a",
    seq,
    capturedAtMillis,
    payload: {
      worldTick: 300 + seq,
      dimensionKey,
      selectedSlot: 0,
      mainHand: {
        itemId: mainHandItemId,
        count: 1,
        damage: 0,
        maxDamage: 131
      },
      offHand: {
        itemId: null,
        count: 0,
        damage: 0,
        maxDamage: 0
      }
    }
  };
}

function createBlockBreakTraceEvent(
  capturedAtMillis: number,
  seq: number,
  blockId: string,
  dimensionKey = "minecraft:overworld"
): InteractionBlockBreakTraceEvent {
  return {
    v: 1,
    kind: "interaction.block.break",
    sessionId: "session-a",
    seq,
    capturedAtMillis,
    payload: {
      worldTick: 300 + seq,
      dimensionKey,
      block: {
        blockId,
        position: {
          x: 12,
          y: 64,
          z: 4
        },
        hitFace: "north"
      },
      hand: "main_hand",
      selectedSlot: 0,
      heldItem: {
        itemId: "minecraft:stone_axe",
        count: 1,
        damage: 0,
        maxDamage: 131
      }
    }
  };
}

function createInventoryTransactionTraceEvent(
  capturedAtMillis: number,
  seq: number,
  gainedItemId: string,
  gainedCount: number,
  dimensionKey = "minecraft:overworld"
): InventoryTransactionTraceEvent {
  return createInventoryTransactionSlotChangeTraceEvent(
    capturedAtMillis,
    seq,
    [createInventorySlotDelta(2, null, 0, gainedItemId, gainedCount)],
    dimensionKey
  );
}

function createInventoryTransactionSlotChangeTraceEvent(
  capturedAtMillis: number,
  seq: number,
  changedSlots: InventoryTransactionTraceEvent["payload"]["changedSlots"],
  dimensionKey = "minecraft:overworld"
): InventoryTransactionTraceEvent {
  return {
    v: 1,
    kind: CURRENT_MOD_TRACE_KIND_INVENTORY_TRANSACTION,
    sessionId: "session-a",
    seq,
    capturedAtMillis,
    payload: {
      worldTick: 300 + seq,
      dimensionKey,
      containerKind: "player_inventory",
      source: "player_inventory.scan",
      changedSlots
    }
  };
}

function createInventorySlotDelta(
  slot: number,
  previousItemId: string | null,
  previousCount: number,
  currentItemId: string | null,
  currentCount: number
): InventoryTransactionTraceEvent["payload"]["changedSlots"][number] {
  return {
    slot,
    previous: {
      itemId: previousItemId,
      count: previousCount,
      damage: 0,
      maxDamage: 0
    },
    current: {
      itemId: currentItemId,
      count: currentCount,
      damage: 0,
      maxDamage: 0
    }
  };
}

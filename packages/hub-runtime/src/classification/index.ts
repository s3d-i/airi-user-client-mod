export type ResourceCategory = "wood";
export type ToolCategory = "axe" | "other";

const WOOD_SUFFIXES = ["_log", "_wood", "_stem", "_hyphae"] as const;
const WOOD_EXACT_IDS = new Set(["minecraft:bamboo_block"]);

export function getBlockResourceCategories(blockId: string | undefined): readonly ResourceCategory[] {
  if (blockId == null) {
    return [];
  }

  return isWoodLikeId(blockId) ? ["wood"] : [];
}

export function getItemResourceCategories(itemId: string | null | undefined): readonly ResourceCategory[] {
  if (itemId == null) {
    return [];
  }

  return isWoodLikeId(itemId) ? ["wood"] : [];
}

export function getToolCategory(itemId: string | null | undefined): ToolCategory | undefined {
  if (itemId == null) {
    return undefined;
  }

  if (itemId.endsWith("_axe")) {
    return "axe";
  }

  return "other";
}

function isWoodLikeId(id: string): boolean {
  return WOOD_EXACT_IDS.has(id) || WOOD_SUFFIXES.some(suffix => id.endsWith(suffix));
}

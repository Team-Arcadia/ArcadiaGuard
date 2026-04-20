// Priority: 900

/*
    Spawn Protection Script
    Prevents entities from taking damage by spells bypassing Yawp in spawn.
    Author: vyrriox

    IMPORTANT: Easy NPC entities must NEVER receive Resistance or Regeneration.
    They must remain killable at all times.
*/

EntityEvents.spawned(event => {
  if (String(event.level.dimension) !== "arcadia:spawn") return;

  let entity = event.entity;
  if (!entity || !entity.isLiving() || entity.isPlayer()) return;

  const entityId = String(entity.type);

  // NEVER protect Easy NPC entities - they must remain killable
  // Check this FIRST before anything else
  if (entityId.startsWith("easy_npc:")) {
    // Aggressively strip any protection effects they might have gotten
    try {
      entity.potionEffects.remove("minecraft:resistance");
      entity.potionEffects.remove("minecraft:regeneration");
      entity.potionEffects.remove("minecraft:invisibility");
    } catch (e) {}
    return; // Skip - do NOT apply any protection
  }

  // Protection for Animals ONLY (Regen + Resistance)
  if (entity.isAnimal()) {
    entity.potionEffects.add("minecraft:resistance", 9999999, 255, false, false);
    entity.potionEffects.add("minecraft:regeneration", 9999999, 255, false, false);
  }
});

// --- Block spawn eggs at spawn ---
ItemEvents.rightClicked(event => {
  if (!event.level || String(event.level.dimension) !== "arcadia:spawn") return;
  if (event.player.isCreative()) return;

  let itemId = String(event.item.id);
  if (itemId.endsWith("_spawn_egg")) {
    event.cancel();
    event.player.tell(Text.red("[Arcadia] Les oeufs de spawn sont interdits ici ! | Spawn eggs are forbidden at spawn!"));
  }
});

// --- Block placing animals at spawn (buckets with fish, etc.) ---
ItemEvents.rightClicked(event => {
  if (!event.level || String(event.level.dimension) !== "arcadia:spawn") return;
  if (event.player.isCreative()) return;

  let itemId = String(event.item.id);
  if (itemId.includes("_bucket") && (
    itemId.includes("fish") || itemId.includes("axolotl") ||
    itemId.includes("tadpole") || itemId.includes("salmon") ||
    itemId.includes("cod") || itemId.includes("pufferfish") ||
    itemId.includes("tropical")
  )) {
    event.cancel();
    event.player.tell(Text.red("[Arcadia] Impossible de relacher des animaux au spawn ! | Cannot release animals at spawn!"));
  }
});

// --- Sculk blocks to block (Catalysis enchantment from Apotheosis spreads these) ---
const SCULK_BLOCKS = new Set([
  "minecraft:sculk", "minecraft:sculk_vein", "minecraft:sculk_catalyst",
  "minecraft:sculk_sensor", "minecraft:sculk_shrieker", "minecraft:calibrated_sculk_sensor"
]);

// --- Block block placement at spawn ---
// Two checks:
// 1. Player placement → block ALL blocks (catches Apotheosis torch enchantment etc.)
// 2. Non-player placement of sculk → block sculk spreading (catches Catalysis enchantment)
BlockEvents.placed(event => {
  if (!event.level || String(event.level.dimension) !== "arcadia:spawn") return;

  // Always block sculk blocks at spawn, regardless of source (Catalysis enchantment)
  let blockId = String(event.block.id);
  if (SCULK_BLOCKS.has(blockId)) {
    event.cancel();
    return;
  }

  // Block all other player-placed blocks (non-creative)
  let player = event.entity;
  if (!player || !player.isPlayer() || player.isCreative()) return;

  event.cancel();
  player.tell(Text.red("[Arcadia] Impossible de poser des blocs au spawn ! | Cannot place blocks at spawn!"));
});

// --- Block block breaking at spawn ---
BlockEvents.broken(event => {
  if (!event.level || String(event.level.dimension) !== "arcadia:spawn") return;
  let player = event.entity;
  if (!player || !player.isPlayer() || player.isCreative()) return;

  event.cancel();
  player.tell(Text.red("[Arcadia] Impossible de casser des blocs au spawn ! | Cannot break blocks at spawn!"));
});

console.info("[Arcadia V2] Spawn Protection Loaded: Animal protection, spawn eggs, block placement/breaking blocked in arcadia:spawn.");

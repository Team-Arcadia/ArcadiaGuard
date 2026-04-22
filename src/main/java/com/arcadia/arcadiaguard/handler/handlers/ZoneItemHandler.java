package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.flag.FlagResolver;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.EntityInteractHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickBlockHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.item.DynamicItemBlockList;
import com.arcadia.arcadiaguard.tag.ArcadiaGuardTags;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Blocks leads, spawn eggs, mob buckets, dynamic items, and NPC interactions
 * in protected zones.
 */
public final class ZoneItemHandler implements RightClickItemHandler, RightClickBlockHandler, EntityInteractHandler,
        com.arcadia.arcadiaguard.handler.HandlerRegistry.BlockBreakHandler {

    private final GuardService guardService;
    private final DynamicItemBlockList dynamicList;

    public ZoneItemHandler(GuardService guardService, DynamicItemBlockList dynamicList) {
        this.guardService = guardService;
        this.dynamicList = dynamicList;
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItemStack();
        BlockPos pos = sp.blockPosition();

        if (ArcadiaGuardConfig.ENABLE_SPAWN_EGG_PROTECTION.get() && isSpawnEgg(stack)
                && checkFlagDenied(sp, pos, BuiltinFlags.SPAWN_EGG)) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                ArcadiaGuardConfig.MESSAGE_SPAWN_EGG.get()).withStyle(net.minecraft.ChatFormatting.RED), true);
            event.setCanceled(true);
            return;
        }

        // MOB_BUCKET: block releasing fish/axolotl/tadpole buckets
        if (isMobBucket(stack) && checkFlagDenied(sp, pos, BuiltinFlags.MOB_BUCKET)) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("arcadiaguard.message.mob_bucket"), true);
            event.setCanceled(true);
            return;
        }

        if (isItemBlockedAt(sp, pos, stack)) {
            // H6: defer "item_use:" + itemId concat until we know it will be used (blockIfProtected checks zone inside)
            String actionName = "item_use:" + itemId(stack);
            if (guardService.blockIfProtected(sp, pos, actionName, "dynamic_item",
                    ArcadiaGuardConfig.MESSAGE_DYNAMIC_ITEM.get()).blocked()) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * S-H20 : un item est bloqu\u00e9 \u00e0 {@code pos} si la zone le contient dans
     * sa liste per-zone {@code blockedItems}, ou si la liste globale legacy
     * {@code DynamicItemBlockList} le contient (fallback pour retro-compat).
     */
    private boolean isItemBlockedAt(ServerPlayer sp, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Optional<ProtectedZone> zoneOpt = guardService.zoneManager().checkZone(sp, pos);
        if (zoneOpt.isPresent() && itemId != null && zoneOpt.get().isItemBlocked(itemId)) {
            return true;
        }
        return dynamicList.contains(stack);
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItemStack();
        BlockPos clicked = event.getPos();
        BlockPos spawnPos = clicked.relative(event.getFace());

        if (ArcadiaGuardConfig.ENABLE_SPAWN_EGG_PROTECTION.get() && isSpawnEgg(stack)) {
            boolean blocked = checkFlagDenied(sp, clicked, BuiltinFlags.SPAWN_EGG)
                || checkFlagDenied(sp, spawnPos, BuiltinFlags.SPAWN_EGG);
            if (blocked) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    ArcadiaGuardConfig.MESSAGE_SPAWN_EGG.get()).withStyle(net.minecraft.ChatFormatting.RED), true);
                event.setCanceled(true);
                return;
            }
        }

        if (ArcadiaGuardConfig.ENABLE_LEAD_PROTECTION.get() && stack.is(ArcadiaGuardTags.BANNED_LEADS)
                && checkFlagDenied(sp, clicked, BuiltinFlags.LEASH)) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                ArcadiaGuardConfig.MESSAGE_LEAD.get()).withStyle(net.minecraft.ChatFormatting.RED), true);
            event.setCanceled(true);
            return;
        }

        // MOB_BUCKET on block (e.g. click water surface)
        if (isMobBucket(stack) && checkFlagDenied(sp, clicked, BuiltinFlags.MOB_BUCKET)) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("arcadiaguard.message.mob_bucket"), true);
            event.setCanceled(true);
            return;
        }

        if (isItemBlockedAt(sp, clicked, stack)) {
            String actionName = "item_use:" + itemId(stack);
            if (guardService.blockIfProtected(sp, clicked, actionName, "dynamic_item",
                    ArcadiaGuardConfig.MESSAGE_DYNAMIC_ITEM.get()).blocked()) {
                event.setCanceled(true);
            }
        }
    }

    @Override
    public void handle(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;

        Entity target = event.getTarget();
        BlockPos pos = target.blockPosition();

        // MOB_BUCKET: block capturing aquatic mobs (fish, axolotl, tadpole) with empty bucket
        if (target instanceof net.minecraft.world.entity.animal.Bucketable) {
            ItemStack held = sp.getMainHandItem().isEmpty() ? sp.getOffhandItem() : sp.getMainHandItem();
            ResourceLocation heldId = BuiltInRegistries.ITEM.getKey(held.getItem());
            if (heldId != null && heldId.getPath().equals("bucket")
                    && checkFlagDenied(sp, pos, BuiltinFlags.MOB_BUCKET)) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("arcadiaguard.message.mob_bucket"), true);
                event.setCanceled(true);
                return;
            }
        }

        // ARS_ADDITIONS_SCROLL : parchemins Ars Additions utilisés via EntityInteract.
        // Utilise l'ItemStack réel de l'event (lié à event.getHand()) — sinon un scroll
        // en offhand est ignoré quand la mainhand tient un autre item.
        ItemStack arsHeld = event.getItemStack();
        if (!arsHeld.isEmpty()) {
            ResourceLocation arsScrollId = BuiltInRegistries.ITEM.getKey(arsHeld.getItem());
            if (arsScrollId != null && "ars_additions".equals(arsScrollId.getNamespace())
                    && checkFlagDenied(sp, pos, BuiltinFlags.ARS_ADDITIONS_SCROLL)) {
                event.setCanceled(true);
                return;
            }
        }

        // NPC_INTERACT: block right-clicking on NPCs (easy_npc mod entities)
        if (isNpc(target) && checkFlagDenied(sp, pos, BuiltinFlags.NPC_INTERACT)) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("arcadiaguard.message.npc_interact"), true);
            event.setCanceled(true);
            return;
        }

        // Lead on mob
        if (!ArcadiaGuardConfig.ENABLE_LEAD_PROTECTION.get()) return;
        ItemStack stack = sp.getMainHandItem().isEmpty() ? sp.getOffhandItem() : sp.getMainHandItem();
        if (!stack.is(ArcadiaGuardTags.BANNED_LEADS)) return;

        if (checkFlagDenied(sp, pos, BuiltinFlags.LEASH)) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                ArcadiaGuardConfig.MESSAGE_LEAD.get()).withStyle(net.minecraft.ChatFormatting.RED), true);
            event.setCanceled(true);
        }
    }

    /** S-H20 / tester feedback : bloque aussi le clic gauche (break block) avec un item banni par zone. */
    @Override
    public void handle(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        ItemStack stack = sp.getMainHandItem();
        if (stack.isEmpty()) return;
        if (isItemBlockedAt(sp, event.getPos(), stack)) {
            String actionName = "item_use:" + itemId(stack);
            if (guardService.blockIfProtected(sp, event.getPos(), actionName, "dynamic_item",
                    ArcadiaGuardConfig.MESSAGE_DYNAMIC_ITEM.get()).blocked()) {
                event.setCanceled(true);
            }
        }
    }

    /** Returns true if the item is a mob-releasing bucket (fish, axolotl, tadpole, etc.). */
    private static boolean isMobBucket(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        String path = id.getPath();
        return path.endsWith("_bucket") && !path.equals("bucket") && !path.equals("water_bucket")
            && !path.equals("lava_bucket") && !path.equals("milk_bucket") && !path.equals("powder_snow_bucket");
    }

    /** M2: cached NPC verdict per EntityType to avoid repeated registry lookup + string ops. */
    private static final java.util.Map<net.minecraft.world.entity.EntityType<?>, Boolean> NPC_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Returns true if the entity is an NPC (easy_npc mod or similar). */
    private static boolean isNpc(Entity entity) {
        return NPC_CACHE.computeIfAbsent(entity.getType(), t -> {
            var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t);
            if (key == null) return false;
            // H-E6: Locale.ROOT for consistent lowercasing across JVM locales
            // M-E2: check path segments precisely to avoid false positives like "zombie_npc_xyz"
            String id = key.toString().toLowerCase(java.util.Locale.ROOT);
            String path = key.getPath().toLowerCase(java.util.Locale.ROOT);
            return id.contains("easy_npc")
                || path.equals("npc")
                || path.endsWith("_npc")
                || path.startsWith("npc_");
        });
    }

    private boolean checkFlagDenied(ServerPlayer player, BlockPos pos,
            com.arcadia.arcadiaguard.api.flag.BooleanFlag flag) {
        if (guardService.shouldBypass(player)) return false;
        Optional<ProtectedZone> zoneOpt = guardService.zoneManager().checkZone(player, pos);
        if (zoneOpt.isEmpty()) return false;
        return !guardService.isFlagAllowedOrUnset(zoneOpt.get(), flag, player.serverLevel());
    }

    private static boolean isSpawnEgg(ItemStack stack) {
        return stack.getItem() instanceof SpawnEggItem || stack.is(ArcadiaGuardTags.BANNED_SPAWN_EGGS);
    }

    private static String itemId(ItemStack stack) {
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null ? key.toString() : "unknown";
    }
}

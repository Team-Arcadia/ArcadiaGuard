package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.EntityInteractHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickBlockHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.item.DynamicItemBlockList;
import com.arcadia.arcadiaguard.tag.ArcadiaGuardTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Blocks leads, spawn eggs, and dynamically registered items in protected zones.
 *
 * Spawn eggs: detected via instanceof SpawnEggItem (covers vanilla + modded) or
 *             the arcadiaguard:banned_spawn_eggs tag (fallback for non-standard eggs).
 *             Both the clicked block AND the face block are checked, since the mob
 *             actually spawns on the face of the clicked block.
 *
 * Leads: arcadiaguard:banned_leads tag (vanilla + Apothic Enchanting ender leads).
 *        Intercepted on fence-post click (RightClickBlock) and mob leashing (EntityInteract).
 *
 * Dynamic items: managed via /arcadiaguard item block|unblock|list.
 */
public final class ZoneItemHandler implements RightClickItemHandler, RightClickBlockHandler, EntityInteractHandler {

    private final GuardService guardService;
    private final DynamicItemBlockList dynamicList;

    public ZoneItemHandler(GuardService guardService, DynamicItemBlockList dynamicList) {
        this.guardService = guardService;
        this.dynamicList = dynamicList;
    }

    // RightClickItem fires when using an item in the air (no block hit).
    // Spawn eggs don't do anything in air in vanilla, but we block just in case.
    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItemStack();
        BlockPos pos = sp.blockPosition();

        if (ArcadiaGuardConfig.ENABLE_SPAWN_EGG_PROTECTION.get() && isSpawnEgg(stack)) {
            if (guardService.blockIfProtected(sp, pos, "spawn_egg_use", "spawn_egg_protection",
                    ArcadiaGuardConfig.MESSAGE_SPAWN_EGG.get()).blocked()) {
                event.setCanceled(true);
                return;
            }
        }

        if (dynamicList.contains(stack)) {
            if (guardService.blockIfProtected(sp, pos, "item_use:" + itemId(stack), "dynamic_item",
                    ArcadiaGuardConfig.MESSAGE_DYNAMIC_ITEM.get()).blocked()) {
                event.setCanceled(true);
            }
        }
    }

    // RightClickBlock: spawn eggs, leads on fence post, dynamic items.
    // For spawn eggs, the mob spawns either at the clicked block (if passable)
    // or on the clicked face — we check BOTH positions.
    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItemStack();
        BlockPos clicked = event.getPos();
        BlockPos spawnPos = clicked.relative(event.getFace()); // actual mob spawn position

        if (ArcadiaGuardConfig.ENABLE_SPAWN_EGG_PROTECTION.get() && isSpawnEgg(stack)) {
            boolean blockedAtClick  = guardService.blockIfProtected(sp, clicked,  "spawn_egg_use", "spawn_egg_protection", ArcadiaGuardConfig.MESSAGE_SPAWN_EGG.get()).blocked();
            boolean blockedAtSpawn  = !blockedAtClick && guardService.blockIfProtected(sp, spawnPos, "spawn_egg_use", "spawn_egg_protection", ArcadiaGuardConfig.MESSAGE_SPAWN_EGG.get()).blocked();
            if (blockedAtClick || blockedAtSpawn) {
                event.setCanceled(true);
                return;
            }
        }

        if (ArcadiaGuardConfig.ENABLE_LEAD_PROTECTION.get() && stack.is(ArcadiaGuardTags.BANNED_LEADS)) {
            if (guardService.blockIfProtected(sp, clicked, "lead_use", "lead_protection",
                    ArcadiaGuardConfig.MESSAGE_LEAD.get()).blocked()) {
                event.setCanceled(true);
                return;
            }
        }

        if (dynamicList.contains(stack)) {
            if (guardService.blockIfProtected(sp, clicked, "item_use:" + itemId(stack), "dynamic_item",
                    ArcadiaGuardConfig.MESSAGE_DYNAMIC_ITEM.get()).blocked()) {
                event.setCanceled(true);
            }
        }
    }

    // Leads used on a mob (right-click entity to leash)
    @Override
    public void handle(PlayerInteractEvent.EntityInteract event) {
        if (!ArcadiaGuardConfig.ENABLE_LEAD_PROTECTION.get()) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;

        ItemStack stack = sp.getMainHandItem().isEmpty() ? sp.getOffhandItem() : sp.getMainHandItem();
        if (!stack.is(ArcadiaGuardTags.BANNED_LEADS)) return;

        BlockPos pos = event.getTarget().blockPosition();
        if (guardService.blockIfProtected(sp, pos, "lead_use", "lead_protection",
                ArcadiaGuardConfig.MESSAGE_LEAD.get()).blocked()) {
            event.setCanceled(true);
        }
    }

    /**
     * True for vanilla spawn eggs (SpawnEggItem subclass) and any item in the
     * arcadiaguard:banned_spawn_eggs tag (fallback for non-standard modded eggs).
     */
    private static boolean isSpawnEgg(ItemStack stack) {
        return stack.getItem() instanceof SpawnEggItem || stack.is(ArcadiaGuardTags.BANNED_SPAWN_EGGS);
    }

    private static String itemId(ItemStack stack) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null ? key.toString() : "unknown";
    }
}

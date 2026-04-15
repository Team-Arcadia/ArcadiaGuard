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
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Blocks leads, spawn eggs, and dynamically registered items in protected zones.
 *
 * Static item lists use tags (overridable via datapack):
 *   arcadiaguard:banned_leads
 *   arcadiaguard:banned_spawn_eggs
 *
 * Dynamic items are managed in-game via:
 *   /arcadiaguard item block <id>
 *   /arcadiaguard item unblock <id>
 */
public final class ZoneItemHandler implements RightClickItemHandler, RightClickBlockHandler, EntityInteractHandler {

    private final GuardService guardService;
    private final DynamicItemBlockList dynamicList;

    public ZoneItemHandler(GuardService guardService, DynamicItemBlockList dynamicList) {
        this.guardService = guardService;
        this.dynamicList = dynamicList;
    }

    // Items used in the air (spawn eggs at feet, dynamic items)
    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItemStack();
        BlockPos pos = sp.blockPosition();

        if (ArcadiaGuardConfig.ENABLE_SPAWN_EGG_PROTECTION.get() && stack.is(ArcadiaGuardTags.BANNED_SPAWN_EGGS)) {
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

    // Items used on a block (spawn eggs, leads on fence post, dynamic items)
    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItemStack();
        BlockPos pos = event.getPos();

        if (ArcadiaGuardConfig.ENABLE_SPAWN_EGG_PROTECTION.get() && stack.is(ArcadiaGuardTags.BANNED_SPAWN_EGGS)) {
            if (guardService.blockIfProtected(sp, pos, "spawn_egg_use", "spawn_egg_protection",
                    ArcadiaGuardConfig.MESSAGE_SPAWN_EGG.get()).blocked()) {
                event.setCanceled(true);
                return;
            }
        }

        if (ArcadiaGuardConfig.ENABLE_LEAD_PROTECTION.get() && stack.is(ArcadiaGuardTags.BANNED_LEADS)) {
            if (guardService.blockIfProtected(sp, pos, "lead_use", "lead_protection",
                    ArcadiaGuardConfig.MESSAGE_LEAD.get()).blocked()) {
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

    private static String itemId(ItemStack stack) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null ? key.toString() : "unknown";
    }
}

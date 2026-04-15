package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.EntityInteractHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickBlockHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.tag.ArcadiaGuardTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Blocks leads (vanilla + Apothic Enchanting ender leads) and vanilla spawn eggs
 * in protected zones. Item lists are driven by item tags and overridable via datapack:
 *   arcadiaguard:banned_leads
 *   arcadiaguard:banned_spawn_eggs
 */
public final class ZoneItemHandler implements RightClickItemHandler, RightClickBlockHandler, EntityInteractHandler {

    private final GuardService guardService;

    public ZoneItemHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    // Spawn eggs used in the air (fallback, spawns mob at feet)
    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItemStack();

        if (ArcadiaGuardConfig.ENABLE_SPAWN_EGG_PROTECTION.get() && stack.is(ArcadiaGuardTags.BANNED_SPAWN_EGGS)) {
            if (guardService.blockIfProtected(sp, sp.blockPosition(), "spawn_egg_use", "spawn_egg_protection",
                    ArcadiaGuardConfig.MESSAGE_SPAWN_EGG.get()).blocked()) {
                event.setCanceled(true);
            }
        }
    }

    // Spawn eggs on block + leads on fence post
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
}

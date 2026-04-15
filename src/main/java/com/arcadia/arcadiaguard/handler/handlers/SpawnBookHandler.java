package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickBlockHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.tag.ArcadiaGuardTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Blocks players from using books (tag: arcadiaguard:spawn_banned_books)
 * inside protected zones, using the same zone system as all other handlers.
 * The item list is overridable via datapack.
 */
public final class SpawnBookHandler implements RightClickItemHandler, RightClickBlockHandler {

    private final GuardService guardService;

    public SpawnBookHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        if (!ArcadiaGuardConfig.ENABLE_SPAWN_BOOK_PROTECTION.get()) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        if (!event.getItemStack().is(ArcadiaGuardTags.SPAWN_BANNED_BOOKS)) return;

        BlockPos pos = sp.blockPosition();
        if (this.guardService.blockIfProtected(sp, pos, "book_use", "spawn_book_protection",
                ArcadiaGuardConfig.MESSAGE_SPAWN_BOOK.get()).blocked()) {
            event.setCanceled(true);
        }
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        if (!ArcadiaGuardConfig.ENABLE_SPAWN_BOOK_PROTECTION.get()) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        if (!event.getItemStack().is(ArcadiaGuardTags.SPAWN_BANNED_BOOKS)) return;

        BlockPos pos = event.getPos();
        if (this.guardService.blockIfProtected(sp, pos, "book_use", "spawn_book_protection",
                ArcadiaGuardConfig.MESSAGE_SPAWN_BOOK.get()).blocked()) {
            event.setCanceled(true);
        }
    }
}

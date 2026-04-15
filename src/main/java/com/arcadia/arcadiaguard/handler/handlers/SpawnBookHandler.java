package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickBlockHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.tag.ArcadiaGuardTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Blocks players from using books in a configurable dimension.
 *
 * Which items are banned is driven by the item tag arcadiaguard:spawn_banned_books,
 * overridable server-side via datapack without any code change.
 * The target dimension is set in arcadiaguard-common.toml (spawn_book_dimension).
 */
public final class SpawnBookHandler implements RightClickItemHandler, RightClickBlockHandler {

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        if (shouldBlock(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            sendMessage(event.getEntity());
        }
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        if (shouldBlock(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            sendMessage(event.getEntity());
        }
    }

    private static boolean shouldBlock(Player player, ItemStack stack) {
        if (!ArcadiaGuardConfig.ENABLE_SPAWN_BOOK_PROTECTION.get()) return false;
        if (!(player instanceof ServerPlayer)) return false;
        if (player.getAbilities().instabuild) return false; // creative bypass
        if (!isTargetDimension(player)) return false;
        return stack.is(ArcadiaGuardTags.SPAWN_BANNED_BOOKS);
    }

    private static boolean isTargetDimension(Player player) {
        String target = ArcadiaGuardConfig.SPAWN_BOOK_DIMENSION.get();
        return target.equals(player.level().dimension().location().toString());
    }

    private static void sendMessage(Player player) {
        player.sendSystemMessage(Component.literal(
            "\u00A7c" + ArcadiaGuardConfig.MESSAGE_SPAWN_BOOK.get()));
    }
}

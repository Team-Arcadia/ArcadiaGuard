package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickBlockHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Blocks players from using books (vanilla books that can be used for duplication)
 * in the arcadia:spawn dimension.
 */
public final class SpawnBookHandler implements RightClickItemHandler, RightClickBlockHandler {

    private static final String SPAWN_DIMENSION = "arcadia:spawn";

    private static final Set<String> BANNED_BOOKS = Set.of(
        "minecraft:book",
        "minecraft:enchanted_book",
        "minecraft:written_book",
        "minecraft:writable_book"
    );

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        if (!ArcadiaGuardConfig.ENABLE_SPAWN_BOOK_PROTECTION.get()) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) return;
        if (player.getAbilities().instabuild) return; // creative players bypass
        if (!isSpawnDimension(player)) return;
        if (!isBannedBook(event.getItemStack())) return;

        event.setCanceled(true);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "\u00A7c" + ArcadiaGuardConfig.MESSAGE_SPAWN_BOOK.get()));
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        if (!ArcadiaGuardConfig.ENABLE_SPAWN_BOOK_PROTECTION.get()) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) return;
        if (player.getAbilities().instabuild) return; // creative players bypass
        if (!isSpawnDimension(player)) return;
        if (!isBannedBook(event.getItemStack())) return;

        event.setCanceled(true);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "\u00A7c" + ArcadiaGuardConfig.MESSAGE_SPAWN_BOOK.get()));
    }

    private static boolean isSpawnDimension(Player player) {
        return SPAWN_DIMENSION.equals(player.level().dimension().location().toString());
    }

    private static boolean isBannedBook(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && BANNED_BOOKS.contains(key.toString());
    }
}

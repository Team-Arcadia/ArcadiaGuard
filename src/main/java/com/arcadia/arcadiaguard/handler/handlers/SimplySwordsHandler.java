package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Blocks Simply Swords item abilities in zones with simplyswords-ability=deny.
 */
public final class SimplySwordsHandler implements RightClickItemHandler {

    private final GuardService guardService;

    public SimplySwordsHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        if (!ArcadiaGuardConfig.ENABLE_SIMPLYSWORDS.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemStack();
        ResourceLocation key = itemKey(stack);
        if (key == null || !"simplyswords".equals(key.getNamespace())) return;

        if (guardService.shouldBypass(player)) return;
        Optional<ProtectedZone> zoneOpt = guardService.zoneManager()
            .checkZone(player, player.blockPosition());
        if (zoneOpt.isEmpty()) return;

        ProtectedZone zone = zoneOpt.get();
        boolean abilityAllowed = guardService.isFlagAllowedOrUnset(zone, BuiltinFlags.SIMPLYSWORDS_ABILITY, player.serverLevel());
        if (!abilityAllowed) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "arcadiaguard.message.simplyswords").withStyle(net.minecraft.ChatFormatting.RED));
            event.setCanceled(true);
        }
    }

    private static ResourceLocation itemKey(ItemStack stack) {
        ResourceKey<Item> k = stack.getItemHolder().getKey();
        return k != null ? k.location() : null;
    }
}

package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class SimplySwordsHandler implements RightClickItemHandler {

    private final GuardService guardService;

    public SimplySwordsHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        if (!ArcadiaGuardConfig.ENABLE_SIMPLYSWORDS.get()) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        if (!(entity instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        ResourceLocation key = stack.getItemHolder().unwrapKey().map(k -> k.location()).orElse(null);
        if (key == null || !"simplyswords".equals(key.getNamespace())) return;
        Object pos = ReflectionHelper.invoke(player, "blockPosition", new Class<?>[0]).orElse(null);
        if (pos instanceof net.minecraft.core.BlockPos blockPos
            && this.guardService.blockIfProtected(player, blockPos, key.toString(), "simplyswords", ArcadiaGuardConfig.MESSAGE_SIMPLYSWORDS.get()).blocked()) {
            event.setCanceled(true);
        }
    }
}

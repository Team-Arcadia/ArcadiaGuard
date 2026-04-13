package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.DynamicEventHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class ArsNouveauHandler implements DynamicEventHandler, RightClickItemHandler {

    private static final String EVENT_CLASS = "com.hollingsworth.arsnouveau.api.event.SpellCastEvent";
    private final GuardService guardService;

    public ArsNouveauHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public String eventClassName() {
        return EVENT_CLASS;
    }

    @Override
    public void handle(Event event) {
        if (!ArcadiaGuardConfig.ENABLE_ARS_NOUVEAU.get()) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        if (entity instanceof ServerPlayer player) {
            String action = ReflectionHelper.field(event, "spell").map(Object::toString).orElse("ars_spell");
            Object pos = ReflectionHelper.invoke(player, "blockPosition", new Class<?>[0]).orElse(null);
            if (pos instanceof net.minecraft.core.BlockPos blockPos
                && this.guardService.blockIfProtected(player, blockPos, action, "arsnouveau", ArcadiaGuardConfig.MESSAGE_ARS_NOUVEAU.get()).blocked()
                && event instanceof ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
        }
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        if (!ArcadiaGuardConfig.ENABLE_ARS_NOUVEAU.get()) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        if (!(entity instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemStack();
        ResourceLocation key = itemKey(stack);
        if (key == null || !"ars_nouveau".equals(key.getNamespace())) return;

        String path = key.getPath();
        if (!"warp_scroll".equals(path) && !"stable_warp_scroll".equals(path)) return;

        Object pos = ReflectionHelper.invoke(player, "blockPosition", new Class<?>[0]).orElse(null);
        if (!(pos instanceof net.minecraft.core.BlockPos blockPos)) return;

        if (this.guardService.blockIfProtected(
            player,
            blockPos,
            key.toString(),
            "arsnouveau",
            ArcadiaGuardConfig.MESSAGE_ARS_NOUVEAU.get()
        ).blocked()) {
            event.setCanceled(true);
        }
    }

    private static ResourceLocation itemKey(ItemStack stack) {
        ResourceKey<Item> itemKey = stack.getItemHolder().getKey();
        return itemKey != null ? itemKey.location() : null;
    }
}

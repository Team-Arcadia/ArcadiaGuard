package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.DynamicEventHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickBlockHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class ArsNouveauHandler implements DynamicEventHandler, RightClickItemHandler, RightClickBlockHandler {

    private static final String EVENT_CLASS = "com.hollingsworth.arsnouveau.api.event.SpellCastEvent";
    private final GuardService guardService;

    public ArsNouveauHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public String eventClassName() {
        return EVENT_CLASS;
    }

    // SpellCastEvent — intercepte les sorts Ars Nouveau
    @Override
    public void handle(Event event) {
        if (!ArcadiaGuardConfig.ENABLE_ARS_NOUVEAU.get()) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        if (entity instanceof ServerPlayer player) {
            String action = ReflectionHelper.field(event, "spell").map(Object::toString).orElse("ars_spell");
            Object pos = ReflectionHelper.invoke(player, "blockPosition", new Class<?>[0]).orElse(null);
            if (pos instanceof BlockPos blockPos
                && this.guardService.blockIfProtected(player, blockPos, action, "arsnouveau", ArcadiaGuardConfig.MESSAGE_ARS_NOUVEAU.get()).blocked()
                && event instanceof ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
        }
    }

    // WarpScroll (normal) : use() téléporte le joueur vers la destination stockée.
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

        // Si le scroll a une destination → vérifier que la destination n'est pas protégée.
        BlockPos destination = warpScrollDestination(stack);
        if (destination != null) {
            if (this.guardService.blockIfProtected(player, destination, key.toString(), "arsnouveau", ArcadiaGuardConfig.MESSAGE_ARS_NOUVEAU.get()).blocked()) {
                event.setCanceled(true);
                return;
            }
        }

        // Shift+use pour enregistrer sa position → empêcher l'enregistrement en zone protégée.
        if (player.isShiftKeyDown()) {
            Object pos = ReflectionHelper.invoke(player, "blockPosition", new Class<?>[0]).orElse(null);
            if (pos instanceof BlockPos blockPos
                && this.guardService.blockIfProtected(player, blockPos, key.toString(), "arsnouveau", ArcadiaGuardConfig.MESSAGE_ARS_NOUVEAU.get()).blocked()) {
                event.setCanceled(true);
            }
        }
    }

    // StableWarpScroll.useOn() : crée un portail à la position du bloc cliqué (RightClickBlock).
    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        if (!ArcadiaGuardConfig.ENABLE_ARS_NOUVEAU.get()) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        if (!(entity instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemStack();
        ResourceLocation key = itemKey(stack);
        if (key == null || !"ars_nouveau".equals(key.getNamespace())) return;
        if (!"stable_warp_scroll".equals(key.getPath())) return;

        Object hitResult = ReflectionHelper.invoke(event, "getHitVec", new Class<?>[0]).orElse(null);
        Object blockPos = hitResult == null ? null : ReflectionHelper.invoke(hitResult, "getBlockPos", new Class<?>[0]).orElse(null);
        if (!(blockPos instanceof BlockPos pos)) return;

        if (this.guardService.blockIfProtected(player, pos, key.toString(), "arsnouveau", ArcadiaGuardConfig.MESSAGE_ARS_NOUVEAU.get()).blocked()) {
            event.setCanceled(true);
        }
    }

    private BlockPos warpScrollDestination(ItemStack stack) {
        Object componentType = ReflectionHelper.field(
            "com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry",
            "WARP_SCROLL"
        ).orElse(null);
        if (!(componentType instanceof DataComponentType<?> type)) return null;
        Object warpScrollData = stack.get(type);
        if (warpScrollData == null) return null;
        if (!Boolean.TRUE.equals(ReflectionHelper.invoke(warpScrollData, "isValid", new Class<?>[0]).orElse(Boolean.FALSE))) return null;
        Object optionalPos = ReflectionHelper.invoke(warpScrollData, "pos", new Class<?>[0]).orElse(null);
        if (optionalPos == null) return null;
        Object posValue = ReflectionHelper.invoke(optionalPos, "get", new Class<?>[0]).orElse(null);
        return posValue instanceof BlockPos bp ? bp : null;
    }

    private static ResourceLocation itemKey(ItemStack stack) {
        ResourceKey<Item> itemKey = stack.getItemHolder().getKey();
        return itemKey != null ? itemKey.location() : null;
    }
}

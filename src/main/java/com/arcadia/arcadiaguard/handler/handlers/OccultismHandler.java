package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.EntityInteractHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickBlockHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class OccultismHandler implements RightClickItemHandler, RightClickBlockHandler, EntityInteractHandler {

    private final GuardService guardService;

    public OccultismHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        if (!ArcadiaGuardConfig.ENABLE_OCCULTISM.get()) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        if (!(entity instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        ResourceLocation key = stack.getItemHolder().unwrapKey().map(k -> k.location()).orElse(null);
        if (key == null || !"occultism".equals(key.getNamespace())) return;
        Object pos = ReflectionHelper.invoke(player, "blockPosition", new Class<?>[0]).orElse(null);
        if (pos instanceof net.minecraft.core.BlockPos blockPos
                && denyOccultism(player, blockPos)) {
            event.setCanceled(true);
        }
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        if (!ArcadiaGuardConfig.ENABLE_OCCULTISM.get()) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        if (!(entity instanceof ServerPlayer player)) return;
        Object pos = ReflectionHelper.invoke(event, "getPos", new Class<?>[0]).orElse(null);
        if (!(pos instanceof net.minecraft.core.BlockPos blockPos)) return;
        Object level = ReflectionHelper.invoke(event, "getLevel", new Class<?>[0]).orElse(null);
        if (level == null) return;
        Object state = ReflectionHelper.invoke(level, "getBlockState", new Class<?>[] { pos.getClass() }, pos).orElse(null);
        Object holder = state == null ? null : ReflectionHelper.invoke(state, "getBlockHolder", new Class<?>[0]).orElse(null);
        Object keyOpt = holder == null ? null : ReflectionHelper.invoke(holder, "unwrapKey", new Class<?>[0]).orElse(null);
        Object key = keyOpt == null ? null : ReflectionHelper.invoke(keyOpt, "orElseThrow", new Class<?>[0]).orElse(null);
        Object location = key == null ? null : ReflectionHelper.invoke(key, "location", new Class<?>[0]).orElse(null);
        if (!(location instanceof ResourceLocation blockKey)) return;
        if (!"occultism".equals(blockKey.getNamespace())) return;
        if (!blockKey.getPath().contains("bowl") && !blockKey.getPath().contains("chalice")) return;
        if (denyOccultism(player, blockPos)) {
            event.setCanceled(true);
        }
    }

    @Override
    public void handle(PlayerInteractEvent.EntityInteract event) {
        if (!ArcadiaGuardConfig.ENABLE_OCCULTISM.get()) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        if (!(entity instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        ResourceLocation key = stack.getItemHolder().unwrapKey().map(k -> k.location()).orElse(null);
        if (key == null || !"occultism".equals(key.getNamespace())) return;
        if (!isSoulGem(key)) return;
        Object target = ReflectionHelper.invoke(event, "getTarget", new Class<?>[0]).orElse(null);
        Object pos = target == null ? null : ReflectionHelper.invoke(target, "blockPosition", new Class<?>[0]).orElse(null);
        if (pos instanceof net.minecraft.core.BlockPos blockPos && denyOccultism(player, blockPos)) {
            ReflectionHelper.invoke(event, "setCancellationResult", new Class<?>[] { InteractionResult.class }, InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    private boolean denyOccultism(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        if (guardService.shouldBypass(player)) return false;
        Optional<ProtectedZone> zoneOpt = guardService.zoneManager().findZoneContaining(player.serverLevel(), pos)
            .map(z -> (ProtectedZone) z);
        if (zoneOpt.isPresent() && guardService.isZoneMember(player, zoneOpt.get())) return false;
        if (!guardService.isZoneDenying(player.serverLevel(), pos, BuiltinFlags.OCCULTISM_USE)) return false;
        player.displayClientMessage(
            net.minecraft.network.chat.Component.translatable(ArcadiaGuardConfig.MESSAGE_OCCULTISM.get())
                .withStyle(net.minecraft.ChatFormatting.RED), true);
        String zoneName = zoneOpt.map(ProtectedZone::name).orElse("(dimension)");
        guardService.auditDenied(player, zoneName, pos, BuiltinFlags.OCCULTISM_USE, "occultism_use");
        return true;
    }

    private boolean isSoulGem(ResourceLocation key) {
        return key.getPath().equals("soul_gem") || key.getPath().equals("fragile_soul_gem") || key.getPath().equals("trinity_gem");
    }
}

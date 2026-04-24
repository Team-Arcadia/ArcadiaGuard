package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * S-H16 T1 : bloque l'acces aux conteneurs Sophisticated Storage
 * (coffres / barrels / shulkers Sophisticated). Leurs BlockEntity
 * implementent bien {@code MenuProvider} mais leur {@code use()}
 * peut ouvrir l'UI via des paths specifiques (shift-click avec
 * upgrade item) qui contournent le check generique CONTAINER_ACCESS.
 *
 * Ce handler s'execute en priority HIGHEST avant tout le reste.
 */
public final class SophisticatedStorageHandler {

    private final GuardService guardService;

    public SophisticatedStorageHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;
        if (!ModList.get().isLoaded("sophisticatedstorage")
                && !ModList.get().isLoaded("sophisticatedbackpacks")) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (guardService.shouldBypass(player)) return;

        Level level = player.level();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) return;
        String ns = blockId.getNamespace();
        if (!"sophisticatedstorage".equals(ns) && !"sophisticatedbackpacks".equals(ns)) return;

        var zoneOpt = guardService.zoneManager().findZoneContaining(level, pos)
            .map(z -> (com.arcadia.arcadiaguard.zone.ProtectedZone) z);
        if (zoneOpt.isPresent() && guardService.isZoneMember(player, zoneOpt.get())) return;
        if (guardService.isZoneDenying(level, pos, BuiltinFlags.CONTAINER_ACCESS)) {
            event.setCanceled(true);
            String zoneName = zoneOpt.map(com.arcadia.arcadiaguard.zone.ProtectedZone::name).orElse("(dimension)");
            guardService.auditDenied(player, zoneName, pos, BuiltinFlags.CONTAINER_ACCESS, "container_access");
        }
    }

    /**
     * S-H16 AC1 : couvre les minecarts-coffres (ChestMinecart, HopperMinecart, ender) et
     * les chest-boats qui sont des Entity, pas des BlockEntity — le check
     * {@code onRightClickBlock} ne les voit pas. Applique CONTAINER_ACCESS
     * sur l'interaction avec ces entites-conteneurs.
     */
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (guardService.shouldBypass(player)) return;
        Entity target = event.getTarget();
        boolean isContainerEntity = target instanceof AbstractMinecartContainer
                || target instanceof ChestBoat;
        if (!isContainerEntity) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        BlockPos tpos = target.blockPosition();
        var zoneOpt = guardService.zoneManager().findZoneContaining(level, tpos)
            .map(z -> (com.arcadia.arcadiaguard.zone.ProtectedZone) z);
        if (zoneOpt.isPresent() && guardService.isZoneMember(player, zoneOpt.get())) return;
        if (guardService.isZoneDenying(level, tpos, BuiltinFlags.CONTAINER_ACCESS)) {
            event.setCanceled(true);
            String zoneName = zoneOpt.map(com.arcadia.arcadiaguard.zone.ProtectedZone::name).orElse("(dimension)");
            guardService.auditDenied(player, zoneName, tpos, BuiltinFlags.CONTAINER_ACCESS, "container_access");
        }
    }
}

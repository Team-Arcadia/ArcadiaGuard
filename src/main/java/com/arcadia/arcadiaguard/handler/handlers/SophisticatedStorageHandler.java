package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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

        if (guardService.isZoneDenying(level, pos, BuiltinFlags.CONTAINER_ACCESS)) {
            event.setCanceled(true);
        }
    }
}

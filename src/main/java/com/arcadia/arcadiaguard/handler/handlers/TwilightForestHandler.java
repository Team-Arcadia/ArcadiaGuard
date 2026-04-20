package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * Blocks Twilight Forest projectiles (IceBomb, LichBomb, HydraMortar, etc.) from
 * hitting inside protected zones when TF_PROJECTILE=deny.
 * Uses namespace filtering — no compile-time dependency on TF.
 */
public final class TwilightForestHandler {

    private final GuardService guardService;

    public TwilightForestHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!ModList.get().isLoaded("twilightforest")) return;
        Entity projectile = event.getProjectile();
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());
        if (type == null || !"twilightforest".equals(type.getNamespace())) return;

        Level level = (Level) projectile.level();
        if (level.isClientSide()) return;

        HitResult hit = event.getRayTraceResult();
        BlockPos pos;
        if (hit instanceof BlockHitResult bhr) {
            pos = bhr.getBlockPos();
        } else {
            pos = projectile.blockPosition();
        }

        if (guardService.isZoneDenying(level, pos, BuiltinFlags.TF_PROJECTILE)) {
            event.setCanceled(true);
            projectile.discard();
        }
    }
}

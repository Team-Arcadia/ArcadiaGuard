package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Prevents mutant monsters (Mutant Monsters mod) from spawning inside protected zones
 * where MUTANT_MOB_SPAWN=deny. Uses namespace filtering — no compile-time dependency.
 */
public final class MutantMonstersHandler {

    private final GuardService guardService;

    public MutantMonstersHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!ModList.get().isLoaded("mutantmonsters")) return;
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (type == null || !"mutantmonsters".equals(type.getNamespace())) return;
        Level level = (Level) event.getLevel();
        if (guardService.isZoneDenying(level, entity.blockPosition(), BuiltinFlags.MUTANT_MOB_SPAWN)) {
            event.setCanceled(true);
        }
    }
}

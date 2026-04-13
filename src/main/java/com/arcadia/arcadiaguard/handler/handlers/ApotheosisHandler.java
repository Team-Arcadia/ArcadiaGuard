package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.BlockBreakHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickBlockHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class ApotheosisHandler implements BlockBreakHandler, RightClickBlockHandler {

    private static final String RADIAL_AFFIX_CLASS = "dev.shadowsoffire.apotheosis.affix.effect.RadialAffix";
    private static final String RADIAL_BONUS_CLASS = "dev.shadowsoffire.apotheosis.socket.gem.bonus.special.RadialBonus";
    private static final String RADIAL_UTIL_CLASS = "dev.shadowsoffire.apotheosis.util.RadialUtil";
    private final GuardService guardService;

    public ApotheosisHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void handle(BlockEvent.BreakEvent event) {
        if (!ArcadiaGuardConfig.ENABLE_APOTHEOSIS_ENCHANTS.get()) return;
        if (!ModList.get().isLoaded("apotheosis")) return;
        Object playerObj = ReflectionHelper.invoke(event, "getPlayer", new Class<?>[0]).orElse(null);
        if (!(playerObj instanceof ServerPlayer player)) return;
        Object posObj = ReflectionHelper.invoke(event, "getPos", new Class<?>[0]).orElse(null);
        if (!(posObj instanceof BlockPos pos)) return;
        Object playerRef = playerObj;
        Object posRef = posObj;

        Object toolObj = ReflectionHelper.invoke(player, "getMainHandItem", new Class<?>[0]).orElse(ItemStack.EMPTY);
        if (!(toolObj instanceof ItemStack tool) || tool.isEmpty()) return;

        Object radialData = radialData(tool);
        if (radialData == null) return;

        if (this.guardService.blockIfProtected(player, pos, "apotheosis:tunneling", "apotheosis", ArcadiaGuardConfig.MESSAGE_APOTHEOSIS.get()).blocked()) {
            event.setCanceled(true);
            return;
        }

        Object hit = ReflectionHelper.invokeStatic(RADIAL_UTIL_CLASS, "tracePlayerLook", new Class<?>[] { playerRef.getClass() }, playerRef).orElse(null);
        Object direction = hit == null ? null : ReflectionHelper.invoke(hit, "getDirection", new Class<?>[0]).orElse(null);
        if (direction == null) return;

        Object broken = ReflectionHelper.invokeStatic(RADIAL_UTIL_CLASS, "getBrokenBlocks", new Class<?>[] { playerRef.getClass(), direction.getClass(), posRef.getClass(), radialData.getClass() }, playerRef, direction, posRef, radialData).orElse(List.of());
        if (!(broken instanceof Collection<?> positions)) return;
        for (Object extraPos : positions) {
            if (!(extraPos instanceof BlockPos blockPos)) continue;
            if (this.guardService.blockIfProtected(player, blockPos, "apotheosis:tunneling", "apotheosis", ArcadiaGuardConfig.MESSAGE_APOTHEOSIS.get()).blocked()) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // Affix "Enlightened" — pose une torche via RightClickBlock en échange de durabilité.
    // L'outil en main est n'importe quel item avec un affix Apotheosis (pioche vanilla, etc.)
    // → on vérifie la présence du data component "apotheosis:affixes", pas le namespace de l'item.
    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        if (!ArcadiaGuardConfig.ENABLE_APOTHEOSIS_ENCHANTS.get()) return;
        if (!ModList.get().isLoaded("apotheosis")) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        if (!(entity instanceof ServerPlayer player)) return;

        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;

        // Vérifie si l'item a des affixes Apotheosis via le data component "apotheosis:affixes"
        if (!hasApotheosisAffixes(tool)) return;

        Object hitResult = ReflectionHelper.invoke(event, "getHitVec", new Class<?>[0]).orElse(null);
        Object blockPos = hitResult == null ? null : ReflectionHelper.invoke(hitResult, "getBlockPos", new Class<?>[0]).orElse(null);
        if (!(blockPos instanceof BlockPos pos)) return;

        if (this.guardService.blockIfProtected(player, pos, "apotheosis:enlightened", "apotheosis", ArcadiaGuardConfig.MESSAGE_APOTHEOSIS.get()).blocked()) {
            event.setCanceled(true);
        }
    }

    private boolean hasApotheosisAffixes(ItemStack stack) {
        // AffixHelper.hasAffixes(ItemStack) — retourne true si l'item a des affixes Apotheosis.
        // L'outil peut être n'importe quel item (pioche vanilla, etc.) avec un data component Apotheosis.
        Object result = ReflectionHelper.invokeStatic(
            "dev.shadowsoffire.apotheosis.affix.AffixHelper",
            "hasAffixes",
            new Class<?>[] { ItemStack.class },
            stack
        ).orElse(Boolean.FALSE);
        return Boolean.TRUE.equals(result);
    }

    private Object radialData(ItemStack tool) {
        Object affixData = ReflectionHelper.invokeStatic(RADIAL_AFFIX_CLASS, "getRadialData", new Class<?>[] { tool.getClass() }, tool).orElse(null);
        if (affixData != null) return affixData;
        return ReflectionHelper.invokeStatic(RADIAL_BONUS_CLASS, "getRadialData", new Class<?>[] { tool.getClass() }, tool).orElse(null);
    }
}

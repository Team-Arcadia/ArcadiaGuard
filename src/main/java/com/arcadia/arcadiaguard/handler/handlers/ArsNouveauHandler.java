package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickBlockHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class ArsNouveauHandler extends AbstractSpellHandler
        implements RightClickItemHandler, RightClickBlockHandler {

    private static final String EVENT_CLASS = "com.hollingsworth.arsnouveau.api.event.SpellCastEvent";

    private static final Set<String> MOVEMENT_GLYPHS = Set.of(
        "ars_nouveau:glyph_blink", "ars_nouveau:glyph_leap", "ars_nouveau:glyph_acceleration"
    );

    public ArsNouveauHandler(GuardService guardService) {
        super(guardService,
            ArcadiaGuardConfig.ENABLE_ARS_NOUVEAU::get,
            BuiltinFlags.ARS_SPELL_CAST,
            BuiltinFlags.SPELL_MOVEMENT,
            BuiltinFlags.ARS_SPELL_WHITELIST,
            BuiltinFlags.ARS_SPELL_BLACKLIST,
            MOVEMENT_GLYPHS,
            "arcadiaguard.message.ars_spell",
            ArcadiaGuardConfig.MESSAGE_ARS_NOUVEAU::get);
    }

    @Override public String eventClassName() { return EVENT_CLASS; }

    @Override
    protected ServerPlayer extractPlayer(Event event) {
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        return entity instanceof ServerPlayer p ? p : null;
    }

    @Override
    protected String extractSpellId(Event event, ServerPlayer player) {
        // H14: Objects.toString(s, "unknown") handles null spell.toString() gracefully
        return ReflectionHelper.field(event, "spell")
            .map(s -> java.util.Objects.toString(s, "unknown")).orElse("unknown").toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        if (!ArcadiaGuardConfig.ENABLE_ARS_NOUVEAU.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemStack();
        ResourceLocation key = itemKey(stack);
        if (key == null || !"ars_nouveau".equals(key.getNamespace())) return;

        String path = key.getPath();
        if (!"warp_scroll".equals(path) && !"stable_warp_scroll".equals(path)) return;

        BlockPos destination = warpScrollDestination(stack);
        if (destination != null) {
            if (guardService.blockIfFlagDenied(player, destination, BuiltinFlags.ARS_SPELL_CAST,
                    key.toString(), ArcadiaGuardConfig.MESSAGE_ARS_NOUVEAU.get()).blocked()) {
                event.setCanceled(true);
                return;
            }
        }

        if (player.isShiftKeyDown()) {
            BlockPos pos = player.blockPosition();
            if (guardService.blockIfFlagDenied(player, pos, BuiltinFlags.ARS_SPELL_CAST,
                    key.toString(), ArcadiaGuardConfig.MESSAGE_ARS_NOUVEAU.get()).blocked()) {
                event.setCanceled(true);
            }
        }
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        if (!ArcadiaGuardConfig.ENABLE_ARS_NOUVEAU.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemStack();
        ResourceLocation key = itemKey(stack);
        if (key == null || !"ars_nouveau".equals(key.getNamespace())) return;
        if (!"stable_warp_scroll".equals(key.getPath())) return;

        Object hitResult = ReflectionHelper.invoke(event, "getHitVec", new Class<?>[0]).orElse(null);
        Object blockPos = hitResult == null ? null : ReflectionHelper.invoke(hitResult, "getBlockPos", new Class<?>[0]).orElse(null);
        if (!(blockPos instanceof BlockPos pos)) return;

        if (guardService.blockIfFlagDenied(player, pos, BuiltinFlags.ARS_SPELL_CAST,
                key.toString(), ArcadiaGuardConfig.MESSAGE_ARS_NOUVEAU.get()).blocked()) {
            event.setCanceled(true);
        }
    }

    private BlockPos warpScrollDestination(ItemStack stack) {
        try {
            Object componentType = ReflectionHelper.field(
                "com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry", "WARP_SCROLL").orElse(null);
            if (!(componentType instanceof DataComponentType<?> type)) return null;
            Object warpScrollData = stack.get(type);
            if (warpScrollData == null) return null;
            if (!Boolean.TRUE.equals(ReflectionHelper.invoke(warpScrollData, "isValid", new Class<?>[0]).orElse(false))) return null;
            Object optionalPos = ReflectionHelper.invoke(warpScrollData, "pos", new Class<?>[0]).orElse(null);
            if (optionalPos == null) return null;
            Object posValue = ReflectionHelper.invoke(optionalPos, "get", new Class<?>[0]).orElse(null);
            return posValue instanceof BlockPos bp ? bp : null;
        } catch (Throwable t) {
            if (!WARN_WARP_LOGGED) {
                WARN_WARP_LOGGED = true;
                com.arcadia.arcadiaguard.ArcadiaGuard.LOGGER.warn(
                    "[ArcadiaGuard] Ars Nouveau warp scroll reflection failed (logged once): {}", t.toString());
            }
            return null;
        }
    }

    private static volatile boolean WARN_WARP_LOGGED = false;

    private static ResourceLocation itemKey(ItemStack stack) {
        ResourceKey<Item> k = stack.getItemHolder().getKey();
        return k != null ? k.location() : null;
    }
}

package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Blocks Apotheosis charm activation inside protected zones when CHARM_USE=deny.
 *
 * <p>Strategy (identical to ArcadiaTool's ApotheosisCompat):
 * <ol>
 *   <li>On RightClickItem: block the toggle interaction (charm uses Item#use to toggle
 *       {@code apotheosis:charm_enabled}).</li>
 *   <li>On zone entry (called from PlayerEventHandler tick): suppress active charms by
 *       setting DataComponent to {@code false} and tracking which slots were changed.</li>
 *   <li>On zone exit: restore suppressed charm slots.</li>
 * </ol>
 *
 * <p>The DataComponent is accessed via the registry to avoid compile-time dependency on Apotheosis.
 */
public final class ApotheosisCharmHandler implements RightClickItemHandler {

    private static final ResourceLocation CHARM_ENABLED_KEY =
        ResourceLocation.fromNamespaceAndPath("apotheosis", "charm_enabled");

    /** Tracks which inventory slots had their charm disabled per player UUID. */
    private static final Map<UUID, java.util.List<Integer>> SUPPRESSED = new ConcurrentHashMap<>();

    private final GuardService guardService;
    private volatile DataComponentType<Boolean> charmComponentType; // resolved lazily

    public ApotheosisCharmHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        if (!ModList.get().isLoaded("apotheosis")) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        if (guardService.shouldBypass(sp)) return;
        ItemStack stack = event.getItemStack();
        if (!isCharmItem(stack)) return;

        var zoneOpt = guardService.zoneManager().checkZone(sp, sp.blockPosition());
        if (zoneOpt.isEmpty()) return;
        if (!guardService.isZoneDenying(zoneOpt.get(), BuiltinFlags.CHARM_USE, sp.serverLevel())) return;

        event.setCanceled(true);
        sp.displayClientMessage(
            Component.translatable("arcadiaguard.message.charm_use").withStyle(ChatFormatting.RED), true);
        guardService.auditDenied(sp, zoneOpt.get().name(), sp.blockPosition(), BuiltinFlags.CHARM_USE, "charm_use");
    }

    /** Call when a player enters a zone with CHARM_USE=deny. Deactivates all active charms. */
    public void suppressCharms(ServerPlayer player) {
        if (!ModList.get().isLoaded("apotheosis")) return;
        DataComponentType<Boolean> type = resolveComponentType();
        if (type == null) return;

        var items = player.getInventory().items;
        var suppressed = new java.util.ArrayList<Integer>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            Boolean enabled = stack.get(type);
            if (Boolean.TRUE.equals(enabled)) {
                stack.set(type, false);
                suppressed.add(i);
            }
        }
        if (!suppressed.isEmpty()) {
            SUPPRESSED.put(player.getUUID(), suppressed);
        }
    }

    /** Call when a player leaves a zone with CHARM_USE=deny. Restores deactivated charms. */
    public void restoreCharms(ServerPlayer player) {
        if (!ModList.get().isLoaded("apotheosis")) return;
        DataComponentType<Boolean> type = resolveComponentType();
        if (type == null) return;

        var suppressed = SUPPRESSED.remove(player.getUUID());
        if (suppressed == null) return;
        var items = player.getInventory().items;
        for (int slot : suppressed) {
            if (slot < items.size()) {
                ItemStack stack = items.get(slot);
                if (isCharmItem(stack)) {
                    stack.set(type, true);
                }
            }
        }
    }

    /** Also clear on logout so state doesn't leak. */
    public void onPlayerLogout(ServerPlayer player) {
        restoreCharms(player);
        SUPPRESSED.remove(player.getUUID());
    }

    public boolean isCharmItem(ItemStack stack) {
        DataComponentType<Boolean> type = resolveComponentType();
        return type != null && stack.has(type);
    }

    @SuppressWarnings("unchecked")
    private DataComponentType<Boolean> resolveComponentType() {
        if (charmComponentType != null) return charmComponentType;
        try {
            Object type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(CHARM_ENABLED_KEY);
            if (type instanceof DataComponentType<?> dct) {
                charmComponentType = (DataComponentType<Boolean>) dct;
            }
        } catch (Exception e) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Could not resolve apotheosis:charm_enabled DataComponent: {}", e.getMessage());
        }
        return charmComponentType;
    }
}

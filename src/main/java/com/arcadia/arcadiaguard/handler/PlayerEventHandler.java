package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.flag.FlagResolver;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.item.ModItems;
import com.arcadia.arcadiaguard.item.WandItem;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class PlayerEventHandler
        implements HandlerRegistry.RightClickBlockHandler, HandlerRegistry.RightClickItemHandler {

    private static final int ZONE_CHECK_INTERVAL = 10; // ticks between zone boundary checks

    private final GuardService guard;
    /**
     * H-P4: Object2IntOpenHashMap avoids Integer boxing on every tick merge.
     * Only accessed from the server main thread (onPlayerTick, onPlayerLogout) so
     * no synchronisation is needed.
     */
    private final Object2IntOpenHashMap<UUID> tickCounter = new Object2IntOpenHashMap<>();
    private final Map<UUID, String> playerCurrentZone = new ConcurrentHashMap<>();
    private final Map<UUID, BlockPos> lastSafePos = new ConcurrentHashMap<>();

    public PlayerEventHandler(GuardService guard) {
        this.guard = guard;
    }

    /** Left-click block with ZONE_EDITOR → set pos1. */
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getMainHandItem().is(ModItems.ZONE_EDITOR.get())) return;
        BlockPos pos = event.getPos();
        WandItem.setPos1(player.getUUID(), pos);
        sendPosMessage(player, 1, pos);
        event.setCanceled(true);
    }

    /** Right-click block with ZONE_EDITOR → set pos2. */
    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getMainHandItem().is(ModItems.ZONE_EDITOR.get())) return;
        BlockPos pos = event.getPos();
        WandItem.setPos2(player.getUUID(), pos);
        sendPosMessage(player, 2, pos);
        event.setCanceled(true);
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {}

    /** Dimension change → clear selection and zone tracking. */
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID id = player.getUUID();
            WandItem.clearSelection(id);
            playerCurrentZone.remove(id);
            lastSafePos.remove(id);
        }
    }

    /** Player disconnects → clean up tracking data and debug mode. */
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        tickCounter.removeInt(id);
        playerCurrentZone.remove(id);
        lastSafePos.remove(id);
        WandItem.clearSelection(id);
        com.arcadia.arcadiaguard.ArcadiaGuard.guardService().clearDebug(id);
        // H7: invalidate bypass cache so stale verdict is not reused on next login
        guard.invalidateBypass(id);
        // H-P6: invalidate LuckPerms permData cache for this player
        com.arcadia.arcadiaguard.compat.luckperms.LuckPermsCompat.invalidatePlayer(id);
    }

    /**
     * Throttled tick check for zone entry/exit.
     * Runs every {@link #ZONE_CHECK_INTERVAL} ticks to detect boundary crossings
     * and enforce ENTRY/EXIT flags.
     */
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;

        UUID id = player.getUUID();
        int tick = tickCounter.addTo(id, 1) + 1;
        if (tick % ZONE_CHECK_INTERVAL != 0) return;

        BlockPos pos = player.blockPosition();
        @SuppressWarnings("unchecked")
        Optional<ProtectedZone> zoneOpt = (Optional<ProtectedZone>)(Optional<?>) guard.zoneManager().findZoneContaining(player.serverLevel(), pos);
        String newZoneName = zoneOpt.map(ProtectedZone::name).orElse(null);
        String oldZoneName = playerCurrentZone.get(id);

        boolean zoneChanged = !java.util.Objects.equals(newZoneName, oldZoneName);

        if (zoneChanged) {
            if (newZoneName != null && zoneOpt.isPresent()) {
                // Entering a new zone → check ENTRY flag
                ProtectedZone zone = zoneOpt.get();
                if (!guard.shouldBypass(player) && !guard.isZoneMember(player, zone)) {
                    boolean entryAllowed = guard.isFlagAllowedOrUnset(zone, BuiltinFlags.ENTRY, player.serverLevel());
                    if (!entryAllowed) {
                        // Push back to last known safe position
                        BlockPos safe = lastSafePos.getOrDefault(id, pos.offset(0, 0, 0));
                        player.teleportTo(
                            player.serverLevel(),
                            safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                            player.getYRot(), player.getXRot());
                        player.displayClientMessage(
                            Component.translatable("arcadiaguard.message.entry"), true);
                        return; // don't update zone tracking
                    }
                }
            } else if (newZoneName == null && oldZoneName != null) {
                // Exiting a zone → check EXIT flag (player already outside, just message)
                @SuppressWarnings("unchecked")
                Optional<ProtectedZone> prevZone = (Optional<ProtectedZone>)(Optional<?>) guard.zoneManager().get(player.serverLevel(), oldZoneName);
                if (prevZone.isPresent() && !guard.shouldBypass(player)
                        && !guard.isZoneMember(player, prevZone.get())) {
                    boolean exitAllowed = guard.isFlagAllowedOrUnset(prevZone.get(), BuiltinFlags.EXIT, player.serverLevel());
                    if (!exitAllowed) {
                        // Teleport back inside the zone (center)
                        ProtectedZone z = prevZone.get();
                        double cx = (z.minX() + z.maxX()) / 2.0 + 0.5;
                        double cy = z.minY();
                        double cz = (z.minZ() + z.maxZ()) / 2.0 + 0.5;
                        player.teleportTo(player.serverLevel(), cx, cy, cz,
                            player.getYRot(), player.getXRot());
                        player.displayClientMessage(
                            Component.translatable("arcadiaguard.message.exit"), true);
                        return;
                    }
                }
            }
            if (newZoneName != null) playerCurrentZone.put(id, newZoneName);
            else playerCurrentZone.remove(id);
        }

        // Track last safe position (outside any zone OR in a zone where ENTRY is allowed)
        if (newZoneName == null) {
            lastSafePos.put(id, pos);
        }

        // HEAL_AMOUNT / FEED_AMOUNT (valeurs par seconde → on tick au pas ZONE_CHECK_INTERVAL=10)
        if (zoneOpt.isPresent() && tick % 20 == 0) {
            ProtectedZone zone = zoneOpt.get();
            @SuppressWarnings("unchecked")
            Function<String, Optional<ProtectedZone>> lookup =
                n -> (Optional<ProtectedZone>)(Optional<?>) guard.zoneManager().get(player.serverLevel(), n);
            Function<String, java.util.Map<String, Object>> dimLookup =
                dim -> com.arcadia.arcadiaguard.ArcadiaGuard.dimFlagStore().flags(dim);
            int heal = FlagResolver.resolveOptional(zone, BuiltinFlags.HEAL_AMOUNT, lookup, dimLookup).orElse(0);
            if (heal > 0 && player.getHealth() < player.getMaxHealth()) {
                player.heal(heal);
            }
            int feed = FlagResolver.resolveOptional(zone, BuiltinFlags.FEED_AMOUNT, lookup, dimLookup).orElse(0);
            if (feed > 0 && player.getFoodData().getFoodLevel() < 20) {
                player.getFoodData().eat(feed, 0.5f);
            }
        }
    }

    private static void sendPosMessage(ServerPlayer player, int num, BlockPos pos) {
        player.sendSystemMessage(Component.translatable(
            "arcadiaguard.wand.pos_set", num, pos.getX(), pos.getY(), pos.getZ()));
        BlockPos p1 = WandItem.getPos1(player.getUUID());
        BlockPos p2 = WandItem.getPos2(player.getUUID());
        if (p1 != null && p2 != null) {
            int dx = Math.abs(p2.getX() - p1.getX()) + 1;
            int dy = Math.abs(p2.getY() - p1.getY()) + 1;
            int dz = Math.abs(p2.getZ() - p1.getZ()) + 1;
            player.sendSystemMessage(Component.translatable(
                "arcadiaguard.wand.selection", dx, dy, dz, (long) dx * dy * dz));
        }
    }
}

package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.flag.FlagResolver;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.handlers.ApotheosisCharmHandler;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class PlayerEventHandler
        implements HandlerRegistry.RightClickBlockHandler, HandlerRegistry.RightClickItemHandler {

    private static final int ZONE_CHECK_INTERVAL = 10; // ticks between zone boundary checks

    private final GuardService guard;
    private final ApotheosisCharmHandler charmHandler;
    /**
     * H-P4: Object2IntOpenHashMap avoids Integer boxing on every tick merge.
     * Only accessed from the server main thread (onPlayerTick, onPlayerLogout) so
     * no synchronisation is needed.
     */
    private final Object2IntOpenHashMap<UUID> tickCounter = new Object2IntOpenHashMap<>();
    private final Map<UUID, String> playerCurrentZone = new ConcurrentHashMap<>();
    private final Map<UUID, BlockPos> lastSafePos = new ConcurrentHashMap<>();
    /** S-H21 : dernier etat envoye au client pour parcool_actions. */
    private final Map<UUID, Boolean> playerParcoolBlocked = new ConcurrentHashMap<>();

    public PlayerEventHandler(GuardService guard, ApotheosisCharmHandler charmHandler) {
        this.guard = guard;
        this.charmHandler = charmHandler;
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
        if (event.getEntity() instanceof ServerPlayer sp) charmHandler.onPlayerLogout(sp);
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
                // Suppress Apotheosis charms if CHARM_USE=deny (skip for bypass/members)
                if (!guard.shouldBypass(player) && !guard.isZoneMember(player, zone)) {
                    if (!guard.isZoneDenying(zone, BuiltinFlags.CHARM_USE, player.serverLevel())) {
                        charmHandler.restoreCharms(player);
                    } else {
                        charmHandler.suppressCharms(player);
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
                        // Teleport back inside the zone (center, safe Y)
                        ProtectedZone z = prevZone.get();
                        double cx = (z.minX() + z.maxX()) / 2.0 + 0.5;
                        double cz = (z.minZ() + z.maxZ()) / 2.0 + 0.5;
                        double cy = findSafeY(player.serverLevel(), (int) Math.floor(cx), z.minY(), z.maxY(), (int) Math.floor(cz));
                        player.teleportTo(player.serverLevel(), cx, cy, cz,
                            player.getYRot(), player.getXRot());
                        player.displayClientMessage(
                            Component.translatable("arcadiaguard.message.exit"), true);
                        return;
                    }
                }
                // Leaving a zone → restore charms if they were suppressed
                charmHandler.restoreCharms(player);
            }
            if (newZoneName != null) playerCurrentZone.put(id, newZoneName);
            else playerCurrentZone.remove(id);
        }

        // Track last safe position (outside any zone OR in a zone where ENTRY is allowed)
        if (newZoneName == null) {
            lastSafePos.put(id, pos);
        }

        // S-H21 : sync du flag PARCOOL_ACTIONS au client (necessaire car l'action
        // parcool s'execute cote client local, pas sur le serveur).
        boolean parcoolBlocked = false;
        if (zoneOpt.isPresent() && !guard.shouldBypass(player) && !guard.isZoneMember(player, zoneOpt.get())) {
            parcoolBlocked = guard.isZoneDenying(zoneOpt.get(), BuiltinFlags.PARCOOL_ACTIONS, player.serverLevel());
        }
        Boolean wasBlocked = playerParcoolBlocked.get(id);
        if (wasBlocked == null || wasBlocked != parcoolBlocked) {
            playerParcoolBlocked.put(id, parcoolBlocked);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player,
                new com.arcadia.arcadiaguard.network.gui.ParcoolBlockedPayload(parcoolBlocked));
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

    /** Finds the lowest Y inside [minY, maxY] where two consecutive non-solid blocks sit above a solid one. */
    private static double findSafeY(ServerLevel level, int x, int minY, int maxY, int z) {
        int start = Math.max(minY, level.getMinBuildHeight());
        int end = Math.min(maxY - 1, level.getMaxBuildHeight() - 2);
        for (int y = start; y < end; y++) {
            BlockState floor = level.getBlockState(new BlockPos(x, y, z));
            BlockState feet  = level.getBlockState(new BlockPos(x, y + 1, z));
            BlockState head  = level.getBlockState(new BlockPos(x, y + 2, z));
            if (floor.isSolid() && !feet.isSolid() && !head.isSolid()) {
                return y + 1;
            }
        }
        return start + 1;
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

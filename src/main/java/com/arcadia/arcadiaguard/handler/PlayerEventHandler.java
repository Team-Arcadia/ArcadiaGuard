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
    /** Dernier etat envoye au client pour emote_use (verifier client-side Emotecraft). */
    private final Map<UUID, Boolean> playerEmoteBlocked = new ConcurrentHashMap<>();
    /** Derniere valeur APOTHEOSIS_FLY pour message one-shot a la transition. */
    private final Map<UUID, Boolean> playerApothFlyBlocked = new ConcurrentHashMap<>();
    /** Derniere fois qu'on a affiche un message parcool/emote a ce joueur (throttle cooldown). */
    private final Map<UUID, Long> lastParcoolMsgAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastEmoteMsgAt = new ConcurrentHashMap<>();
    private static final long MSG_THROTTLE_MS = 10_000L;

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
        playerParcoolBlocked.remove(id);
        playerEmoteBlocked.remove(id);
        playerApothFlyBlocked.remove(id);
        lastParcoolMsgAt.remove(id);
        lastEmoteMsgAt.remove(id);
        // Cleanup: retirer le modifier creative_flight si joueur se deco en zone bloquante.
        if (event.getEntity() instanceof ServerPlayer spLogout) applyCreativeFlightModifier(spLogout, false);
        GuiActionHandler.onPlayerLogout(id);
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
                        guard.auditDenied(player, zone.name(), pos, BuiltinFlags.ENTRY, "entry");
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
                        guard.auditDenied(player, z.name(), pos, BuiltinFlags.EXIT, "exit");
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
            // Message one-shot a la transition + throttle 10s pour securite (edge cases frontiere zone).
            if (parcoolBlocked) {
                long now = System.currentTimeMillis();
                Long last = lastParcoolMsgAt.get(id);
                if (last == null || now - last >= MSG_THROTTLE_MS) {
                    lastParcoolMsgAt.put(id, now);
                    player.displayClientMessage(
                        Component.translatable("arcadiaguard.message.parcool_actions")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                    guard.auditDenied(player, zoneOpt.get().name(), pos, BuiltinFlags.PARCOOL_ACTIONS, "parcool_actions");
                }
            }
        }

        // Meme pattern pour Emotecraft (verifier client-side).
        boolean emoteBlocked = false;
        if (zoneOpt.isPresent() && !guard.shouldBypass(player) && !guard.isZoneMember(player, zoneOpt.get())) {
            emoteBlocked = guard.isZoneDenying(zoneOpt.get(), BuiltinFlags.EMOTE_USE, player.serverLevel());
        }
        Boolean wasEmoteBlocked = playerEmoteBlocked.get(id);
        if (wasEmoteBlocked == null || wasEmoteBlocked != emoteBlocked) {
            playerEmoteBlocked.put(id, emoteBlocked);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player,
                new com.arcadia.arcadiaguard.network.gui.EmoteBlockedPayload(emoteBlocked));
            if (emoteBlocked) {
                long now = System.currentTimeMillis();
                Long last = lastEmoteMsgAt.get(id);
                if (last == null || now - last >= MSG_THROTTLE_MS) {
                    lastEmoteMsgAt.put(id, now);
                    player.displayClientMessage(
                        Component.translatable("arcadiaguard.message.emote_use")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                    guard.auditDenied(player, zoneOpt.get().name(), pos, BuiltinFlags.EMOTE_USE, "emote_use");
                }
            }
        }

        // FLY / APOTHEOSIS_FLY : bloque mayfly dans la zone.
        // Les DEUX flags appliquent le AttributeModifier negatif sur 'neoforge:creative_flight'
        // ET coupent mayfly directement. C'est necessaire car NeoForge re-set mayfly=true a chaque
        // tick depuis l'attribut (Apotheosis, Curios Flight Trinket, Mahou Tsukai, etc. utilisent
        // TOUS l'attribut standard neoforge:creative_flight). Sans modifier, notre cut clignote.
        //
        // FLY = superset (tout vol coupe). APOTHEOSIS_FLY = subset utile pour autoriser mayfly
        // creative mais bloquer les mods-attributs (cas d'usage: admin en creative flight dans
        // une zone ou les affixes mythic sont bannis).
        boolean inZone = zoneOpt.isPresent()
            && !guard.shouldBypass(player)
            && !guard.isZoneMember(player, zoneOpt.get())
            && !player.isCreative()
            && !player.isSpectator();

        boolean denyFly = inZone && guard.isZoneDenying(zoneOpt.get(), BuiltinFlags.FLY, player.serverLevel());
        boolean denyApothFly = inZone && !denyFly
            && guard.isZoneDenying(zoneOpt.get(), BuiltinFlags.APOTHEOSIS_FLY, player.serverLevel());

        // Modifier negatif applique dans LES DEUX cas (FLY ou APOTHEOSIS_FLY).
        applyCreativeFlightModifier(player, denyFly || denyApothFly);

        if (denyFly) {
            // Cut immediat en plus du modifier, + message one-shot a la transition.
            if (player.getAbilities().mayfly || player.getAbilities().flying) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            Boolean prev = playerApothFlyBlocked.put(id, Boolean.TRUE);
            if (prev == null || !prev) {
                player.displayClientMessage(
                    Component.translatable("arcadiaguard.message.fly").withStyle(net.minecraft.ChatFormatting.RED), true);
                guard.auditDenied(player, zoneOpt.get().name(), pos, BuiltinFlags.FLY, "fly");
            }
        } else if (denyApothFly) {
            Boolean prev = playerApothFlyBlocked.put(id, Boolean.TRUE);
            if (prev == null || !prev) {
                player.displayClientMessage(
                    Component.translatable("arcadiaguard.message.apotheosis_fly").withStyle(net.minecraft.ChatFormatting.RED), true);
                guard.auditDenied(player, zoneOpt.get().name(), pos, BuiltinFlags.APOTHEOSIS_FLY, "apotheosis_fly");
            }
        } else {
            playerApothFlyBlocked.remove(id);
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

    /**
     * Detecte si le joueur a l'attribut 'neoforge:creative_flight' > 0.
     * Cet attribut est utilise par l'affixe Apotheosis 'unbound' (chestplate mythic).
     * Cache le Holder via lookup statique pour eviter les allocations en hot path.
     */
    private static volatile net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> CREATIVE_FLIGHT_ATTR;
    private static volatile boolean CREATIVE_FLIGHT_MISSING = false;

    private static boolean hasCreativeFlightAttribute(ServerPlayer player) {
        if (CREATIVE_FLIGHT_MISSING) return false;
        var holder = CREATIVE_FLIGHT_ATTR;
        if (holder == null) {
            try {
                var rl = net.minecraft.resources.ResourceLocation.parse("neoforge:creative_flight");
                holder = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(rl).orElse(null);
                if (holder == null) { CREATIVE_FLIGHT_MISSING = true; return false; }
                CREATIVE_FLIGHT_ATTR = holder;
            } catch (Throwable t) { CREATIVE_FLIGHT_MISSING = true; return false; }
        }
        var inst = player.getAttributes().getInstance(holder);
        return inst != null && inst.getValue() > 0.0D;
    }

    /** ID du modifier negatif qu'on applique pour annuler neoforge:creative_flight en zone. */
    private static final net.minecraft.resources.ResourceLocation APOTH_FLY_MODIFIER_ID =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arcadiaguard", "apoth_fly_block");

    /**
     * Applique ou retire un AttributeModifier negatif sur neoforge:creative_flight pour
     * annuler tout bonus Apotheosis (affixe unbound) quand {@code active} est true.
     * Stateful et idempotent : add seulement si absent, remove seulement si present.
     */
    private static void applyCreativeFlightModifier(ServerPlayer player, boolean active) {
        if (CREATIVE_FLIGHT_MISSING) return;
        var holder = CREATIVE_FLIGHT_ATTR;
        if (holder == null) {
            // Lazy-init via le meme path que hasCreativeFlightAttribute.
            if (!hasCreativeFlightAttribute(player)) { /* force init */ }
            holder = CREATIVE_FLIGHT_ATTR;
            if (holder == null) return;
        }
        var inst = player.getAttributes().getInstance(holder);
        if (inst == null) return;
        boolean has = inst.getModifier(APOTH_FLY_MODIFIER_ID) != null;
        if (active && !has) {
            inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                APOTH_FLY_MODIFIER_ID, -1000.0D,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        } else if (!active && has) {
            inst.removeModifier(APOTH_FLY_MODIFIER_ID);
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

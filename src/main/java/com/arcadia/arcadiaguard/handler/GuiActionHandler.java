package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.command.ZonePermission;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.util.FlagUtils;
import com.arcadia.arcadiaguard.event.ZoneLifecycleEvent;
import com.arcadia.arcadiaguard.network.gui.DimFlagsPayload;
import com.arcadia.arcadiaguard.network.gui.DimFlagsPayload.FlagInfo;
import com.arcadia.arcadiaguard.network.gui.GuiActionPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneLogsPayload;
import com.arcadia.arcadiaguard.persist.DimFlagSerializer;
import com.arcadia.arcadiaguard.zone.ZoneRole;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Traite toutes les actions C→S émises depuis le GUI de gestion des zones. */
public final class GuiActionHandler {

    private GuiActionHandler() {}

    // C1: Token-bucket rate-limiter per player UUID (capacity=20, refill=20/s).
    // Cleanup au logout via onPlayerLogout (cap hard 500 en fallback si hook rate).
    private static final ConcurrentHashMap<UUID, RateLimiter> RATE_LIMITERS = new ConcurrentHashMap<>();

    /** Appele depuis PlayerEventHandler.onPlayerLogout pour eviter l'accumulation memoire. */
    public static void onPlayerLogout(UUID playerId) {
        RATE_LIMITERS.remove(playerId);
    }

    /** Simple token-bucket rate-limiter. Thread-safe. */
    private static final class RateLimiter {
        private final int capacity;
        private final int refillPerSecond;
        private long lastRefill;
        private int tokens;

        RateLimiter(int capacity, int refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.lastRefill = System.nanoTime();
            this.tokens = capacity;
        }

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            long elapsed = now - lastRefill;
            int refilled = (int) (elapsed / 1_000_000_000L * refillPerSecond);
            if (refilled > 0) {
                tokens = Math.min(capacity, tokens + refilled);
                lastRefill = now;
            }
            if (tokens <= 0) return false;
            tokens--;
            return true;
        }
    }

    public static void handle(GuiActionPayload payload, IPayloadContext context) {
        // C1: Rate-limit packets C→S before enqueuing work on main thread.
        if (context.player() instanceof ServerPlayer sp) {
            UUID uuid = sp.getUUID();
            if (RATE_LIMITERS.size() > 500) {
                RATE_LIMITERS.clear();
            }
            if (!RATE_LIMITERS.computeIfAbsent(uuid, k -> new RateLimiter(20, 20)).tryConsume()) {
                ArcadiaGuard.LOGGER.debug("[ArcadiaGuard] rate limited packet from {}", uuid);
                return;
            }
        }
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            switch (payload.action()) {
                case REQUEST_DETAIL -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.MEMBER)) return;
                    ArcadiaGuard.zoneManager().sendDetailToPlayer(player, payload.zoneName());
                }
                case CREATE_ZONE -> {
                    if (!isOp(player)) return;
                    createZone(player, payload);
                }
                case DELETE_ZONE -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.OWNER)) return;
                    deleteZone(player, payload.zoneName());
                }
                case SET_FLAG -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.OWNER)) return;
                    postSetFlag(player, payload.zoneName(), payload.arg1(), payload.boolVal(), false);
                }
                case WHITELIST_ADD -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.MODERATOR)) return;
                    whitelistAction(player, payload, true);
                }
                case WHITELIST_REMOVE -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.MODERATOR)) return;
                    whitelistAction(player, payload, false);
                }
                case TELEPORT -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.MEMBER)) return;
                    teleport(player, payload.zoneName());
                }
                case TOGGLE_DEBUG -> {
                    if (!isOp(player)) return;
                    toggleDebug(player);
                }
                case SET_PARENT -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.OWNER)) return;
                    setParent(player, payload);
                }
                case TOGGLE_ZONE_ENABLED -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.OWNER)) return;
                    postModify(player, payload.zoneName(),
                        ZoneLifecycleEvent.ModifyZone.Kind.TOGGLE_ENABLED, payload.boolVal(), null);
                }
                case TOGGLE_INHERIT_DIM_FLAGS -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.OWNER)) return;
                    postModify(player, payload.zoneName(),
                        ZoneLifecycleEvent.ModifyZone.Kind.TOGGLE_INHERIT, payload.boolVal(), null);
                }
                case SET_DIM_FLAG -> {
                    if (!isOp(player)) return;
                    setDimFlag(player, payload);
                }
                case RESET_DIM_FLAG -> {
                    if (!isOp(player)) return;
                    resetDimFlag(player, payload);
                }
                case REQUEST_DIM_DETAIL -> {
                    if (!isOp(player)) return;
                    sendDimDetail(player, payload.zoneName());
                }
                case RESET_FLAG -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.OWNER)) return;
                    postSetFlag(player, payload.zoneName(), payload.arg1(), false, true);
                }
                case SET_FLAG_STR -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.OWNER)) return;
                    setFlagStr(player, payload);
                }
                case SET_DIM_FLAG_STR -> {
                    if (!isOp(player)) return;
                    setDimFlagStr(player, payload);
                }
                case SET_ZONE_BOUNDS -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.OWNER)) return;
                    setZoneBounds(player, payload);
                }
                case REQUEST_ZONE_LOGS -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.MEMBER)) return;
                    sendZoneLogs(player, payload);
                }
                case REQUEST_PAGE -> {
                    if (!isOp(player)) return;
                    ArcadiaGuard.zoneManager().sendRefreshedList(player, payload.x1());
                }
                case ITEM_BLOCK_ADD -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.OWNER)) return;
                    itemBlockChange(player, payload, true);
                }
                case ITEM_BLOCK_REMOVE -> {
                    if (!canAccessZone(player, payload.zoneName(), ZoneRole.OWNER)) return;
                    itemBlockChange(player, payload, false);
                }
            }
        });
    }

    /** S-H20 : ajoute/retire un item de la liste des items bloqu\u00e9s d'une zone. */
    private static void itemBlockChange(ServerPlayer player, GuiActionPayload p, boolean add) {
        net.minecraft.resources.ResourceLocation itemId =
            net.minecraft.resources.ResourceLocation.tryParse(p.arg1());
        if (itemId == null) {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.invalid_value", p.arg1())
                .withStyle(ChatFormatting.RED));
            return;
        }
        postModify(player, p.zoneName(),
            ZoneLifecycleEvent.ModifyZone.Kind.ITEM_BLOCK_CHANGED,
            add ? "add" : "remove", itemId.toString());
    }

    private static void sendZoneLogs(ServerPlayer player, GuiActionPayload p) {
        var entries = ArcadiaGuard.auditLogger().tail(p.zoneName(), p.arg1(), p.arg2(), 200);
        List<ZoneLogsPayload.LogLine> lines = new ArrayList<>(entries.size());
        for (var e : entries) {
            lines.add(new ZoneLogsPayload.LogLine(e.timestamp(), e.player(), e.action(), e.pos()));
        }
        PacketDistributor.sendToPlayer(player, new ZoneLogsPayload(p.zoneName(), lines));
    }

    private static boolean isOp(ServerPlayer player) {
        return player.hasPermissions(ArcadiaGuardConfig.BYPASS_OP_LEVEL.get());
    }

    private static boolean canAccessZone(ServerPlayer player, String zoneName, ZoneRole minRole) {
        if (zoneName == null || zoneName.isEmpty()) return false;
        return ZonePermission.hasAccess(player.createCommandSourceStack(), zoneName, minRole);
    }

    // ── Event-driven zone write operations ────────────────────────────────────────

    private static void createZone(ServerPlayer player, GuiActionPayload p) {
        String raw = p.zoneName();
        String name = ArcadiaGuardPaths.normalizeZoneName(raw);
        if (name.isEmpty() || !ArcadiaGuardPaths.isValidZoneName(name)) {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.zone_name_invalid").withStyle(ChatFormatting.RED));
            return;
        }
        // Informer le joueur si son nom a ete normalise (espaces/majuscules/accents).
        if (!name.equals(raw.trim())) {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.zone_name_normalized", raw.trim(), name)
                .withStyle(ChatFormatting.YELLOW));
        }
        Level level = player.serverLevel();
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight() - 1;
        if (!validCoords(p.x1(), p.y1(), p.z1(), minY, maxY)
                || !validCoords(p.x2(), p.y2(), p.z2(), minY, maxY)) {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.coords_out_of_bounds").withStyle(ChatFormatting.RED));
            return;
        }
        String dim = level.dimension().location().toString();
        var event = new ZoneLifecycleEvent.CreateZone(player, level, name, dim,
            new BlockPos(p.x1(), p.y1(), p.z1()), new BlockPos(p.x2(), p.y2(), p.z2()));
        NeoForge.EVENT_BUS.post(event);
        if (event.isSuccess()) {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.zone_created", name).withStyle(ChatFormatting.GREEN));
            ArcadiaGuard.zoneManager().sendRefreshedList(player);
        } else {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.zone_exists").withStyle(ChatFormatting.RED));
        }
    }

    private static void deleteZone(ServerPlayer player, String zoneName) {
        // S-H18 : resoudre le level reel de la zone (cross-dim), pas celui du joueur.
        var event = new ZoneLifecycleEvent.DeleteZone(player, resolveZoneLevel(player, zoneName), zoneName);
        NeoForge.EVENT_BUS.post(event);
        if (event.isSuccess()) {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.zone_deleted", zoneName).withStyle(ChatFormatting.GREEN));
            // Purge le cache de rendu client (ZoneRenderer) chez TOUS les joueurs connectes,
            // sinon une zone "Voir : ON" reste dessinee apres suppression.
            var payload = new com.arcadia.arcadiaguard.network.gui.ZoneRemovedPayload(zoneName);
            for (ServerPlayer sp : player.getServer().getPlayerList().getPlayers()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, payload);
            }
            ArcadiaGuard.zoneManager().sendRefreshedList(player);
        } else {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.zone_not_found", zoneName).withStyle(ChatFormatting.RED));
        }
    }

    private static void postSetFlag(ServerPlayer player, String zoneName,
            String flagId, Object value, boolean reset) {
        var event = new ZoneLifecycleEvent.SetFlag(player, resolveZoneLevel(player, zoneName), zoneName, flagId, value, reset);
        NeoForge.EVENT_BUS.post(event);
        if (event.isSuccess()) ArcadiaGuard.zoneManager().sendDetailToPlayer(player, zoneName);
        else player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.zone_not_found", zoneName).withStyle(ChatFormatting.RED));
    }

    private static void setFlagStr(ServerPlayer player, GuiActionPayload p) {
        Object value = parseFlagValue(p.arg1(), p.arg2());
        if (value == null) {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.invalid_value", p.arg1()).withStyle(ChatFormatting.RED));
            return;
        }
        postSetFlag(player, p.zoneName(), p.arg1(), value, false);
    }

    private static void postModify(ServerPlayer player, String zoneName,
            ZoneLifecycleEvent.ModifyZone.Kind kind, Object arg1, Object arg2) {
        // S-H18/S-H20 : resoudre le level reel de la zone cross-dim, sinon
        // internal.setXxx(level, zoneName) ne trouve pas la zone et echoue
        // silencieusement (bug: cliquer +/x sur items bloques depuis l'overworld
        // pour une zone du nether ne faisait rien).
        Level targetLevel = resolveZoneLevel(player, zoneName);
        var event = new ZoneLifecycleEvent.ModifyZone(player, targetLevel, zoneName, kind, arg1, arg2);
        NeoForge.EVENT_BUS.post(event);
        if (event.isSuccess()) {
            ArcadiaGuard.zoneManager().sendDetailToPlayer(player, zoneName);
        } else {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.zone_not_found", zoneName).withStyle(ChatFormatting.RED));
            // Re-push le detail pour que le client resynchronise son UI à l'état serveur réel
            // (sinon un échec de validation laisse l'UI cliente avec une valeur fantôme).
            if (ArcadiaGuard.zoneManager().get(targetLevel, zoneName).isPresent()) {
                ArcadiaGuard.zoneManager().sendDetailToPlayer(player, zoneName);
            }
        }
    }

    /** Retourne le {@link Level} de la zone (cross-dim), ou celui du joueur en fallback. */
    private static Level resolveZoneLevel(ServerPlayer player, String zoneName) {
        var server = player.getServer();
        if (server != null) {
            for (var lvl : server.getAllLevels()) {
                if (ArcadiaGuard.zoneManager().get(lvl, zoneName).isPresent()) return lvl;
            }
        }
        return player.serverLevel();
    }

    private static void whitelistAction(ServerPlayer player, GuiActionPayload p, boolean add) {
        String targetName = p.arg1();
        MinecraftServer server = player.getServer();

        // REMOVE : pas besoin de ProfileCache. Cherche directement dans les membres existants
        // de la zone par nom (case-insensitive) — permet de retirer un joueur inconnu du
        // serveur / fake name / banni sans etre bloque par un lookup Mojang qui echoue.
        if (!add) {
            Level zoneLevel = resolveZoneLevel(player, p.zoneName());
            @SuppressWarnings("unchecked")
            var zoneOpt = (Optional<com.arcadia.arcadiaguard.zone.ProtectedZone>)(Optional<?>)
                ArcadiaGuard.zoneManager().get(zoneLevel, p.zoneName());
            if (zoneOpt.isEmpty()) {
                player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.zone_not_found", p.zoneName()).withStyle(ChatFormatting.RED));
                return;
            }
            // 1) Essayer UUID direct (cas le plus frequent : GUI envoie l'UUID du membre).
            //    On check contre whitelistedPlayers qui agrege memberRoles + whitelist explicite,
            //    pour ne pas rater un joueur whitelist-only (pas de role).
            UUID matchedUuid = null;
            String matchedName = targetName;
            try {
                UUID asUuid = UUID.fromString(targetName);
                if (zoneOpt.get().whitelistedPlayers().contains(asUuid)) {
                    matchedUuid = asUuid;
                    var cache0 = server.getProfileCache();
                    if (cache0 != null) {
                        matchedName = cache0.get(asUuid).map(GameProfile::getName).orElse(asUuid.toString());
                    }
                }
            } catch (IllegalArgumentException ignored) {}
            // 2) Fallback : recherche par nom via ProfileCache (admin tape un pseudo en CLI).
            if (matchedUuid == null) {
                var cache = server.getProfileCache();
                if (cache != null) {
                    for (UUID uuid : zoneOpt.get().whitelistedPlayers()) {
                        var name = cache.get(uuid).map(GameProfile::getName).orElse(null);
                        if (name != null && name.equalsIgnoreCase(targetName)) {
                            matchedUuid = uuid;
                            matchedName = name;
                            break;
                        }
                    }
                }
            }
            if (matchedUuid == null) {
                player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.player_not_found", targetName).withStyle(ChatFormatting.RED));
                return;
            }
            postModify(player, p.zoneName(), ZoneLifecycleEvent.ModifyZone.Kind.WHITELIST_REMOVE, matchedUuid, matchedName);
            return;
        }

        // ADD : on garde le ProfileCache lookup async (UUID authoritatif necessaire).
        // C2: ProfileCache.get(name) may block on a network lookup (HTTP miss).
        // Dispatch to background thread, then re-enter main thread for zone mutation.
        CompletableFuture.supplyAsync(() -> {
            try {
                return server.getProfileCache().get(targetName);
            } catch (Exception e) {
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] ProfileCache lookup failed for '{}': {}", targetName, e.toString());
                return Optional.<GameProfile>empty();
            }
        }, ForkJoinPool.commonPool()).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] ProfileCache lookup timed out for '{}'", targetName);
            return Optional.<GameProfile>empty();
        }).thenAcceptAsync(optProfile -> {
            if (player.hasDisconnected() || !player.isAlive()) return;
            if (optProfile == null || optProfile.isEmpty()) {
                player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.player_not_found", targetName).withStyle(ChatFormatting.RED));
                return;
            }
            GameProfile profile = optProfile.get();
            postModify(player, p.zoneName(), ZoneLifecycleEvent.ModifyZone.Kind.WHITELIST_ADD,
                profile.getId(), profile.getName());
        }, server);
    }

    private static void setParent(ServerPlayer player, GuiActionPayload p) {
        String parentName = p.arg1().trim();
        String zoneName = p.zoneName();
        if (!parentName.isEmpty()) {
            // Self-parent
            if (parentName.equalsIgnoreCase(zoneName)) {
                player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.parent_self").withStyle(ChatFormatting.RED));
                ArcadiaGuard.zoneManager().sendDetailToPlayer(player, zoneName);
                return;
            }
            // Zone parente inexistante
            if (ArcadiaGuard.zoneManager().get(player.serverLevel(), parentName).isEmpty()) {
                player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.zone_not_found", parentName).withStyle(ChatFormatting.RED));
                ArcadiaGuard.zoneManager().sendDetailToPlayer(player, zoneName);
                return;
            }
            // Détection de cycle : remonter la chaîne du parent proposé
            if (wouldCreateCycle(player.serverLevel(), parentName, zoneName)) {
                player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.parent_cycle", parentName).withStyle(ChatFormatting.RED));
                ArcadiaGuard.zoneManager().sendDetailToPlayer(player, zoneName);
                return;
            }
        }
        postModify(player, zoneName, ZoneLifecycleEvent.ModifyZone.Kind.SET_PARENT,
            parentName.isEmpty() ? null : parentName, null);
        // Renvoie la liste mise a jour pour que le badge "sous zones" s'actualise sur le
        // parent. Le client gere intelligemment : si la ZoneDetailScreen est ouverte, il
        // ne navigue pas (juste mise a jour du cache pour la prochaine ouverture de la liste).
        ArcadiaGuard.zoneManager().sendRefreshedList(player);
    }

    /**
     * Retourne true si définir {@code parentName} comme parent de {@code zoneName}
     * créerait un cycle (parent → grand-parent → … → zoneName).
     */
    private static boolean wouldCreateCycle(Level level, String parentName, String zoneName) {
        String current = parentName;
        int maxDepth = 32; // garde-fou contre une chaîne déjà cyclique
        while (current != null && maxDepth-- > 0) {
            if (current.equalsIgnoreCase(zoneName)) return true;
            var opt = ArcadiaGuard.zoneManager().get(level, current);
            if (opt.isEmpty()) return false;
            if (!(opt.get() instanceof com.arcadia.arcadiaguard.zone.ProtectedZone pz)) return false;
            current = pz.parent();
        }
        return false;
    }

    private static void setZoneBounds(ServerPlayer player, GuiActionPayload p) {
        Level level = player.serverLevel();
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight() - 1;
        if (!validCoords(p.x1(), p.y1(), p.z1(), minY, maxY)
                || !validCoords(p.x2(), p.y2(), p.z2(), minY, maxY)) {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.coords_out_of_bounds").withStyle(ChatFormatting.RED));
            return;
        }
        long dx = Math.abs((long) p.x2() - p.x1()) + 1;
        long dz = Math.abs((long) p.z2() - p.z1()) + 1;
        if (dx * dz > 10_000_000) {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.zone_too_large").withStyle(ChatFormatting.RED));
            return;
        }
        postModify(player, p.zoneName(), ZoneLifecycleEvent.ModifyZone.Kind.SET_BOUNDS,
            new BlockPos(p.x1(), p.y1(), p.z1()), new BlockPos(p.x2(), p.y2(), p.z2()));
        ArcadiaGuard.zoneManager().sendRefreshedList(player);
    }

    private static boolean validCoords(int x, int y, int z, int minY, int maxY) {
        if (y < minY || y > maxY) return false;
        return x > Integer.MIN_VALUE && z > Integer.MIN_VALUE
            && Math.abs((long) x) <= 30_000_000L && Math.abs((long) z) <= 30_000_000L;
    }

    // ── Teleport / Debug ─────────────────────────────────────────────────────────

    private static void teleport(ServerPlayer player, String zoneName) {
        // S-H18 : chercher la zone cross-dim (sinon impossible de TP vers une zone d'une autre dim).
        MinecraftServer server = player.getServer();
        var zoneOpt = server.getAllLevels() != null
            ? java.util.stream.StreamSupport.stream(server.getAllLevels().spliterator(), false)
                .flatMap(lvl -> ArcadiaGuard.zoneManager().get(lvl, zoneName).stream())
                .findFirst()
            : java.util.Optional.<com.arcadia.arcadiaguard.api.zone.IZone>empty();
        zoneOpt.ifPresent(zone -> {
            double cx = (zone.minX() + zone.maxX()) / 2.0 + 0.5;
            double cz = (zone.minZ() + zone.maxZ()) / 2.0 + 0.5;
            double cy = zone.maxY() + 1;
            // Résoudre le ServerLevel cible depuis le dimKey de la zone.
            net.minecraft.resources.ResourceLocation dimLoc =
                net.minecraft.resources.ResourceLocation.tryParse(zone.dimension());
            net.minecraft.server.level.ServerLevel targetLevel = dimLoc != null
                ? server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimLoc))
                : null;
            if (targetLevel == null) targetLevel = player.serverLevel();
            player.teleportTo(targetLevel, cx, cy, cz, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.teleported", zoneName).withStyle(ChatFormatting.GREEN));
        });
    }

    private static void toggleDebug(ServerPlayer player) {
        boolean enabled = ArcadiaGuard.guardService().toggleDebug(player.getUUID());
        String key = enabled
            ? "arcadiaguard.gui.debug_mode.enabled"
            : "arcadiaguard.gui.debug_mode.disabled";
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW));
        ArcadiaGuard.zoneManager().sendRefreshedList(player);
    }

    // ── Dimension flags ──────────────────────────────────────────────────────────

    private static void setDimFlag(ServerPlayer player, GuiActionPayload p) {
        ArcadiaGuard.dimFlagStore().setFlag(p.zoneName(), p.arg1(), p.boolVal());
        saveDimFlags();
        sendDimDetail(player, p.zoneName());
    }

    private static void resetDimFlag(ServerPlayer player, GuiActionPayload p) {
        ArcadiaGuard.dimFlagStore().resetFlag(p.zoneName(), p.arg1());
        saveDimFlags();
        sendDimDetail(player, p.zoneName());
    }

    private static void setDimFlagStr(ServerPlayer player, GuiActionPayload p) {
        Object value = parseFlagValue(p.arg1(), p.arg2());
        if (value == null) {
            player.sendSystemMessage(Component.translatable("arcadiaguard.gui.action.invalid_value", p.arg1()).withStyle(ChatFormatting.RED));
            return;
        }
        ArcadiaGuard.dimFlagStore().setFlag(p.zoneName(), p.arg1(), value);
        saveDimFlags();
        sendDimDetail(player, p.zoneName());
    }

    public static void sendDimDetail(ServerPlayer player, String dimKey) {
        java.util.Map<String, Object> dimFlagValues = ArcadiaGuard.dimFlagStore().flags(dimKey);
        List<FlagInfo> flags = new ArrayList<>();
        for (Flag<?> flag : ArcadiaGuard.flagRegistry().all()) {
            // chunkload n'a de sens qu'au niveau zone (tickets par zone), pas par dimension
            if ("chunkload".equals(flag.id())) continue;
            String mod = flag.requiredMod();
            if (!mod.isEmpty() && !ModList.get().isLoaded(mod)) continue;
            Object raw = dimFlagValues.get(flag.id());
            boolean configured = raw != null;
            byte type;
            boolean boolVal = false;
            String strVal;
            if (flag instanceof BooleanFlag bf) {
                type = FlagInfo.TYPE_BOOL;
                boolVal = raw instanceof Boolean b ? b : bf.defaultValue();
                strVal = Boolean.toString(boolVal);
            } else if (flag instanceof com.arcadia.arcadiaguard.api.flag.IntFlag intF) {
                type = FlagInfo.TYPE_INT;
                int v = raw instanceof Integer i ? i : intF.defaultValue();
                strVal = Integer.toString(v);
            } else if (flag instanceof com.arcadia.arcadiaguard.api.flag.ListFlag) {
                type = FlagInfo.TYPE_LIST;
                @SuppressWarnings("unchecked")
                List<String> v = raw instanceof List<?> l ? (List<String>) l : List.of();
                String joined = String.join(",", v);
                strVal = joined.length() > 30_000 ? joined.substring(0, 29_990) + ",…" : joined;
            } else { continue; }
            // i18n : envoie la cle de traduction, le client traduira.
            String desc = "arcadiaguard.flag." + flag.id() + ".description";
            flags.add(new FlagInfo(flag.id(), FlagUtils.formatFlagLabel(flag.id()),
                boolVal, configured, desc, type, strVal));
        }
        PacketDistributor.sendToPlayer(player, new DimFlagsPayload(dimKey, flags));
    }

    private static void saveDimFlags() {
        try { DimFlagSerializer.write(ArcadiaGuard.dimFlagStore(), ArcadiaGuardPaths.dimFlagsFile()); }
        catch (IOException e) { ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Failed to save dimension flags", e); }
    }

    private static Object parseFlagValue(String flagId, String raw) {
        Optional<Flag<?>> opt = ArcadiaGuard.flagRegistry().get(flagId);
        if (opt.isEmpty()) return null;
        Flag<?> flag = opt.get();
        try {
            if (flag instanceof com.arcadia.arcadiaguard.api.flag.IntFlag intFlag) {
                int parsed = Integer.parseInt(raw.trim());
                // Clamp serveur pour garantir que les bornes ne peuvent pas etre contournees
                // meme si un client malveillant envoie une valeur hors plage.
                return Math.max(intFlag.min(), Math.min(intFlag.max(), parsed));
            }
            if (flag instanceof com.arcadia.arcadiaguard.api.flag.ListFlag) {
                if (raw.isBlank()) return new ArrayList<String>();
                ArrayList<String> out = new ArrayList<>();
                for (String s : raw.split(",")) { String t = s.trim(); if (!t.isEmpty()) out.add(t); }
                return out;
            }
            if (flag instanceof BooleanFlag) return Boolean.parseBoolean(raw);
        } catch (NumberFormatException ignored) {}
        return null;
    }

    /** Backward-compat delegate for ArcadiaGuardCommands and other callers. */
    public static void sendRefreshedList(ServerPlayer player) {
        ArcadiaGuard.zoneManager().sendRefreshedList(player);
    }
}

package com.arcadia.arcadiaguard.guard;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.api.IGuardService;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.zone.IZone;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.FlagResolver;
import com.arcadia.arcadiaguard.logging.ArcadiaGuardAuditLogger;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.arcadia.arcadiaguard.api.zone.ZoneCheckResult;
import com.arcadia.arcadiaguard.zone.DimensionFlagStore;
import com.arcadia.arcadiaguard.zone.ZoneManager;
import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsCompat;
import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsPermissionChecker;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;

public final class GuardService implements IGuardService {

    public record GuardResult(boolean blocked, String zoneName) {}

    /**
     * C6: profiler push/pop gated behind a system property so they don't run on
     * every hot-path call in production. Enable with {@code -Darcadiaguard.profile=true}.
     */
    private static final boolean PROFILE = Boolean.getBoolean("arcadiaguard.profile");

    private final ZoneManager zoneManager;
    private final ArcadiaGuardAuditLogger auditLogger;
    private final DimensionFlagStore dimFlagStore;
    private final Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();

    /**
     * H7: Cache the LuckPerms checker once — avoids calling isAvailable() + checker()
     * on every shouldBypass/isZoneMember call. Initialized lazily on first use so boot
     * order is not a problem (LuckPerms is fully loaded by the time players connect).
     */
    private volatile LuckPermsPermissionChecker lpCheckerCache = null;
    private volatile boolean lpCheckerResolved = false;

    /**
     * H7: UUID → bypass verdict cache. Invalidated on player logout via {@link #invalidateBypass(UUID)}.
     * TRUE = has bypass, FALSE = no bypass.
     */
    private final ConcurrentHashMap<UUID, Boolean> bypassCache = new ConcurrentHashMap<>();

    public GuardService(ZoneManager zoneManager, ArcadiaGuardAuditLogger auditLogger, DimensionFlagStore dimFlagStore) {
        this.zoneManager = zoneManager;
        this.auditLogger = auditLogger;
        this.dimFlagStore = dimFlagStore;
    }

    /** Returns the cached LuckPerms checker, or null if LP is unavailable. Thread-safe lazy init. */
    private LuckPermsPermissionChecker lpChecker() {
        if (!lpCheckerResolved) {
            synchronized (this) {
                if (!lpCheckerResolved) {
                    lpCheckerCache = LuckPermsCompat.isAvailable() ? LuckPermsCompat.checker() : null;
                    lpCheckerResolved = true;
                }
            }
        }
        return lpCheckerCache;
    }

    @Override
    public boolean shouldBypass(ServerPlayer player) {
        UUID id = player.getUUID();
        if (debugPlayers.contains(id)) return false;
        // H7: use bypass cache
        Boolean cached = bypassCache.get(id);
        if (cached != null) return cached;
        try {
            boolean bypass = computeBypass(player);
            bypassCache.put(id, bypass);
            return bypass;
        } catch (Exception e) {
            ArcadiaGuard.LOGGER.warn("ArcadiaGuard: failed to check permissions for player {}, defaulting to no bypass", player.getGameProfile().getName(), e);
            return false;
        }
    }

    private boolean computeBypass(ServerPlayer player) {
        var lp = lpChecker();
        if (lp != null && lp.hasBypass(player)) return true;
        return player.hasPermissions(ArcadiaGuardConfig.BYPASS_OP_LEVEL.get());
    }

    /**
     * H7: Invalidates the bypass cache for a player UUID (call on logout / permission change).
     */
    public void invalidateBypass(UUID playerId) {
        bypassCache.remove(playerId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ZoneCheckResult checkFlag(ServerPlayer player, BlockPos pos, Flag<Boolean> flag) {
        if (shouldBypass(player)) return ZoneCheckResult.allowed();
        Optional<IZone> zoneOpt = this.zoneManager.findZoneContaining(player.serverLevel(), pos);
        if (zoneOpt.isEmpty()) return ZoneCheckResult.allowed();
        ProtectedZone zone = (ProtectedZone) zoneOpt.get();
        if (zone.whitelistedPlayers().contains(player.getUUID())) return ZoneCheckResult.allowed();
        Function<String, Optional<ProtectedZone>> lookup = name ->
            (Optional<ProtectedZone>)(Optional<?>) this.zoneManager.get(player.serverLevel(), name);
        boolean allowed = FlagResolver.resolve(zone, flag, lookup);
        return allowed ? ZoneCheckResult.allowed() : ZoneCheckResult.blocked(zone.name());
    }

    /** Bascule le mode debug pour ce joueur. Retourne true si le debug est maintenant actif. */
    public boolean toggleDebug(UUID playerId) {
        invalidateBypass(playerId); // debug toggle changes bypass behaviour
        if (!debugPlayers.remove(playerId)) {
            debugPlayers.add(playerId);
            return true;
        }
        return false;
    }

    /** Returns true if {@code player} is whitelisted or has a LuckPerms role in {@code zone}. */
    @Override
    public boolean isZoneMember(ServerPlayer player, IZone zone) {
        ProtectedZone pz = (ProtectedZone) zone;
        if (pz.whitelistedPlayers().contains(player.getUUID())) return true;
        try {
            var lp = lpChecker(); // H7: use cached checker
            return lp != null && lp.resolveRole(player, pz.name()) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isDebugMode(UUID playerId) {
        return debugPlayers.contains(playerId);
    }

    public void clearDebug(UUID playerId) {
        debugPlayers.remove(playerId);
    }

    public GuardResult blockIfProtected(ServerPlayer player, BlockPos pos, String actionName, String featureKey, String message) {
        // C6: only push/pop profiler when explicitly enabled via system property
        ProfilerFiller profiler = PROFILE ? player.getServer().getProfiler() : null;
        if (profiler != null) profiler.push("arcadiaguard");
        try {
            if (shouldBypass(player)) return new GuardResult(false, "");

            ZoneCheckResult result = this.zoneManager.check(player, pos);
            if (!result.blocked()) return new GuardResult(false, "");

            // Actionbar (true) au lieu du chat pour eviter le spam lors
            // d'actions repetitives (casse, pose, interactions, etc.).
            player.displayClientMessage(Component.translatable(message).withStyle(ChatFormatting.RED), true);
            this.auditLogger.logBlockedAction(player.getGameProfile().getName(), actionName, result.zoneName(), pos);
            return new GuardResult(true, result.zoneName());
        } finally {
            if (profiler != null) profiler.pop();
        }
    }

    /**
     * Flag-aware block: checks if the given {@code flag} is denied in the zone at {@code pos}.
     * Returns blocked if the player is in a protected zone and the flag value is false (deny).
     * Respects bypass permission, whitelist, and exception zones.
     */
    public GuardResult blockIfFlagDenied(ServerPlayer player, BlockPos pos, BooleanFlag flag,
                                         String actionName, String message) {
        // C6: only push/pop profiler when explicitly enabled via system property
        ProfilerFiller profiler = PROFILE ? player.getServer().getProfiler() : null;
        if (profiler != null) profiler.push("arcadiaguard");
        try {
            if (shouldBypass(player)) return new GuardResult(false, "");

            Optional<ProtectedZone> zoneOpt = this.zoneManager.checkZone(player, pos);
            Optional<Boolean> resolved;
            String zoneName;
            if (zoneOpt.isPresent()) {
                @SuppressWarnings("unchecked")
                Function<String, Optional<ProtectedZone>> lookup = name ->
                    (Optional<ProtectedZone>)(Optional<?>) this.zoneManager.get(player.serverLevel(), name);
                Function<String, java.util.Map<String, Object>> dimLookup = dim -> this.dimFlagStore.flags(dim);
                resolved = FlagResolver.resolveOptional(zoneOpt.get(), flag, lookup, dimLookup);
                zoneName = zoneOpt.get().name();
            } else {
                // Pas de zone → fallback direct sur les flags de dimension.
                resolved = resolveDimFlag(player.level(), flag);
                zoneName = "(dimension)";
            }

            // Flag non défini quelque part → le mod n'intervient pas.
            if (resolved.isEmpty() || resolved.get()) return new GuardResult(false, "");

            player.displayClientMessage(Component.translatable(message).withStyle(ChatFormatting.RED), true);
            this.auditLogger.logBlockedAction(player.getGameProfile().getName(), actionName, zoneName, pos);
            return new GuardResult(true, zoneName);
        } finally {
            if (profiler != null) profiler.pop();
        }
    }

    /**
     * H-P2: Returns true if the given {@code flag} is denied in an already-resolved
     * {@code zone}. Avoids a second zone lookup when the caller already has the zone.
     */
    @SuppressWarnings("unchecked")
    public boolean isZoneDenying(ProtectedZone zone, BooleanFlag flag, Level level) {
        if (!zone.enabled()) return false;
        Function<String, Optional<ProtectedZone>> lookup = name ->
            (Optional<ProtectedZone>)(Optional<?>) this.zoneManager.get(level, name);
        Function<String, java.util.Map<String, Object>> dimLookup = dim -> this.dimFlagStore.flags(dim);
        return FlagResolver.resolveOptional(zone, flag, lookup, dimLookup).map(v -> !v).orElse(false);
    }

    /**
     * Returns true if the given {@code flag} is denied at {@code pos} in {@code level}.
     * Used by entity event handlers where no player is directly involved.
     */
    public boolean isZoneDenying(Level level, BlockPos pos, BooleanFlag flag) {
        Optional<IZone> zoneIZoneOpt = this.zoneManager.findZoneContaining(level, pos);
        Optional<Boolean> resolved;
        if (zoneIZoneOpt.isPresent()) {
            ProtectedZone zone = (ProtectedZone) zoneIZoneOpt.get();
            if (!zone.enabled()) return false;
            @SuppressWarnings("unchecked")
            Function<String, Optional<ProtectedZone>> lookup = name ->
                (Optional<ProtectedZone>)(Optional<?>) this.zoneManager.get(level, name);
            Function<String, java.util.Map<String, Object>> dimLookup = dim -> this.dimFlagStore.flags(dim);
            resolved = FlagResolver.resolveOptional(zone, flag, lookup, dimLookup);
        } else {
            resolved = resolveDimFlag(level, flag);
        }
        return resolved.map(v -> !v).orElse(false);
    }

    /** Récupère la valeur d'un flag directement sur la dimension (sans zone). */
    @SuppressWarnings("unchecked")
    private <T> Optional<T> resolveDimFlag(Level level, com.arcadia.arcadiaguard.api.flag.Flag<T> flag) {
        String dimKey = com.arcadia.arcadiaguard.util.DimensionUtils.keyOf(level);
        java.util.Map<String, Object> dimFlags = this.dimFlagStore.flags(dimKey);
        Object raw = dimFlags.get(flag.id());
        if (raw == null) return Optional.empty();
        try { return Optional.of((T) raw); }
        catch (ClassCastException ignored) { return Optional.empty(); }
    }

    public ZoneManager zoneManager() {
        return this.zoneManager;
    }

    /**
     * Resolves a boolean flag on {@code zone} with full chain (zone → parent → dim).
     * Returns {@code true} (= allowed / mod stays out) when the flag is not set anywhere.
     * Use for opt-out restrictions (ENTRY, spell-cast, item-use…).
     */
    @SuppressWarnings("unchecked")
    public boolean isFlagAllowedOrUnset(ProtectedZone zone, BooleanFlag flag, Level level) {
        Function<String, Optional<ProtectedZone>> lookup = name ->
            (Optional<ProtectedZone>)(Optional<?>) this.zoneManager.get(level, name);
        Function<String, java.util.Map<String, Object>> dimLookup = dim -> this.dimFlagStore.flags(dim);
        return FlagResolver.resolveOptional(zone, flag, lookup, dimLookup).orElse(true);
    }
}

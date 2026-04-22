package com.arcadia.arcadiaguard.zone;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import com.arcadia.arcadiaguard.flag.FlagRegistryImpl;
import com.arcadia.arcadiaguard.persist.AsyncZoneWriter;
import com.arcadia.arcadiaguard.api.zone.ZoneCheckResult;
import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsCompat;
import com.arcadia.arcadiaguard.api.event.FlagChangedEvent;
import com.arcadia.arcadiaguard.api.event.ZoneCreatedEvent;
import com.arcadia.arcadiaguard.api.event.ZoneRemovedEvent;
import com.arcadia.arcadiaguard.persist.ZoneSerializer;
import com.arcadia.arcadiaguard.util.DimensionUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Provides zone protection backed by individual JSON files on disk.
 * One file per zone: {@code config/arcadiaguard/zones/<dim>/<name>.json}.
 *
 * <p>All disk writes are delegated to {@link com.arcadia.arcadiaguard.persist.AsyncZoneWriter}.
 * Corrupted files are logged and skipped — other zones continue loading normally.
 *
 * <p><b>DEV RULE — persistence contract:</b> every public/package method that mutates
 * zone state (add, remove, setFlag, whitelist, parent, enabled, priority, members, bounds…)
 * <b>MUST</b> end with a call to {@link #scheduleWrite(ProtectedZone)} or
 * {@link #scheduleDeletion(String, String)} before returning. Forgetting this leaves
 * the state in memory only — it will be lost on restart / reload. The async writer
 * coalesces bursts on a shared key per zone, so calling scheduleWrite in a tight loop
 * is cheap.
 */
public final class InternalZoneProvider implements ZoneProvider {

    private final FlagRegistryImpl flagRegistry;
    private final AsyncZoneWriter asyncZoneWriter;

    // dimKey -> (nameLower -> zone). ConcurrentHashMap so that AsyncZoneWriter
    // snapshot iteration never collides with main-thread mutations.
    // volatile so reload() can swap the whole reference atomically (B3).
    private volatile Map<String, Map<String, ProtectedZone>> zonesByDimension = new ConcurrentHashMap<>();

    // Spatial index: dimKey -> (chunkKey -> zones covering that chunk).
    // Zones with Integer.MIN_VALUE bounds (dimensional/global) live in globalZones instead.
    // volatile so reload() can swap both references in one assignment each (B3).
    private volatile Map<String, Map<Long, List<ProtectedZone>>> chunkIndex = new ConcurrentHashMap<>();
    private volatile Map<String, List<ProtectedZone>> globalZones = new ConcurrentHashMap<>();

    public InternalZoneProvider(FlagRegistryImpl flagRegistry, AsyncZoneWriter asyncZoneWriter) {
        this.flagRegistry = flagRegistry;
        this.asyncZoneWriter = asyncZoneWriter;
    }

    @Override
    public String name() {
        return "internal";
    }

    @Override
    public void reload(MinecraftServer server) {
        // Build new structures locally, then swap atomically (B3): readers see either the
        // old complete state or the new complete state, never a partially-populated map.
        Map<String, Map<String, ProtectedZone>> newZones = new ConcurrentHashMap<>();
        Path zonesRoot = ArcadiaGuardPaths.zonesRoot();
        if (Files.exists(zonesRoot)) {
            try (var dimDirs = Files.list(zonesRoot)) {
                dimDirs.filter(Files::isDirectory).forEach(dimDir -> {
                    try (var zoneFiles = Files.list(dimDir)) {
                        zoneFiles.filter(p -> p.toString().endsWith(".json"))
                                 .forEach(file -> loadZoneFile(file, newZones));
                    } catch (IOException e) {
                        ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Failed to list zone files in {}", dimDir, e);
                    }
                });
            } catch (IOException e) {
                ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Failed to scan zones directory", e);
            }
        }
        // Build spatial indexes from the new data before swapping references.
        Map<String, Map<Long, List<ProtectedZone>>> newChunkIndex = new ConcurrentHashMap<>();
        Map<String, List<ProtectedZone>> newGlobalZones = new ConcurrentHashMap<>();
        for (Map.Entry<String, Map<String, ProtectedZone>> entry : newZones.entrySet()) {
            for (ProtectedZone zone : entry.getValue().values()) {
                indexZoneInto(entry.getKey(), zone, newChunkIndex, newGlobalZones);
            }
        }
        // Atomic swap: each field write is individually volatile, which is sufficient since
        // readers use whichever consistent snapshot they happen to see.
        this.zonesByDimension = newZones;
        this.chunkIndex = newChunkIndex;
        this.globalZones = newGlobalZones;
        // Invalidate mixin cache for all dimensions (B2).
        FlagMixinHelper.invalidateAll();
        // Cleanup dossiers de dim orphelins (empty) + warn si dim avec zones absente du serveur.
        cleanupOrphanDimDirectories(server, zonesRoot, newZones);
    }

    /**
     * Post-reload cleanup des dossiers de dimensions qui n'existent plus dans le serveur
     * (ex. pocket dim Ars Nouveau supprimee, mod retire). Pour securite :
     *  - supprime SEULEMENT les dossiers vides (pas de .json)
     *  - log un WARN si un dossier non-vide correspond a une dim absente du serveur,
     *    pour que l'admin decide (pocket dim chargee plus tard ? mod desinstalle ?).
     * Ne touche jamais a un fichier zone — zero risque de perte de donnees.
     */
    private void cleanupOrphanDimDirectories(MinecraftServer server, Path zonesRoot,
                                             Map<String, Map<String, ProtectedZone>> loadedZones) {
        if (!Files.exists(zonesRoot)) return;
        // Liste des dim keys actives, sanitizees pour matcher les noms de dossiers.
        java.util.Set<String> liveDimDirs = new java.util.HashSet<>();
        for (var level : server.getAllLevels()) {
            liveDimDirs.add(ArcadiaGuardPaths.sanitizeDimKey(level.dimension().location().toString()));
        }
        try (var dimDirs = Files.list(zonesRoot)) {
            dimDirs.filter(Files::isDirectory).forEach(dimDir -> {
                String dirName = dimDir.getFileName().toString();
                if (liveDimDirs.contains(dirName)) return; // dim active, rien a faire
                // Dim absente du serveur : check si le dossier contient des zones.
                try (var files = Files.list(dimDir)) {
                    boolean hasJson = files.anyMatch(p -> p.toString().endsWith(".json"));
                    if (hasJson) {
                        ArcadiaGuard.LOGGER.warn(
                            "[ArcadiaGuard] Dossier de dimension orphelin '{}' contient des zones mais la dim n'est pas enregistree dans le serveur. "
                            + "La dim est peut-etre chargee plus tard (pocket dim) ou un mod a ete desinstalle. "
                            + "Supprimer manuellement si confirme inutile.", dirName);
                    } else {
                        try { Files.delete(dimDir); }
                        catch (IOException e) {
                            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Failed to delete empty orphan dim directory '{}': {}", dirName, e.toString());
                        }
                    }
                } catch (IOException e) {
                    ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Failed to inspect orphan dim directory '{}': {}", dirName, e.toString());
                }
            });
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Failed to scan zones root for orphan cleanup: {}", e.toString());
        }
    }

    private void loadZoneFile(Path file, Map<String, Map<String, ProtectedZone>> target) {
        try {
            ProtectedZone zone = ZoneSerializer.read(file);
            if (zone == null) return; // skipped (e.g. file too large)
            target.computeIfAbsent(zone.dimension(), ignored -> new ConcurrentHashMap<>())
                  .put(zone.name().toLowerCase(), zone);
        } catch (Exception e) {
            ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Skipping corrupted zone file {}: {}", file.getFileName(), e.getMessage());
        }
    }

    @Override
    public ZoneCheckResult check(ServerPlayer player, BlockPos pos) {
        String dim = DimensionUtils.keyOf(player);
        if (!this.zonesByDimension.containsKey(dim)) return ZoneCheckResult.allowed();
        try {
            List<ProtectedZone> cands = candidates(dim, pos);
            if (cands.isEmpty()) return ZoneCheckResult.allowed();
            UUID playerId = player.getUUID();
            List<ProtectedZone> matching = null;
            ProtectedZone first = null;
            for (ProtectedZone zone : cands) {
                if (zone.enabled() && zone.contains(dim, pos)
                        && !zone.whitelistedPlayers().contains(playerId)
                        && !isLpMember(player, zone)) {
                    if (first == null) { first = zone; }
                    else {
                        if (matching == null) { matching = new ArrayList<>(); matching.add(first); }
                        matching.add(zone);
                    }
                }
            }
            if (first == null) return ZoneCheckResult.allowed();
            ProtectedZone winner = matching != null ? ZoneConflictResolver.resolve(matching) : first;
            return new ZoneCheckResult(true, winner.name(), name());
        } catch (Exception e) {
            ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Zone check failed for {}, defaulting to BLOCKED", player.getGameProfile().getName(), e);
            return new ZoneCheckResult(true, "error-fallback", name());
        }
    }

    @Override
    public Collection<ProtectedZone> zones(Level level) {
        return this.zonesByDimension.getOrDefault(DimensionUtils.keyOf(level), Map.of()).values();
    }

    @Override
    public Collection<ProtectedZone> allZones(MinecraftServer server) {
        // Accès natif : on lit directement la map en mémoire, pas besoin d'itérer les levels
        // (couvre aussi les dimensions dont aucun level n'est chargé au moment du call).
        List<ProtectedZone> out = new ArrayList<>();
        for (Map<String, ProtectedZone> dimZones : this.zonesByDimension.values()) {
            out.addAll(dimZones.values());
        }
        return out;
    }

    @Override
    public Optional<ProtectedZone> get(Level level, String name) {
        return Optional.ofNullable(
            this.zonesByDimension.getOrDefault(DimensionUtils.keyOf(level), Map.of()).get(name.toLowerCase()));
    }

    @Override
    public boolean add(Level level, ProtectedZone zone) {
        String dimKey = DimensionUtils.keyOf(level);
        Map<String, ProtectedZone> zones = this.zonesByDimension.computeIfAbsent(dimKey, ignored -> new ConcurrentHashMap<>());
        if (zones.containsKey(zone.name().toLowerCase())) return false;
        zones.put(zone.name().toLowerCase(), zone);
        try {
            indexZone(dimKey, zone);
        } catch (Exception e) {
            zones.remove(zone.name().toLowerCase());
            ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Failed to index zone '{}', rolling back", zone.name(), e);
            return false;
        }
        scheduleWrite(zone);
        FlagMixinHelper.invalidateHasZoneCache(dimKey);
        NeoForge.EVENT_BUS.post(new ZoneCreatedEvent(zone));
        return true;
    }

    @Override
    public boolean remove(Level level, String name) {
        String dimKey = DimensionUtils.keyOf(level);
        Map<String, ProtectedZone> zones = this.zonesByDimension.get(dimKey);
        if (zones == null) return false;
        ProtectedZone removed = zones.remove(name.toLowerCase());
        if (removed == null) return false;
        rebuildDimIndex(dimKey);
        scheduleDeletion(dimKey, name);
        FlagMixinHelper.invalidateHasZoneCache(dimKey);
        NeoForge.EVENT_BUS.post(new ZoneRemovedEvent(removed));
        return true;
    }

    @Override
    public boolean whitelistAdd(Level level, String name, UUID playerId, @Nullable String playerName) {
        Optional<ProtectedZone> zone = get(level, name);
        if (zone.isEmpty()) return false;
        boolean changed = zone.get().whitelistAdd(playerId);
        if (changed) scheduleWrite(zone.get());
        return changed;
    }

    @Override
    public boolean whitelistRemove(Level level, String name, UUID playerId, @Nullable String playerName) {
        Optional<ProtectedZone> zone = get(level, name);
        if (zone.isEmpty()) return false;
        boolean changed = zone.get().whitelistRemove(playerId);
        if (changed) scheduleWrite(zone.get());
        return changed;
    }

    /** Assigns a role to a player in the named zone and schedules a disk write. */
    public boolean setMemberRole(Level level, String zoneName, UUID playerId, ZoneRole role) {
        Optional<ProtectedZone> zone = get(level, zoneName);
        if (zone.isEmpty()) return false;
        zone.get().setRole(playerId, role);
        scheduleWrite(zone.get());
        return true;
    }

    /**
     * Single-pass check: returns the winning zone if {@code player} is blocked at {@code pos},
     * already filtering whitelisted players and LuckPerms members. Replaces the pattern of
     * calling {@code check()} followed by {@code findContaining()} (two linear scans).
     */
    public Optional<ProtectedZone> checkZone(ServerPlayer player, BlockPos pos) {
        String dim = DimensionUtils.keyOf(player);
        if (!this.zonesByDimension.containsKey(dim)) return Optional.empty();
        try {
            List<ProtectedZone> cands = candidates(dim, pos);
            if (cands.isEmpty()) return Optional.empty();
            UUID playerId = player.getUUID();
            List<ProtectedZone> matching = null;
            ProtectedZone first = null;
            for (ProtectedZone zone : cands) {
                if (zone.enabled() && zone.contains(dim, pos)
                        && !zone.whitelistedPlayers().contains(playerId)
                        && !isLpMember(player, zone)) {
                    if (first == null) { first = zone; }
                    else {
                        if (matching == null) { matching = new ArrayList<>(); matching.add(first); }
                        matching.add(zone);
                    }
                }
            }
            if (first == null) return Optional.empty();
            return Optional.of(matching != null ? ZoneConflictResolver.resolve(matching) : first);
        } catch (Exception e) {
            ArcadiaGuard.LOGGER.error("[ArcadiaGuard] checkZone failed for {}, defaulting to not-blocked",
                player.getGameProfile().getName(), e);
            return Optional.empty();
        }
    }

    public Optional<ProtectedZone> findContaining(Level level, BlockPos pos) {
        String dim = DimensionUtils.keyOf(level);
        if (!this.zonesByDimension.containsKey(dim)) return Optional.empty();
        List<ProtectedZone> cands = candidates(dim, pos);
        if (cands.isEmpty()) return Optional.empty();
        List<ProtectedZone> matching = null;
        ProtectedZone first = null;
        for (ProtectedZone zone : cands) {
            // Disabled zones are invisible to all callers — matches check()/checkZone() semantics.
            if (zone.enabled() && zone.contains(dim, pos)) {
                if (first == null) { first = zone; }
                else {
                    if (matching == null) { matching = new ArrayList<>(); matching.add(first); }
                    matching.add(zone);
                }
            }
        }
        if (first == null) return Optional.empty();
        return Optional.of(matching != null ? ZoneConflictResolver.resolve(matching) : first);
    }

    public boolean setParent(Level level, String zoneName, @Nullable String parentName) {
        Optional<ProtectedZone> zone = get(level, zoneName);
        if (zone.isEmpty()) return false;
        zone.get().setParent(parentName);
        scheduleWrite(zone.get());
        return true;
    }

    public boolean setBounds(Level level, String zoneName, BlockPos a, BlockPos b) {
        Optional<ProtectedZone> zone = get(level, zoneName);
        if (zone.isEmpty()) return false;
        zone.get().setBounds(a, b);
        rebuildDimIndex(DimensionUtils.keyOf(level));
        scheduleWrite(zone.get());
        return true;
    }

    public boolean setEnabled(Level level, String zoneName, boolean enabled) {
        Optional<ProtectedZone> zone = get(level, zoneName);
        if (zone.isEmpty()) return false;
        zone.get().setEnabled(enabled);
        scheduleWrite(zone.get());
        FlagMixinHelper.invalidateHasZoneCache(DimensionUtils.keyOf(level));
        return true;
    }

    public boolean setInheritDimFlags(Level level, String zoneName, boolean inherit) {
        Optional<ProtectedZone> zone = get(level, zoneName);
        if (zone.isEmpty()) return false;
        zone.get().setInheritDimFlags(inherit);
        scheduleWrite(zone.get());
        return true;
    }

    /** S-H20 : ajoute ou retire un item de la liste des items bloqu\u00e9s de la zone. */
    public boolean setItemBlocked(Level level, String zoneName,
            net.minecraft.resources.ResourceLocation itemId, boolean add) {
        Optional<ProtectedZone> zone = get(level, zoneName);
        if (zone.isEmpty() || itemId == null) return false;
        boolean changed = add ? zone.get().blockItem(itemId) : zone.get().unblockItem(itemId);
        if (changed) scheduleWrite(zone.get());
        return true;
    }

    public boolean setFlag(Level level, String zoneName, String flagId, Object value) {
        Optional<ProtectedZone> zone = get(level, zoneName);
        if (zone.isEmpty()) return false;
        Object oldValue = zone.get().flagValues().get(flagId);
        zone.get().setFlag(flagId, value);
        scheduleWrite(zone.get());
        this.flagRegistry.get(flagId).ifPresent(flag ->
            NeoForge.EVENT_BUS.post(new FlagChangedEvent(zone.get(), flag, oldValue, value)));
        return true;
    }

    public boolean resetFlag(Level level, String zoneName, String flagId) {
        Optional<ProtectedZone> zone = get(level, zoneName);
        if (zone.isEmpty()) return false;
        Object oldValue = zone.get().flagValues().get(flagId);
        zone.get().resetFlag(flagId);
        scheduleWrite(zone.get());
        this.flagRegistry.get(flagId).ifPresent(flag ->
            NeoForge.EVENT_BUS.post(new FlagChangedEvent(zone.get(), flag, oldValue, null)));
        return true;
    }

    // ── Spatial index helpers ────────────────────────────────────────────────────

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private void rebuildIndex() {
        this.chunkIndex.clear();
        this.globalZones.clear();
        for (Map.Entry<String, Map<String, ProtectedZone>> entry : this.zonesByDimension.entrySet()) {
            for (ProtectedZone zone : entry.getValue().values()) {
                indexZone(entry.getKey(), zone);
            }
        }
    }

    private void rebuildDimIndex(String dim) {
        this.chunkIndex.remove(dim);
        this.globalZones.remove(dim);
        Map<String, ProtectedZone> dimZones = this.zonesByDimension.get(dim);
        if (dimZones != null) {
            for (ProtectedZone zone : dimZones.values()) indexZone(dim, zone);
        }
    }

    /** Indexes a zone into the live (this.*) spatial indexes. */
    private void indexZone(String dim, ProtectedZone zone) {
        indexZoneInto(dim, zone, this.chunkIndex, this.globalZones);
    }

    /**
     * Indexes a zone into the provided target maps.
     * Used both for incremental indexing (live maps) and reload (local maps before swap).
     */
    private static void indexZoneInto(String dim, ProtectedZone zone,
            Map<String, Map<Long, List<ProtectedZone>>> targetChunkIndex,
            Map<String, List<ProtectedZone>> targetGlobalZones) {
        if (zone.minX() == Integer.MIN_VALUE) {
            targetGlobalZones.computeIfAbsent(dim, k -> new CopyOnWriteArrayList<>()).add(zone);
            return;
        }
        // TODO: octree par section verticale si beaucoup de zones empilées
        int cMinX = Math.floorDiv(zone.minX(), 16);
        int cMaxX = Math.floorDiv(zone.maxX(), 16);
        int cMinZ = Math.floorDiv(zone.minZ(), 16);
        int cMaxZ = Math.floorDiv(zone.maxZ(), 16);
        Map<Long, List<ProtectedZone>> dimIdx =
            targetChunkIndex.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        for (int cx = cMinX; cx <= cMaxX; cx++) {
            for (int cz = cMinZ; cz <= cMaxZ; cz++) {
                dimIdx.computeIfAbsent(chunkKey(cx, cz), k -> new CopyOnWriteArrayList<>()).add(zone);
            }
        }
    }

    /** Returns zones that could contain {@code pos} (chunk-filtered + global). */
    private List<ProtectedZone> candidates(String dim, BlockPos pos) {
        int cx = Math.floorDiv(pos.getX(), 16);
        int cz = Math.floorDiv(pos.getZ(), 16);
        List<ProtectedZone> fromChunk = this.chunkIndex
            .getOrDefault(dim, Map.of())
            .getOrDefault(chunkKey(cx, cz), List.of());
        List<ProtectedZone> fromGlobal = this.globalZones.getOrDefault(dim, List.of());
        if (fromGlobal.isEmpty()) return fromChunk;
        if (fromChunk.isEmpty()) return fromGlobal;
        List<ProtectedZone> merged = new ArrayList<>(fromChunk.size() + fromGlobal.size());
        merged.addAll(fromChunk);
        merged.addAll(fromGlobal);
        return merged;
    }

    private static final java.util.concurrent.atomic.AtomicReference<Boolean> LP_AVAILABLE_CACHE
        = new java.util.concurrent.atomic.AtomicReference<>();

    private static boolean lpAvailable() {
        Boolean v = LP_AVAILABLE_CACHE.get();
        if (v == null) { v = LuckPermsCompat.isAvailable(); LP_AVAILABLE_CACHE.set(v); }
        return v;
    }

    private boolean isLpMember(ServerPlayer player, ProtectedZone zone) {
        var lp = lpAvailable() ? LuckPermsCompat.checker() : null;
        return lp != null && lp.resolveRole(player, zone.name()) != null;
    }

    private void scheduleWrite(ProtectedZone zone) {
        // Snapshot mutable collections on the calling (main) thread before the runnable is enqueued,
        // so the writer thread never iterates live LinkedHashMap/HashSet under concurrent mutation (C6).
        ProtectedZone snapshot = new ProtectedZone(
            zone.name(), zone.dimension(),
            zone.minX(), zone.minY(), zone.minZ(),
            zone.maxX(), zone.maxY(), zone.maxZ(),
            new java.util.HashSet<>(zone.whitelistedPlayers()),
            zone.parent(), zone.priority(),
            new java.util.LinkedHashMap<>(zone.flagValues()),
            new java.util.LinkedHashMap<>(zone.memberRoles()));
        snapshot.setEnabled(zone.enabled());
        snapshot.setInheritDimFlags(zone.inheritDimFlags());
        Path file = ArcadiaGuardPaths.zoneFile(zone.dimension(), zone.name());
        // Cle coalescing partagee write/delete : la derniere operation pour la meme zone gagne.
        String key = "zone|" + zone.dimension() + "|" + zone.name();
        this.asyncZoneWriter.schedule(key, () -> {
            try {
                ZoneSerializer.write(snapshot, file);
            } catch (IOException e) {
                ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Failed to save zone '{}'", snapshot.name(), e);
            }
        });
    }

    private void scheduleDeletion(String dimKey, String name) {
        Path file = ArcadiaGuardPaths.zoneFile(dimKey, name);
        String key = "zone|" + dimKey + "|" + name;
        this.asyncZoneWriter.schedule(key, () -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Failed to delete zone file for '{}'", name, e);
            }
        });
    }
}

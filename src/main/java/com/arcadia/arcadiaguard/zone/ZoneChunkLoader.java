package com.arcadia.arcadiaguard.zone;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Gestion du flag {@link BuiltinFlags#CHUNKLOAD}. Quand active sur une zone,
 * force le chargement de tous les chunks dans la bbox de la zone via
 * {@link ServerLevel#setChunkForced(int, int, boolean)}.
 *
 * <p>Tracking interne en memoire : pour chaque zone active, on garde l'ensemble
 * des chunks qu'on a forces afin de les unforce proprement a la desactivation
 * ou a la suppression de la zone (sans toucher aux chunks forces par d'autres
 * systemes).
 *
 * <p>Les zones globales (bornes Integer.MIN_VALUE) sont IGNOREES (chargerait la
 * dim entiere). Les zones > 400 chunks (~ 6400 blocs cote) declenchent un
 * warning serveur — mais sont chargees quand meme.
 */
public final class ZoneChunkLoader {

    private ZoneChunkLoader() {}

    /** dim -> zone name -> set de ChunkPos forces par ArcadiaGuard. */
    private static final Map<String, Map<String, Set<ChunkPos>>> FORCED =
        new ConcurrentHashMap<>();

    private static final int CHUNK_WARN_THRESHOLD = 400;

    /**
     * Applique ou retire le force-load selon l'etat du flag {@code CHUNKLOAD}
     * sur la zone.
     */
    public static void apply(ServerLevel level, ProtectedZone zone, GuardService guard) {
        if (zone == null) return;
        boolean shouldLoad = !isGlobalZone(zone)
            && guard.isZoneDenying(zone, BuiltinFlags.CHUNKLOAD, level);
        if (shouldLoad) forceLoad(level, zone);
        else unload(level, zone);
    }

    /**
     * Force-load tous les chunks dans la bbox de la zone. Si la zone etait deja
     * chargee, calcule les chunks a ajouter/retirer (apres un SET_BOUNDS par ex.).
     */
    public static void forceLoad(ServerLevel level, ProtectedZone zone) {
        if (isGlobalZone(zone)) return;
        Set<ChunkPos> desired = chunksOf(zone);
        if (desired.size() > CHUNK_WARN_THRESHOLD) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Zone '{}' triggers chunkload for {} chunks "
                + "(threshold {}). Watch server RAM.", zone.name(), desired.size(), CHUNK_WARN_THRESHOLD);
        }
        String dim = level.dimension().location().toString();
        Map<String, Set<ChunkPos>> byZone = FORCED.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        Set<ChunkPos> current = byZone.getOrDefault(zone.name(), Set.of());

        // Ajout des nouveaux
        for (ChunkPos pos : desired) {
            if (!current.contains(pos)) level.setChunkForced(pos.x, pos.z, true);
        }
        // Retrait des chunks qui ne sont plus dans la zone (apres resize)
        for (ChunkPos pos : current) {
            if (!desired.contains(pos)) level.setChunkForced(pos.x, pos.z, false);
        }
        byZone.put(zone.name(), new HashSet<>(desired));
        ArcadiaGuard.debugInfo("[ArcadiaGuard] Zone '{}' @ {}: {} chunks force-loaded.",
            zone.name(), dim, desired.size());
    }

    /** Unforce tous les chunks precedemment forces par cette zone. */
    public static void unload(ServerLevel level, ProtectedZone zone) {
        String dim = level.dimension().location().toString();
        Map<String, Set<ChunkPos>> byZone = FORCED.get(dim);
        if (byZone == null) return;
        Set<ChunkPos> current = byZone.remove(zone.name());
        if (current == null || current.isEmpty()) return;
        for (ChunkPos pos : current) {
            level.setChunkForced(pos.x, pos.z, false);
        }
        ArcadiaGuard.debugInfo("[ArcadiaGuard] Zone '{}' @ {}: {} chunks released.",
            zone.name(), dim, current.size());
    }

    /**
     * Au boot du serveur : ré-applique le force-load pour toutes les zones
     * actives (apres un restart, les chunks sont déchargés par défaut).
     */
    public static void refreshAll(MinecraftServer server, GuardService guard) {
        FORCED.clear();
        for (ServerLevel level : server.getAllLevels()) {
            var zones = guard.zoneManager().zones(level);
            for (var iz : zones) {
                if (iz instanceof ProtectedZone zone) apply(level, zone, guard);
            }
        }
    }

    /** Calcule l'ensemble des ChunkPos couverts par la bbox de la zone. */
    private static Set<ChunkPos> chunksOf(ProtectedZone zone) {
        int minCx = Math.floorDiv(zone.minX(), 16);
        int maxCx = Math.floorDiv(zone.maxX(), 16);
        int minCz = Math.floorDiv(zone.minZ(), 16);
        int maxCz = Math.floorDiv(zone.maxZ(), 16);
        Set<ChunkPos> out = new HashSet<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                out.add(new ChunkPos(cx, cz));
            }
        }
        return out;
    }

    private static boolean isGlobalZone(ProtectedZone zone) {
        return zone.minX() == Integer.MIN_VALUE || zone.minZ() == Integer.MIN_VALUE;
    }
}

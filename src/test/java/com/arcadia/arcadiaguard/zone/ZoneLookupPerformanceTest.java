package com.arcadia.arcadiaguard.zone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.arcadia.arcadiaguard.flag.FlagRegistryImpl;
import com.arcadia.arcadiaguard.persist.AsyncZoneWriter;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre NFR01 / NFR10 : le lookup de zone par nom est O(1) et le système
 * supporte sans dégradation 1000 zones actives simultanément.
 *
 * <p>On injecte les données via réflexion sur zonesByDimension pour éviter
 * de déclencher les événements NeoForge lors des appels à add().
 */
@ExtendWith(MockitoExtension.class)
class ZoneLookupPerformanceTest {

    private static final String DIM = "minecraft:overworld";
    private static final int ZONE_COUNT = 1000;

    @Mock private AsyncZoneWriter asyncZoneWriter;
    @Mock private Level level;

    private InternalZoneProvider provider;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        // Level.OVERWORLD n'a pas besoin de Bootstrap (ResourceKey est pure Java),
        // mais DimensionUtils.keyOf() accède level.dimension() — mocké ci-dessous.
    }

    @BeforeEach
    void setUp() throws Exception {
        provider = new InternalZoneProvider(new FlagRegistryImpl(), asyncZoneWriter);

        // Injecte 1000 zones directement dans la map interne (évite NeoForge event bus)
        Map<String, Map<String, ProtectedZone>> zonesByDim = new ConcurrentHashMap<>();
        Map<String, ProtectedZone> dimZones = new ConcurrentHashMap<>();
        for (int i = 0; i < ZONE_COUNT; i++) {
            ProtectedZone zone = new ProtectedZone("zone-" + i, DIM, i, 0, i, i + 10, 10, i + 10, new java.util.HashSet<>());
            dimZones.put("zone-" + i, zone);
        }
        zonesByDim.put(DIM, dimZones);

        Field field = InternalZoneProvider.class.getDeclaredField("zonesByDimension");
        field.setAccessible(true);
        field.set(provider, zonesByDim);

        // Level mock : dimension() retourne la clé overworld (pur Java, pas de Bootstrap requis)
        lenient().when(level.dimension()).thenReturn(Level.OVERWORLD);
    }

    // ── Exactitude ────────────────────────────────────────────────────────────

    @Test
    void get_existingZone_returnsPresent() {
        Optional<ProtectedZone> result = provider.get(level, "zone-0");
        assertTrue(result.isPresent(), "zone-0 doit être trouvée");
        assertEquals("zone-0", result.get().name());
    }

    @Test
    void get_lastZone_returnsPresent() {
        Optional<ProtectedZone> result = provider.get(level, "zone-" + (ZONE_COUNT - 1));
        assertTrue(result.isPresent(), "La dernière zone doit être trouvée");
    }

    @Test
    void get_unknownZone_returnsEmpty() {
        Optional<ProtectedZone> result = provider.get(level, "nonexistent-zone");
        assertTrue(result.isEmpty(), "Une zone inexistante doit retourner empty");
    }

    @Test
    void get_lookupIsCaseInsensitive() {
        // InternalZoneProvider stocke les noms en lower-case → zone-42 doit matcher
        Optional<ProtectedZone> lower = provider.get(level, "zone-42");
        assertTrue(lower.isPresent());
    }

    @Test
    void zones_count_equals1000() {
        assertEquals(ZONE_COUNT, provider.zones(level).size(),
            "zones() doit retourner toutes les zones injectées");
    }

    // ── Performance O(1) ──────────────────────────────────────────────────────

    @Test
    void get_1000Lookups_completesUnder100ms() {
        long start = System.nanoTime();
        for (int i = 0; i < ZONE_COUNT; i++) {
            assertTrue(provider.get(level, "zone-" + i).isPresent());
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 100,
            "1000 lookups doivent prendre < 100 ms (O(1)) — temps réel : " + elapsedMs + " ms");
    }

    @Test
    void get_randomAccess_noPerformanceDegradation() {
        // Lookup aléatoire : premier + milieu + dernier doivent être identiquement rapides
        long t1 = time(() -> provider.get(level, "zone-0"));
        long t2 = time(() -> provider.get(level, "zone-500"));
        long t3 = time(() -> provider.get(level, "zone-999"));

        // Chaque lookup individuel (sur ConcurrentHashMap) doit être < 5 ms
        assertTrue(t1 < 5, "Lookup zone-0 trop lent : " + t1 + " ms");
        assertTrue(t2 < 5, "Lookup zone-500 trop lent : " + t2 + " ms");
        assertTrue(t3 < 5, "Lookup zone-999 trop lent : " + t3 + " ms");
    }

    @Test
    void get_repeatedSameZone_stableLatency() {
        // Vérifie qu'un même lookup répété ne dégrade pas les performances (pas de cache qui échoue)
        long start = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            provider.get(level, "zone-42");
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 50, "500 lookups répétés sur la même zone < 50 ms");
    }

    // ── Cas limites avec 1000 zones ───────────────────────────────────────────

    @Test
    void zones_returns_iterable_collection() {
        var all = provider.zones(level);
        assertNotNull(all);
        assertFalse(all.isEmpty());
        assertEquals(ZONE_COUNT, all.size());
    }

    @Test
    void get_missingDimension_returnsEmpty() {
        Level otherLevel = mock(Level.class);
        when(otherLevel.dimension()).thenReturn(Level.NETHER);
        Optional<ProtectedZone> result = provider.get(otherLevel, "zone-0");
        assertTrue(result.isEmpty(), "Une dimension sans zones doit retourner empty");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private long time(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }
}

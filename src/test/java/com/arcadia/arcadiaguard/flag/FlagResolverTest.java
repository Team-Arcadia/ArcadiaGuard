package com.arcadia.arcadiaguard.flag;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Tests FlagResolver : chaîne parent, fallback dim, parent désactivé,
 * cycle protection. POJO pur, pas de NeoForge.
 */
class FlagResolverTest {

    private static final BooleanFlag PVP = new BooleanFlag("pvp", true);

    private ProtectedZone newZone(String name, String parent) {
        return new ProtectedZone(name, "minecraft:overworld",
            0, 0, 0, 10, 10, 10, new HashSet<>(), parent, 0, null, null, null);
    }

    @Test
    void resolve_zoneFlagWins() {
        var zone = newZone("z", null);
        zone.setFlag(PVP.id(), false);
        var resolved = FlagResolver.resolveOptional(zone, PVP, n -> Optional.empty(), null);
        assertEquals(Optional.of(false), resolved);
    }

    @Test
    void resolve_unsetReturnsEmpty() {
        var zone = newZone("z", null);
        var resolved = FlagResolver.resolveOptional(zone, PVP, n -> Optional.empty(), null);
        assertTrue(resolved.isEmpty());
    }

    @Test
    void resolve_inheritsFromParent() {
        var parent = newZone("parent", null);
        parent.setFlag(PVP.id(), false);
        var child = newZone("child", "parent");
        Function<String, Optional<ProtectedZone>> lookup = n -> "parent".equals(n) ? Optional.of(parent) : Optional.empty();
        var resolved = FlagResolver.resolveOptional(child, PVP, lookup, null);
        assertEquals(Optional.of(false), resolved);
    }

    @Test
    void resolve_childFlagOverridesParent() {
        var parent = newZone("parent", null);
        parent.setFlag(PVP.id(), false);
        var child = newZone("child", "parent");
        child.setFlag(PVP.id(), true);
        Function<String, Optional<ProtectedZone>> lookup = n -> "parent".equals(n) ? Optional.of(parent) : Optional.empty();
        var resolved = FlagResolver.resolveOptional(child, PVP, lookup, null);
        assertEquals(Optional.of(true), resolved);
    }

    // ── Regression 1.5.5 fix #12 : parent désactivé ne propage plus ses flags ──

    @Test
    void resolve_disabledParent_isIgnored() {
        var parent = newZone("parent", null);
        parent.setFlag(PVP.id(), false);
        parent.setEnabled(false);
        var child = newZone("child", "parent");
        Function<String, Optional<ProtectedZone>> lookup = n -> "parent".equals(n) ? Optional.of(parent) : Optional.empty();
        var resolved = FlagResolver.resolveOptional(child, PVP, lookup, null);
        assertTrue(resolved.isEmpty(),
            "regression 1.5.5 : un parent désactivé ne doit plus propager ses flags");
    }

    @Test
    void resolve_disabledParent_fallsBackToDim() {
        // Si parent disabled, l'enfant doit passer aux dim flags directement.
        var parent = newZone("parent", null);
        parent.setFlag(PVP.id(), false);
        parent.setEnabled(false);
        var child = newZone("child", "parent");
        Function<String, Optional<ProtectedZone>> lookup = n -> "parent".equals(n) ? Optional.of(parent) : Optional.empty();
        Map<String, Object> dimFlags = new LinkedHashMap<>();
        dimFlags.put(PVP.id(), true);
        Function<String, Map<String, Object>> dimLookup = d -> "minecraft:overworld".equals(d) ? dimFlags : null;
        var resolved = FlagResolver.resolveOptional(child, PVP, lookup, dimLookup);
        assertEquals(Optional.of(true), resolved,
            "parent désactivé → fallback dim doit s'appliquer (1.5.5)");
    }

    // ── Dim fallback ────────────────────────────────────────────────────────

    @Test
    void resolve_unsetEverywhere_fallsBackToDim() {
        var zone = newZone("z", null);
        Map<String, Object> dimFlags = new LinkedHashMap<>();
        dimFlags.put(PVP.id(), false);
        Function<String, Map<String, Object>> dimLookup = d -> dimFlags;
        var resolved = FlagResolver.resolveOptional(zone, PVP, n -> Optional.empty(), dimLookup);
        assertEquals(Optional.of(false), resolved);
    }

    @Test
    void resolve_zoneNotInheritingDim_skipsDimFallback() {
        var zone = newZone("z", null);
        zone.setInheritDimFlags(false);
        Map<String, Object> dimFlags = new LinkedHashMap<>();
        dimFlags.put(PVP.id(), false);
        Function<String, Map<String, Object>> dimLookup = d -> dimFlags;
        var resolved = FlagResolver.resolveOptional(zone, PVP, n -> Optional.empty(), dimLookup);
        assertTrue(resolved.isEmpty(),
            "inheritDimFlags=false doit ignorer la dim malgré flag présent");
    }

    // ── Chain order ─────────────────────────────────────────────────────────

    @Test
    void resolve_grandparentChain() {
        var grand = newZone("grand", null);
        grand.setFlag(PVP.id(), false);
        var parent = newZone("parent", "grand");
        var child = newZone("child", "parent");
        Function<String, Optional<ProtectedZone>> lookup = n -> switch (n) {
            case "grand" -> Optional.of(grand);
            case "parent" -> Optional.of(parent);
            default -> Optional.empty();
        };
        var resolved = FlagResolver.resolveOptional(child, PVP, lookup, null);
        assertEquals(Optional.of(false), resolved);
    }

    @Test
    void resolve_disabledGrandparent_fallsThroughToParentThenDim() {
        // grand désactivé, parent vide, dim a la valeur → enfant prend la dim.
        var grand = newZone("grand", null);
        grand.setFlag(PVP.id(), false);
        grand.setEnabled(false);
        var parent = newZone("parent", "grand");
        var child = newZone("child", "parent");
        Function<String, Optional<ProtectedZone>> lookup = n -> switch (n) {
            case "grand" -> Optional.of(grand);
            case "parent" -> Optional.of(parent);
            default -> Optional.empty();
        };
        Map<String, Object> dimFlags = new LinkedHashMap<>();
        dimFlags.put(PVP.id(), true);
        Function<String, Map<String, Object>> dimLookup = d -> dimFlags;
        var resolved = FlagResolver.resolveOptional(child, PVP, lookup, dimLookup);
        assertEquals(Optional.of(true), resolved);
    }

    // ── Cycle protection ────────────────────────────────────────────────────

    @Test
    void resolve_cycleIsCutOff() {
        var a = newZone("a", "b");
        var b = newZone("b", "a");
        Function<String, Optional<ProtectedZone>> lookup = n -> switch (n) {
            case "a" -> Optional.of(a);
            case "b" -> Optional.of(b);
            default -> Optional.empty();
        };
        var resolved = FlagResolver.resolveOptional(a, PVP, lookup, null);
        assertTrue(resolved.isEmpty(),
            "cycle a→b→a doit être détecté et stoppé sans StackOverflow");
    }

    @Test
    void resolve_missingParent_returnsEmpty() {
        var child = newZone("child", "ghost");
        var resolved = FlagResolver.resolveOptional(child, PVP, n -> Optional.empty(), null);
        assertTrue(resolved.isEmpty());
    }
}

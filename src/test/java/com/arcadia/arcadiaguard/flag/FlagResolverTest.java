package com.arcadia.arcadiaguard.flag;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class FlagResolverTest {

    private static final BooleanFlag FLAG = new BooleanFlag("block-break", true);

    private static ProtectedZone zone(String name, Boolean flagValue) {
        Map<String, Object> flags = new LinkedHashMap<>();
        if (flagValue != null) flags.put(FLAG.id(), flagValue);
        return new ProtectedZone(name, "minecraft:overworld",
            0, 0, 0, 9, 9, 9, new HashSet<>(), null, 0, flags);
    }

    private static ProtectedZone childZone(String name, String parentName, Boolean flagValue) {
        Map<String, Object> flags = new LinkedHashMap<>();
        if (flagValue != null) flags.put(FLAG.id(), flagValue);
        return new ProtectedZone(name, "minecraft:overworld",
            0, 0, 0, 9, 9, 9, new HashSet<>(), parentName, 0, flags);
    }

    // --- Valeur explicite sur la zone ---

    @Test
    void resolve_explicitValue_returnsThatValue() {
        ProtectedZone z = zone("z", false);
        assertFalse(FlagResolver.resolve(z, FLAG));
    }

    @Test
    void resolve_explicitTrue_returnsTrue() {
        ProtectedZone z = zone("z", true);
        assertTrue(FlagResolver.resolve(z, FLAG));
    }

    // --- Valeur par défaut ---

    @Test
    void resolve_noFlagSet_returnsDefault() {
        ProtectedZone z = zone("z", null);
        // Default du FLAG est true
        assertTrue(FlagResolver.resolve(z, FLAG));
    }

    // --- Héritage parent ---

    @Test
    void resolve_inheritFromParent() {
        ProtectedZone parent = zone("parent", false);
        ProtectedZone child = childZone("child", "parent", null);

        Function<String, Optional<ProtectedZone>> lookup = name -> {
            if ("parent".equals(name)) return Optional.of(parent);
            return Optional.empty();
        };

        assertFalse(FlagResolver.resolve(child, FLAG, lookup));
    }

    @Test
    void resolve_childOverridesParent() {
        ProtectedZone parent = zone("parent", false);
        ProtectedZone child = childZone("child", "parent", true);

        Function<String, Optional<ProtectedZone>> lookup = name -> {
            if ("parent".equals(name)) return Optional.of(parent);
            return Optional.empty();
        };

        assertTrue(FlagResolver.resolve(child, FLAG, lookup));
    }

    @Test
    void resolve_parentNotFound_usesDefault() {
        ProtectedZone child = childZone("child", "missing-parent", null);
        assertTrue(FlagResolver.resolve(child, FLAG, name -> Optional.empty()));
    }

    // --- Fallback dim-flags ---

    @Test
    void resolve_dimFlagFallback_usedWhenZoneAndParentUnset() {
        ProtectedZone z = zone("z", null);
        z.setInheritDimFlags(true); // explicit: this test verifies the inheritance IS active
        Map<String, Object> dimFlags = Map.of(FLAG.id(), false);

        boolean result = FlagResolver.resolve(z, FLAG, name -> Optional.empty(), dim -> dimFlags);
        assertFalse(result);
    }

    @Test
    void resolve_dimFlagIgnored_whenInheritDimFlagsFalse() {
        ProtectedZone z = zone("z", null);
        z.setInheritDimFlags(false);
        Map<String, Object> dimFlags = Map.of(FLAG.id(), false);

        boolean result = FlagResolver.resolve(z, FLAG, name -> Optional.empty(), dim -> dimFlags);
        assertTrue(result);
    }

    @Test
    void resolve_dimFlagIgnored_whenNullLookup() {
        ProtectedZone z = zone("z", null);
        assertTrue(FlagResolver.resolve(z, FLAG, name -> Optional.empty(), null));
    }

    // --- Profondeur MAX_DEPTH ---

    @Test
    void resolve_maxDepthReached_returnsDefault() {
        // Chaîne de 12 parents (> MAX_DEPTH=10) — le flag au bout n'est jamais atteint
        ProtectedZone[] chain = new ProtectedZone[12];
        for (int i = 0; i < 12; i++) {
            String parentName = (i < 11) ? "zone-" + (i + 1) : null;
            chain[i] = new ProtectedZone("zone-" + i, "minecraft:overworld",
                0, 0, 0, 9, 9, 9, new HashSet<>(), parentName, 0, null);
        }
        chain[11].setFlag(FLAG.id(), false);

        Map<String, ProtectedZone> byName = new LinkedHashMap<>();
        for (ProtectedZone z : chain) byName.put(z.name(), z);
        Function<String, Optional<ProtectedZone>> lookup = name -> Optional.ofNullable(byName.get(name));

        // zone-0 depth=0 → zone-1 depth=1 → ... → zone-10 depth=10 = MAX_DEPTH → arrêt
        // zone-11 (flag=false) jamais atteinte → default (true)
        assertTrue(FlagResolver.resolve(chain[0], FLAG, lookup));
    }

    // --- resolveOptional ---

    @Test
    void resolveOptional_noFlagAnywhere_returnsEmpty() {
        ProtectedZone z = zone("z", null);
        Optional<Boolean> result = FlagResolver.resolveOptional(z, FLAG, name -> Optional.empty(), null);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveOptional_explicitValue_returnsPresent() {
        ProtectedZone z = zone("z", false);
        Optional<Boolean> result = FlagResolver.resolveOptional(z, FLAG, name -> Optional.empty(), null);
        assertTrue(result.isPresent());
        assertFalse(result.get());
    }
}

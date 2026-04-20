package com.arcadia.arcadiaguard.flag;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the effective value of a flag for a zone, walking up the parent chain,
 * then falling back to dimension-level flags if the zone inherits them.
 */
public final class FlagResolver {

    private static final int MAX_DEPTH = 10;

    private FlagResolver() {}

    /** Resolves with no parent chain and no dim-flag fallback. */
    public static <T> T resolve(ProtectedZone zone, Flag<T> flag) {
        return resolve(zone, flag, ignored -> Optional.empty(), null, 0);
    }

    /** Resolves walking the parent chain. No dim-flag fallback. */
    public static <T> T resolve(ProtectedZone zone, Flag<T> flag,
                                Function<String, Optional<ProtectedZone>> parentLookup) {
        return resolve(zone, flag, parentLookup, null, 0);
    }

    /**
     * Resolves walking the parent chain, then falls back to dimension flags.
     * Pass {@code dimFlagLookup = dimKey -> dimFlagStore.flags(dimKey)} on the server.
     */
    public static <T> T resolve(ProtectedZone zone, Flag<T> flag,
                                Function<String, Optional<ProtectedZone>> parentLookup,
                                @Nullable Function<String, Map<String, Object>> dimFlagLookup) {
        return resolve(zone, flag, parentLookup, dimFlagLookup, 0);
    }

    @SuppressWarnings("unchecked")
    private static <T> T resolve(ProtectedZone zone, Flag<T> flag,
                                 Function<String, Optional<ProtectedZone>> parentLookup,
                                 @Nullable Function<String, Map<String, Object>> dimFlagLookup,
                                 int depth) {
        return (T) resolveOptional(zone, flag, parentLookup, dimFlagLookup, depth).orElse(flag.defaultValue());
    }

    /** Resolves walking zone → parent chain → dimension. Returns empty if never explicitly set. */
    public static <T> Optional<T> resolveOptional(ProtectedZone zone, Flag<T> flag,
                                                  Function<String, Optional<ProtectedZone>> parentLookup,
                                                  @Nullable Function<String, Map<String, Object>> dimFlagLookup) {
        return resolveOptional(zone, flag, parentLookup, dimFlagLookup, 0);
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> resolveOptional(ProtectedZone zone, Flag<T> flag,
                                                   Function<String, Optional<ProtectedZone>> parentLookup,
                                                   @Nullable Function<String, Map<String, Object>> dimFlagLookup,
                                                   int depth) {
        if (depth > MAX_DEPTH) {
            ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Parent chain depth exceeded {} for zone '{}' resolving flag '{}'. Possible cycle in parent references — resolution stopped.",
                MAX_DEPTH, zone.name(), flag.id());
            return Optional.empty();
        }

        Object value = zone.flagValues().get(flag.id());
        if (value != null) {
            try { return Optional.of((T) value); }
            catch (ClassCastException e) {
                ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Type mismatch for flag '{}' on zone '{}' — stored type incompatible with flag's expected type. Zone config may be corrupt.",
                    flag.id(), zone.name());
            }
        }

        if (zone.parent() != null) {
            Optional<ProtectedZone> parentZone = parentLookup.apply(zone.parent());
            if (parentZone.isPresent()) {
                Optional<T> fromParent = resolveOptional(parentZone.get(), flag, parentLookup, dimFlagLookup, depth + 1);
                if (fromParent.isPresent()) return fromParent;
                // Parent chain explicit about nothing: fall through to dim (if this zone inherits).
            }
        }

        // Fallback: dimension-level flags (only if this zone inherits them)
        if (dimFlagLookup != null && zone.inheritDimFlags()) {
            Map<String, Object> dimFlags = dimFlagLookup.apply(zone.dimension());
            if (dimFlags != null) {
                Object dimVal = dimFlags.get(flag.id());
                if (dimVal != null) {
                    try { return Optional.of((T) dimVal); }
                    catch (ClassCastException e) {
                        ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Type mismatch for dim flag '{}' in '{}' — stored type incompatible with flag's expected type.",
                            flag.id(), zone.dimension());
                    }
                }
            }
        }

        return Optional.empty();
    }
}

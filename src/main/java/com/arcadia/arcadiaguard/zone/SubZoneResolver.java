package com.arcadia.arcadiaguard.zone;

import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.flag.FlagResolver;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.level.Level;

/**
 * Resolves flag inheritance from parent zones to sub-zones.
 * Delegates to {@link FlagResolver} for the actual chain walk.
 */
public final class SubZoneResolver {

    private final ZoneManager zoneManager;

    public SubZoneResolver(ZoneManager zoneManager) {
        this.zoneManager = zoneManager;
    }

    /**
     * Walks the parent chain of {@code zone} to find the effective value for {@code flag}.
     * Returns empty only if {@code zone} has no parent.
     */
    public <T> Optional<T> resolveFromParent(ProtectedZone zone, Flag<T> flag, Level level) {
        if (zone.parent() == null) return Optional.empty();
        @SuppressWarnings("unchecked")
        Function<String, Optional<ProtectedZone>> lookup = name -> (Optional<ProtectedZone>)(Optional<?>) zoneManager.get(level, name);
        Optional<ProtectedZone> parentZone = lookup.apply(zone.parent());
        if (parentZone.isEmpty()) return Optional.empty();
        return Optional.of(FlagResolver.resolve(parentZone.get(), flag, lookup));
    }
}

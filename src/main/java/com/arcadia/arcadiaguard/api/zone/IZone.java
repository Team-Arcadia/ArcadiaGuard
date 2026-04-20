package com.arcadia.arcadiaguard.api.zone;

import com.arcadia.arcadiaguard.api.flag.Flag;
import java.util.Set;
import java.util.UUID;

/**
 * Public read-only view of a protected zone.
 * Implementations are provided by ArcadiaGuard internals.
 */
public interface IZone {

    /** Returns the unique name of this zone within its dimension. */
    String name();

    /** Returns the dimension resource-location key (e.g. {@code "minecraft:overworld"}). */
    String dimension();

    /** @return western boundary (inclusive) */
    int minX();
    /** @return bottom boundary (inclusive) */
    int minY();
    /** @return northern boundary (inclusive) */
    int minZ();
    /** @return eastern boundary (inclusive) */
    int maxX();
    /** @return top boundary (inclusive) */
    int maxY();
    /** @return southern boundary (inclusive) */
    int maxZ();

    /** Returns the resolution priority. Higher value wins over lower. */
    int priority();

    /** Returns the UUIDs of players whitelisted in this zone. */
    Set<UUID> whitelistedPlayers();

    /**
     * Returns the value of the given flag as set on this zone, or the flag's default value.
     * Never returns null.
     */
    <T> T flag(Flag<T> flag);
}

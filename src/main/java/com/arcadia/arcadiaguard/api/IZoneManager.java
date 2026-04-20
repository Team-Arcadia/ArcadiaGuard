package com.arcadia.arcadiaguard.api;

import com.arcadia.arcadiaguard.api.zone.IZone;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.Collection;
import java.util.Optional;

/**
 * Public read-only zone manager interface exposed by ArcadiaGuard.
 *
 * <p>Obtain an instance via {@link ArcadiaGuardAPI#get()}.
 * All methods are safe to call from the server thread only.
 *
 * <p>This interface covers read operations only. Zone mutations (add, remove, set flags…)
 * are handled internally by ArcadiaGuard commands and are not part of the public API contract.
 */
public interface IZoneManager {

    /**
     * Returns all zones currently loaded for the given level.
     *
     * <p>The returned collection is a read-only snapshot; modifications are not reflected
     * in zone state and may throw {@link UnsupportedOperationException}.
     *
     * @param level the server level to query
     * @return an unmodifiable collection of zones in the level (may be empty)
     */
    Collection<IZone> zones(Level level);

    /**
     * Returns the zone with the given name in the given level, or empty if none exists.
     *
     * @param level the server level to search in
     * @param name  the zone name (case-insensitive)
     * @return an {@link Optional} containing the matching zone, or empty
     */
    Optional<IZone> get(Level level, String name);

    /**
     * Returns the highest-priority zone containing {@code pos} in the given level, or empty.
     *
     * <p>When multiple zones overlap the position, conflict resolution (priority → volume → name)
     * determines which zone is returned.
     *
     * @param level the server level to search in
     * @param pos   the block position to test
     * @return an {@link Optional} containing the winning zone, or empty if no zone covers the position
     */
    Optional<IZone> findZoneContaining(Level level, BlockPos pos);
}

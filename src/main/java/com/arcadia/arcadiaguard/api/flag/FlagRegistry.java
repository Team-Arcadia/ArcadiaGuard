package com.arcadia.arcadiaguard.api.flag;

import java.util.Collection;
import java.util.Optional;

/**
 * Global registry for all zone flags, both built-in and registered by third-party mods.
 * Obtain the instance via {@link com.arcadia.arcadiaguard.api.ArcadiaGuardAPI}.
 */
public interface FlagRegistry {

    /**
     * Registers a flag. Must be called during {@code FMLCommonSetupEvent}.
     *
     * @param flag the flag to register
     * @throws IllegalArgumentException if a flag with the same id is already registered
     */
    void register(Flag<?> flag);

    /**
     * Retrieves a flag by its id.
     *
     * @param id the kebab-case flag id
     * @return the flag, or empty if not found
     */
    Optional<Flag<?>> get(String id);

    /** Returns all registered flags. */
    Collection<Flag<?>> all();
}

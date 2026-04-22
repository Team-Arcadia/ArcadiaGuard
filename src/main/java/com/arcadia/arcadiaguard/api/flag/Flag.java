package com.arcadia.arcadiaguard.api.flag;

/**
 * Represents a typed zone flag that can be applied to protected zones.
 *
 * @param <T> the value type of this flag (e.g. {@link Boolean}, {@link Integer})
 */
public interface Flag<T> {

    /** Returns the unique kebab-case identifier of this flag (e.g. {@code "block-break"}). */
    String id();

    /** Returns the default value applied when no value is explicitly set on a zone. Never null. */
    T defaultValue();

    /** Short human-readable description shown in the GUI tooltip. May be empty. */
    default String description() { return ""; }

    /**
     * Returns the NeoForge mod ID that must be loaded for this flag to be shown in the GUI.
     * Returns an empty string if the flag is always visible (no mod dependency).
     */
    default String requiredMod() { return ""; }

    /**
     * Classe de frequence utilisee par l'admin pour desactiver des classes entieres de flags
     * via la config. Defaut {@link FlagFrequency#NORMAL}.
     */
    default FlagFrequency frequency() { return FlagFrequency.NORMAL; }
}

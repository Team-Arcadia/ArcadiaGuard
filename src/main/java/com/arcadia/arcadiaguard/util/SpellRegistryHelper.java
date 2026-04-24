package com.arcadia.arcadiaguard.util;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Accede via reflexion aux registres de sorts Ars Nouveau / Iron's Spellbooks pour
 * peupler l'autocompletion des ListFlag {@code ars-spell-blacklist/whitelist}
 * et {@code irons-spell-blacklist/whitelist}.
 *
 * <p>Cache construit au premier appel (lazy). Retourne une liste vide si le mod
 * n'est pas charge ou si la reflexion echoue — zero dependance compile-time.
 */
public final class SpellRegistryHelper {

    private static volatile List<String> ARS_CACHE;
    private static volatile List<String> IRONS_CACHE;
    private static volatile List<String> MOB_CACHE;
    private static volatile boolean ARS_WARNED = false;
    private static volatile boolean IRONS_WARNED = false;
    private static volatile boolean MOB_WARNED = false;

    private SpellRegistryHelper() {}

    /** Retourne la liste triee des glyph IDs d'Ars Nouveau, ex. "ars_nouveau:glyph_blink". */
    public static List<String> arsNouveauGlyphs() {
        List<String> cache = ARS_CACHE;
        if (cache != null) return cache;
        Set<String> ids = new TreeSet<>();
        try {
            Class<?> registryCls = Class.forName("com.hollingsworth.arsnouveau.api.registry.GlyphRegistry");
            var getMap = registryCls.getMethod("getSpellpartMap");
            Object map = getMap.invoke(null);
            if (map instanceof java.util.Map<?, ?> m) {
                for (Object key : m.keySet()) {
                    if (key instanceof net.minecraft.resources.ResourceLocation rl) {
                        ids.add(rl.toString());
                    }
                }
            }
        } catch (Throwable t) {
            if (!ARS_WARNED) {
                ARS_WARNED = true;
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Ars Nouveau spell registry unavailable (logged once): {}", t.toString());
            }
        }
        cache = List.copyOf(ids);
        ARS_CACHE = cache;
        return cache;
    }

    /** Retourne la liste triee des spell IDs d'Iron's Spellbooks, ex. "irons_spellbooks:fireball". */
    public static List<String> ironsSpells() {
        List<String> cache = IRONS_CACHE;
        if (cache != null) return cache;
        Set<String> ids = new TreeSet<>();
        try {
            Class<?> registryCls = Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
            var field = registryCls.getDeclaredField("SPELLS");
            field.setAccessible(true);
            Object deferred = field.get(null);
            if (deferred instanceof net.neoforged.neoforge.registries.DeferredRegister<?> dr) {
                for (var holder : dr.getEntries()) {
                    net.minecraft.resources.ResourceLocation rl = holder.getId();
                    if (rl != null) ids.add(rl.toString());
                }
            }
        } catch (Throwable t) {
            if (!IRONS_WARNED) {
                IRONS_WARNED = true;
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Iron's Spellbooks spell registry unavailable (logged once): {}", t.toString());
            }
        }
        cache = List.copyOf(ids);
        IRONS_CACHE = cache;
        return cache;
    }

    /**
     * Retourne la liste triee de tous les types d'entite enregistres
     * (ex. "minecraft:zombie", "mutantmonsters:mutant_creeper"). Utilise pour
     * l'autocompletion du flag {@code mob-spawn-list}.
     */
    public static List<String> entityTypes() {
        List<String> cache = MOB_CACHE;
        if (cache != null) return cache;
        Set<String> ids = new TreeSet<>();
        try {
            var registry = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
            for (var type : registry) {
                var key = registry.getKey(type);
                if (key != null) ids.add(key.toString());
            }
        } catch (Throwable t) {
            if (!MOB_WARNED) {
                MOB_WARNED = true;
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Entity type registry unavailable (logged once): {}", t.toString());
            }
        }
        cache = List.copyOf(ids);
        MOB_CACHE = cache;
        return cache;
    }

    /** Retourne les suggestions pour un flag donne, ou liste vide si non pertinent. */
    public static List<String> suggestionsFor(String flagId) {
        if (flagId == null) return Collections.emptyList();
        if (flagId.startsWith("ars-spell-")) return arsNouveauGlyphs();
        if (flagId.startsWith("irons-spell-")) return ironsSpells();
        if ("mob-spawn-list".equals(flagId)) return entityTypes();
        return Collections.emptyList();
    }

    /** Invalide le cache (a appeler apres un reload de mods — rarement utile). */
    public static void invalidate() {
        ARS_CACHE = null;
        IRONS_CACHE = null;
        MOB_CACHE = null;
    }
}

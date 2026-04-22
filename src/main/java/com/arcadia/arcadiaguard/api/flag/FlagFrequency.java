package com.arcadia.arcadiaguard.api.flag;

/**
 * Classe de frequence d'un flag. Utilise par l'admin-config pour activer/desactiver
 * des classes entieres de flags cote perf (equivalent YAWP FlagFrequency).
 *
 * <p>Exemple : si {@code disabled_frequencies = [VERY_HIGH]} dans la config,
 * tous les flags tagues VERY_HIGH (leaf-decay, sculk-spread, water-spread...)
 * passent en no-op — les events correspondants bypassent entierement ArcadiaGuard.
 *
 * <p>Ordre de gravite croissante : plus le niveau est haut, plus le flag est
 * appele souvent (hot path). Les admins peuvent desactiver les niveaux qui
 * posent probleme sur leur hardware.
 */
public enum FlagFrequency {
    /** Occasionnel, effet minime (ex. entry/exit, chunkload). */
    NEGLIGIBLE,
    /** Quelques fois par minute (ex. mob-spawn, explosions). */
    LOW,
    /** Frequent sur interaction joueur (ex. block-break, pvp). Default. */
    NORMAL,
    /** Par seconde ou plus (ex. heal-amount, fall-damage). */
    HIGH,
    /** Par tick ou par chunk (ex. leaf-decay, sculk-spread, water-spread). */
    VERY_HIGH
}

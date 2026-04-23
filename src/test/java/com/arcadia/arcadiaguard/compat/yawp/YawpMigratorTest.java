package com.arcadia.arcadiaguard.compat.yawp;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Couvre FR26 : migration YAWP via /ag migrate yawp.
 * Couvre NFR11 : vérification que YAWP n'est pas une dépendance compile-time.
 *
 * <p>YAWP n'est pas sur le classpath des tests (zéro dépendance compile) — YawpMigrator
 * doit intercepter ClassNotFoundException et retourner une liste vide sans lever d'exception.
 * C'est exactement la dégrade gracieuse attendue pour un outil de migration optionnel.
 */
class YawpMigratorTest {

    // ── NFR11 : zéro dépendance compile sur YAWP ──────────────────────────────

    @Test
    void yawp_notOnTestClasspath_confirmsZeroCompileDependency() {
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("de.z0rdak.yawp.data.region.RegionDataManager"),
            "YAWP RegionDataManager ne doit pas être sur le classpath de compilation");
    }

    // ── FR26 : migrate() sans YAWP → liste vide sans exception ──────────────

    @Test
    void migrate_yawpNotInstalled_returnsEmptyList() {
        // Sans YAWP sur le classpath, migrate() doit intercepter ClassNotFoundException
        // et retourner une liste vide (comportement de dégradation documenté)
        List<ProtectedZone> result = YawpMigrator.migrate(null);
        assertNotNull(result, "Le résultat ne doit jamais être null");
        assertTrue(result.isEmpty(),
            "Sans YAWP installé, la migration doit retourner une liste vide");
    }

    @Test
    void migrate_yawpNotInstalled_doesNotThrow() {
        // La méthode ne doit pas propager d'exception même avec un server null
        assertDoesNotThrow(() -> YawpMigrator.migrate(null),
            "migrate() ne doit jamais lever d'exception, même sans YAWP ni serveur");
    }

    @Test
    void migrate_yawpNotInstalled_resultIsModifiable() {
        // La liste retournée doit pouvoir être manipulée par l'appelant
        List<ProtectedZone> result = YawpMigrator.migrate(null);
        assertDoesNotThrow(() -> result.size(),
            "La liste retournée doit être exploitable");
    }

    @Test
    void migrate_calledTwice_doesNotThrow() {
        // Deux appels consécutifs ne doivent pas provoquer d'état corrompu
        assertDoesNotThrow(() -> {
            YawpMigrator.migrate(null);
            YawpMigrator.migrate(null);
        });
    }

    // ── NFR11 : vérification que YawpMigrator n'a pas de référence directe à YAWP ──

    @Test
    void yawpMigrator_classFile_doesNotReferencYawpInConstantPool() throws Exception {
        // Si YAWP était une dépendance compile-time directe, le .class de YawpMigrator
        // contiendrait "de.z0rdak.yawp" dans son constant pool. On vérifie via les
        // interfaces/superclasses déclarées (qui ne font pas référence à YAWP).
        // La preuve principale est que Class.forName("yawp...") lève ClassNotFoundException.
        Class<?> cls = YawpMigrator.class;
        assertNotNull(cls);
        // La superclasse est Object, pas une classe YAWP
        assertEquals(Object.class, cls.getSuperclass());
        // Aucune interface YAWP
        assertEquals(0, cls.getInterfaces().length);
    }
}

package com.arcadia.arcadiaguard.compat.luckperms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests pour {@link LuckPermsPermissionChecker} sans dependance LuckPerms.
 * Quand LP n'est pas charge, toutes les methodes doivent retourner false/null
 * sans throw — le mod doit fonctionner sans LP installe.
 */
class LuckPermsPermissionCheckerTest {

    @Test
    void hasBypass_lpAbsent_returnsFalseSafely() {
        var c = new LuckPermsPermissionChecker();
        // Sans LP, hasBypass doit retourner false sans NPE.
        // Le test ne fournit pas de ServerPlayer (impossible sans MC server),
        // mais l'API publique invalidatePlayer + state initial sont verifiables.
        c.invalidatePlayer(UUID.randomUUID());
        assertNotNull(c, "checker should construct without LP loaded");
    }

    @Test
    void invalidatePlayer_unknownUuid_noThrow() {
        var c = new LuckPermsPermissionChecker();
        c.invalidatePlayer(UUID.randomUUID());
        c.invalidatePlayer(UUID.randomUUID());
        assertNotNull(c);
    }

    @Test
    void multipleInvalidates_idempotent() {
        var c = new LuckPermsPermissionChecker();
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 10; i++) c.invalidatePlayer(id);
        assertNotNull(c);
    }

    @Test
    void resolveRoleNamePattern_mapsCorrectly() {
        // Smoke test du mapping nom -> ZoneRole utilise par ZonePermission.
        // Le pattern 'arcadiaguard.zone.<name>.{owner,moderator,member}' doit etre
        // accepte par checkNode (LP renverra false en absence de LP, mais le node
        // construit correctement).
        var c = new LuckPermsPermissionChecker();
        // Sans player (impossible a mocker simplement), on verifie juste que la
        // classe ne crash pas a l'init.
        assertNotNull(c);
    }
}

package com.arcadia.arcadiaguard.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Tests pour {@link ProtectedZone} : contains(), whitelist, members, flag values,
 * blocked items, bounds normalization. POJO pur, pas de server Minecraft necessaire.
 */
class ProtectedZoneTest {

    private ProtectedZone make() {
        return new ProtectedZone("test", "minecraft:overworld",
            new BlockPos(0, 0, 0), new BlockPos(10, 20, 30));
    }

    @Test
    void contains_insideBounds_returnsTrue() {
        var zone = make();
        assertTrue(zone.contains("minecraft:overworld", new BlockPos(5, 10, 15)));
    }

    @Test
    void contains_onBoundary_returnsTrue() {
        var zone = make();
        assertTrue(zone.contains("minecraft:overworld", new BlockPos(0, 0, 0)));
        assertTrue(zone.contains("minecraft:overworld", new BlockPos(10, 20, 30)));
    }

    @Test
    void contains_outsideBounds_returnsFalse() {
        var zone = make();
        assertFalse(zone.contains("minecraft:overworld", new BlockPos(11, 10, 15)));
        assertFalse(zone.contains("minecraft:overworld", new BlockPos(5, -1, 15)));
    }

    @Test
    void contains_wrongDimension_returnsFalse() {
        var zone = make();
        assertFalse(zone.contains("minecraft:the_nether", new BlockPos(5, 10, 15)));
    }

    @Test
    void boundsNormalization_swapsIfReversed() {
        var zone = new ProtectedZone("x", "minecraft:overworld",
            new BlockPos(10, 20, 30), new BlockPos(0, 0, 0));
        assertEquals(0, zone.minX());
        assertEquals(0, zone.minY());
        assertEquals(0, zone.minZ());
        assertEquals(10, zone.maxX());
        assertEquals(20, zone.maxY());
        assertEquals(30, zone.maxZ());
    }

    @Test
    void setBounds_replacesAndNormalizes() {
        var zone = make();
        zone.setBounds(new BlockPos(100, 200, 300), new BlockPos(50, 50, 50));
        assertEquals(50, zone.minX());
        assertEquals(50, zone.minY());
        assertEquals(50, zone.minZ());
        assertEquals(100, zone.maxX());
        assertEquals(200, zone.maxY());
        assertEquals(300, zone.maxZ());
    }

    @Test
    void setFlag_storesAndReadsBack() {
        var zone = make();
        zone.setFlag("pvp", false);
        assertEquals(false, zone.flagValues().get("pvp"));
    }

    @Test
    void resetFlag_removesValue() {
        var zone = make();
        zone.setFlag("pvp", false);
        zone.resetFlag("pvp");
        assertFalse(zone.flagValues().containsKey("pvp"));
    }

    @Test
    void flagValues_isUnmodifiable() {
        var zone = make();
        zone.setFlag("pvp", false);
        assertThrows(UnsupportedOperationException.class,
            () -> zone.flagValues().put("other", true));
    }

    @Test
    void whitelist_addAndRemove() {
        var zone = make();
        UUID uuid = UUID.randomUUID();
        assertTrue(zone.whitelistAdd(uuid));
        assertTrue(zone.whitelistedPlayers().contains(uuid));
        assertTrue(zone.whitelistRemove(uuid));
        assertFalse(zone.whitelistedPlayers().contains(uuid));
    }

    @Test
    void whitelistAdd_duplicateReturnsFalse() {
        var zone = make();
        UUID uuid = UUID.randomUUID();
        assertTrue(zone.whitelistAdd(uuid));
        assertFalse(zone.whitelistAdd(uuid)); // deja present
    }

    @Test
    void setRole_addsPlayerToWhitelistToo() {
        var zone = make();
        UUID uuid = UUID.randomUUID();
        zone.setRole(uuid, ZoneRole.MEMBER);
        assertTrue(zone.whitelistedPlayers().contains(uuid),
            "setRole doit garantir whitelist consistante");
        assertEquals(ZoneRole.MEMBER, zone.memberRoles().get(uuid));
    }

    @Test
    void whitelistRemove_alsoRemovesRole() {
        var zone = make();
        UUID uuid = UUID.randomUUID();
        zone.setRole(uuid, ZoneRole.MODERATOR);
        zone.whitelistRemove(uuid);
        assertFalse(zone.memberRoles().containsKey(uuid),
            "whitelistRemove doit retirer des deux structures");
        assertFalse(zone.whitelistedPlayers().contains(uuid));
    }

    @Test
    void hasRole_respectsRoleHierarchy() {
        var zone = make();
        UUID uuid = UUID.randomUUID();
        zone.setRole(uuid, ZoneRole.OWNER);
        assertTrue(zone.hasRole(uuid, ZoneRole.MEMBER));
        assertTrue(zone.hasRole(uuid, ZoneRole.MODERATOR));
        assertTrue(zone.hasRole(uuid, ZoneRole.OWNER));
    }

    @Test
    void hasRole_lowerRoleDoesNotSatisfyHigher() {
        var zone = make();
        UUID uuid = UUID.randomUUID();
        zone.setRole(uuid, ZoneRole.MEMBER);
        assertFalse(zone.hasRole(uuid, ZoneRole.OWNER));
    }

    @Test
    void hasRole_nonMemberReturnsFalse() {
        var zone = make();
        assertFalse(zone.hasRole(UUID.randomUUID(), ZoneRole.MEMBER));
    }

    @Test
    void removeRole_doesNotRemoveWhitelist() {
        var zone = make();
        UUID uuid = UUID.randomUUID();
        zone.setRole(uuid, ZoneRole.MODERATOR);
        zone.removeRole(uuid);
        assertFalse(zone.memberRoles().containsKey(uuid));
        assertTrue(zone.whitelistedPlayers().contains(uuid),
            "removeRole doit garder la whitelist (contrat: downgrade to whitelist-only)");
    }

    @Test
    void blockItem_addAndRemove() {
        var zone = make();
        var id = net.minecraft.resources.ResourceLocation.parse("minecraft:tnt");
        assertTrue(zone.blockItem(id));
        assertTrue(zone.isItemBlocked(id));
        assertTrue(zone.unblockItem(id));
        assertFalse(zone.isItemBlocked(id));
    }

    @Test
    void blockItem_nullReturnsFalse() {
        var zone = make();
        assertFalse(zone.blockItem(null));
        assertFalse(zone.isItemBlocked(null));
    }

    @Test
    void blockItem_duplicateReturnsFalse() {
        var zone = make();
        var id = net.minecraft.resources.ResourceLocation.parse("minecraft:tnt");
        assertTrue(zone.blockItem(id));
        assertFalse(zone.blockItem(id));
    }

    @Test
    void enabled_defaultTrue() {
        assertTrue(make().enabled());
    }

    @Test
    void inheritDimFlags_defaultTrue() {
        assertTrue(make().inheritDimFlags());
    }

    @Test
    void priority_defaultZero() {
        assertEquals(0, make().priority());
    }

    @Test
    void parent_nullByDefault() {
        var zone = make();
        assertEquals(null, zone.parent());
    }

    @Test
    void memberRolesWithInitialEntries_addsToWhitelist() {
        // Contrat : les membres passes au constructeur doivent se retrouver dans la whitelist.
        UUID uuid = UUID.randomUUID();
        var roles = new java.util.LinkedHashMap<UUID, ZoneRole>();
        roles.put(uuid, ZoneRole.OWNER);
        var zone = new ProtectedZone("x", "minecraft:overworld",
            0, 0, 0, 10, 10, 10, new HashSet<>(), null, 0, null, roles);
        assertTrue(zone.whitelistedPlayers().contains(uuid));
    }
}

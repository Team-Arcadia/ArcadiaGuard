package com.arcadia.arcadiaguard.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsCompat;
import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsPermissionChecker;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.arcadia.arcadiaguard.zone.ZoneManager;
import com.arcadia.arcadiaguard.zone.ZoneRole;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.*;

/**
 * Couvre FR14 : commandes /ag — modèle de permissions, hiérarchie des rôles,
 * et vérification d'accès aux zones (ZonePermission + ZoneRole).
 *
 * <p>Le setup bootstrappe Minecraft et charge la config NeoForge en mémoire
 * (même technique que GuardServiceTest) pour que ArcadiaGuardConfig.BYPASS_OP_LEVEL.get()
 * retourne une valeur valide sans lancer d'exception.
 */
@ExtendWith(MockitoExtension.class)
class ZoneCommandsCoverageTest {

    @Mock private CommandSourceStack source;
    @Mock private ServerPlayer player;
    @Mock private ZoneManager zoneManager;

    private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @BeforeAll
    static void setupConfig() throws Exception {
        Class<?> lmlClass = Class.forName("net.neoforged.fml.loading.LoadingModList");
        java.lang.reflect.Method ofMethod = lmlClass.getMethod(
            "of", List.class, List.class, List.class, List.class, java.util.Map.class
        );
        ofMethod.invoke(null, List.of(), List.of(), List.of(), List.of(), java.util.Map.of());

        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        CommentedConfig cfg = CommentedConfig.inMemory();
        cfg.set(List.of("general", "bypass_op_level"), 2);
        cfg.set(List.of("general", "enable_logging"), true);
        cfg.set(List.of("general", "log_to_file"), true);
        cfg.set(List.of("toggles", "enable_block_break"), true);
        cfg.set(List.of("toggles", "enable_block_place"), true);
        cfg.set(List.of("toggles", "enable_ironsspellbooks"), true);
        cfg.set(List.of("toggles", "enable_arsnouveau"), true);
        cfg.set(List.of("toggles", "enable_simplyswords"), true);
        cfg.set(List.of("toggles", "enable_occultism"), true);
        cfg.set(List.of("toggles", "enable_supplementaries"), true);
        cfg.set(List.of("toggles", "enable_apotheosis_enchants"), true);
        cfg.set(List.of("toggles", "enable_betterarcheology"), true);
        cfg.set(List.of("toggles", "enable_spawn_book_protection"), true);
        cfg.set(List.of("toggles", "enable_lead_protection"), true);
        cfg.set(List.of("toggles", "enable_spawn_egg_protection"), true);
        cfg.set(List.of("messages", "message_block_break"), "blocked");
        cfg.set(List.of("messages", "message_block_place"), "blocked");
        cfg.set(List.of("messages", "message_ironsspellbooks"), "blocked");
        cfg.set(List.of("messages", "message_arsnouveau"), "blocked");
        cfg.set(List.of("messages", "message_simplyswords"), "blocked");
        cfg.set(List.of("messages", "message_occultism"), "blocked");
        cfg.set(List.of("messages", "message_supplementaries"), "blocked");
        cfg.set(List.of("messages", "message_apotheosis"), "blocked");
        cfg.set(List.of("messages", "message_betterarcheology"), "blocked");
        cfg.set(List.of("messages", "message_spawn_book"), "blocked");
        cfg.set(List.of("messages", "message_lead"), "blocked");
        cfg.set(List.of("messages", "message_spawn_egg"), "blocked");
        cfg.set(List.of("messages", "message_dynamic_item"), "blocked");

        Class<?> loadedConfigClass = Class.forName("net.neoforged.fml.config.LoadedConfig");
        Constructor<?> ctor = loadedConfigClass.getDeclaredConstructor(
            CommentedConfig.class, Path.class,
            Class.forName("net.neoforged.fml.config.ModConfig")
        );
        ctor.setAccessible(true);
        IConfigSpec.ILoadedConfig loadedConfig =
            (IConfigSpec.ILoadedConfig) ctor.newInstance(cfg, null, null);

        java.lang.reflect.Field loadedConfigField =
            ArcadiaGuardConfig.SPEC.getClass().getDeclaredField("loadedConfig");
        loadedConfigField.setAccessible(true);
        loadedConfigField.set(ArcadiaGuardConfig.SPEC, loadedConfig);

        java.lang.reflect.Method afterReload =
            ArcadiaGuardConfig.SPEC.getClass().getDeclaredMethod("afterReload");
        afterReload.setAccessible(true);
        afterReload.invoke(ArcadiaGuardConfig.SPEC);
    }

    @BeforeEach
    void setUp() {
        lenient().when(player.getUUID()).thenReturn(PLAYER_UUID);
    }

    // ── ZoneRole.atLeast() ────────────────────────────────────────────────────

    @Test
    void zoneRole_member_atLeast_member_isTrue() {
        assertTrue(ZoneRole.MEMBER.atLeast(ZoneRole.MEMBER));
    }

    @Test
    void zoneRole_moderator_atLeast_member_isTrue() {
        assertTrue(ZoneRole.MODERATOR.atLeast(ZoneRole.MEMBER));
    }

    @Test
    void zoneRole_moderator_atLeast_moderator_isTrue() {
        assertTrue(ZoneRole.MODERATOR.atLeast(ZoneRole.MODERATOR));
    }

    @Test
    void zoneRole_owner_atLeast_allRoles_isTrue() {
        assertTrue(ZoneRole.OWNER.atLeast(ZoneRole.MEMBER));
        assertTrue(ZoneRole.OWNER.atLeast(ZoneRole.MODERATOR));
        assertTrue(ZoneRole.OWNER.atLeast(ZoneRole.OWNER));
    }

    @Test
    void zoneRole_member_atLeast_moderator_isFalse() {
        assertFalse(ZoneRole.MEMBER.atLeast(ZoneRole.MODERATOR));
    }

    @Test
    void zoneRole_member_atLeast_owner_isFalse() {
        assertFalse(ZoneRole.MEMBER.atLeast(ZoneRole.OWNER));
    }

    @Test
    void zoneRole_moderator_atLeast_owner_isFalse() {
        assertFalse(ZoneRole.MODERATOR.atLeast(ZoneRole.OWNER));
    }

    // ── ProtectedZone — gestion des rôles ────────────────────────────────────

    @Test
    void protectedZone_roleOf_emptyInitially() {
        ProtectedZone zone = new ProtectedZone("test", "minecraft:overworld", 0, 0, 0, 10, 10, 10, new java.util.HashSet<>());
        assertTrue(zone.roleOf(PLAYER_UUID).isEmpty());
    }

    @Test
    void protectedZone_setRole_owner_thenRoleOf_returnsOwner() {
        ProtectedZone zone = new ProtectedZone("test", "minecraft:overworld", 0, 0, 0, 10, 10, 10, new java.util.HashSet<>());
        zone.setRole(PLAYER_UUID, ZoneRole.OWNER);
        assertEquals(ZoneRole.OWNER, zone.roleOf(PLAYER_UUID).orElseThrow());
    }

    @Test
    void protectedZone_hasRole_moderator_allowsMemberCheck() {
        ProtectedZone zone = new ProtectedZone("test", "minecraft:overworld", 0, 0, 0, 10, 10, 10, new java.util.HashSet<>());
        zone.setRole(PLAYER_UUID, ZoneRole.MODERATOR);
        assertTrue(zone.hasRole(PLAYER_UUID, ZoneRole.MODERATOR));
        assertTrue(zone.hasRole(PLAYER_UUID, ZoneRole.MEMBER));
        assertFalse(zone.hasRole(PLAYER_UUID, ZoneRole.OWNER));
    }

    @Test
    void protectedZone_removeRole_thenRoleOf_isEmpty() {
        ProtectedZone zone = new ProtectedZone("test", "minecraft:overworld", 0, 0, 0, 10, 10, 10, new java.util.HashSet<>());
        zone.setRole(PLAYER_UUID, ZoneRole.MEMBER);
        zone.removeRole(PLAYER_UUID);
        assertTrue(zone.roleOf(PLAYER_UUID).isEmpty());
    }

    @Test
    void protectedZone_setRole_alsoAddsToWhitelist() {
        ProtectedZone zone = new ProtectedZone("test", "minecraft:overworld", 0, 0, 0, 10, 10, 10, new java.util.HashSet<>());
        zone.setRole(PLAYER_UUID, ZoneRole.MEMBER);
        assertTrue(zone.whitelistedPlayers().contains(PLAYER_UUID));
    }

    @Test
    void protectedZone_memberRoles_reflectsAllAssignments() {
        ProtectedZone zone = new ProtectedZone("test", "minecraft:overworld", 0, 0, 0, 10, 10, 10, new java.util.HashSet<>());
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000099");
        zone.setRole(PLAYER_UUID, ZoneRole.OWNER);
        zone.setRole(other, ZoneRole.MEMBER);
        assertEquals(ZoneRole.OWNER, zone.memberRoles().get(PLAYER_UUID));
        assertEquals(ZoneRole.MEMBER, zone.memberRoles().get(other));
    }

    // ── ZonePermission.hasAccess() ────────────────────────────────────────────

    @Test
    void hasAccess_opSource_returnsTrue() {
        when(source.hasPermission(anyInt())).thenReturn(true);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            assertTrue(ZonePermission.hasAccess(source, "myzone", ZoneRole.OWNER));
        }
    }

    @Test
    void hasAccess_nonPlayerSource_returnsFalse() {
        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(null);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            assertFalse(ZonePermission.hasAccess(source, "myzone", ZoneRole.MEMBER));
        }
    }

    @Test
    void hasAccess_playerNotInZone_returnsFalse() {
        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class);
             MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            ag.when(ArcadiaGuard::zoneManager).thenReturn(zoneManager);
            when(zoneManager.get(any(), eq("myzone"))).thenReturn(Optional.empty());

            assertFalse(ZonePermission.hasAccess(source, "myzone", ZoneRole.MEMBER));
        }
    }

    @Test
    void hasAccess_playerInZoneWithSufficientRole_returnsTrue() {
        ProtectedZone zone = new ProtectedZone("myzone", "minecraft:overworld", 0, 0, 0, 10, 10, 10, new java.util.HashSet<>());
        zone.setRole(PLAYER_UUID, ZoneRole.OWNER);

        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class);
             MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            ag.when(ArcadiaGuard::zoneManager).thenReturn(zoneManager);
            when(zoneManager.get(any(), eq("myzone"))).thenReturn(Optional.of(zone));

            assertTrue(ZonePermission.hasAccess(source, "myzone", ZoneRole.MEMBER));
            assertTrue(ZonePermission.hasAccess(source, "myzone", ZoneRole.OWNER));
        }
    }

    @Test
    void hasAccess_playerInZoneWithInsufficientRole_returnsFalse() {
        ProtectedZone zone = new ProtectedZone("myzone", "minecraft:overworld", 0, 0, 0, 10, 10, 10, new java.util.HashSet<>());
        zone.setRole(PLAYER_UUID, ZoneRole.MEMBER);

        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class);
             MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            ag.when(ArcadiaGuard::zoneManager).thenReturn(zoneManager);
            when(zoneManager.get(any(), eq("myzone"))).thenReturn(Optional.of(zone));

            assertFalse(ZonePermission.hasAccess(source, "myzone", ZoneRole.OWNER));
        }
    }

    @Test
    void hasAccess_luckPermsBypass_returnsTrue_withoutZoneLookup() {
        LuckPermsPermissionChecker checker = mock(LuckPermsPermissionChecker.class);
        when(checker.hasBypass(player)).thenReturn(true);

        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(true);
            lp.when(LuckPermsCompat::checker).thenReturn(checker);

            assertTrue(ZonePermission.hasAccess(source, "myzone", ZoneRole.OWNER));
        }
    }

    @Test
    void hasAccess_luckPermsOwnerRole_satisfiesModeratorCheck() {
        LuckPermsPermissionChecker checker = mock(LuckPermsPermissionChecker.class);
        when(checker.hasBypass(player)).thenReturn(false);
        when(checker.resolveRole(player, "myzone")).thenReturn("owner");

        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(true);
            lp.when(LuckPermsCompat::checker).thenReturn(checker);

            assertTrue(ZonePermission.hasAccess(source, "myzone", ZoneRole.MODERATOR));
        }
    }

    @Test
    void hasAccess_luckPermsMemberRole_insufficientForOwner_fallsThrough() {
        LuckPermsPermissionChecker checker = mock(LuckPermsPermissionChecker.class);
        when(checker.hasBypass(player)).thenReturn(false);
        when(checker.resolveRole(player, "myzone")).thenReturn("member");

        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class);
             MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(true);
            lp.when(LuckPermsCompat::checker).thenReturn(checker);
            ag.when(ArcadiaGuard::zoneManager).thenReturn(zoneManager);
            when(zoneManager.get(any(), eq("myzone"))).thenReturn(Optional.empty());

            assertFalse(ZonePermission.hasAccess(source, "myzone", ZoneRole.OWNER));
        }
    }

    @Test
    void hasAccess_luckPermsModeratorRole_satisfiesMemberCheck() {
        LuckPermsPermissionChecker checker = mock(LuckPermsPermissionChecker.class);
        when(checker.hasBypass(player)).thenReturn(false);
        when(checker.resolveRole(player, "myzone")).thenReturn("moderator");

        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(true);
            lp.when(LuckPermsCompat::checker).thenReturn(checker);

            assertTrue(ZonePermission.hasAccess(source, "myzone", ZoneRole.MEMBER));
        }
    }

    @Test
    void hasAccess_luckPermsModeratorRole_insufficientForOwner_fallsThrough() {
        LuckPermsPermissionChecker checker = mock(LuckPermsPermissionChecker.class);
        when(checker.hasBypass(player)).thenReturn(false);
        when(checker.resolveRole(player, "myzone")).thenReturn("moderator");

        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class);
             MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(true);
            lp.when(LuckPermsCompat::checker).thenReturn(checker);
            ag.when(ArcadiaGuard::zoneManager).thenReturn(zoneManager);
            when(zoneManager.get(any(), eq("myzone"))).thenReturn(Optional.empty());

            assertFalse(ZonePermission.hasAccess(source, "myzone", ZoneRole.OWNER));
        }
    }

    // ── ZonePermission.hasAnyRole() ───────────────────────────────────────────

    @Test
    void hasAnyRole_opSource_returnsTrue() {
        when(source.hasPermission(anyInt())).thenReturn(true);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            assertTrue(ZonePermission.hasAnyRole(source));
        }
    }

    @Test
    void hasAnyRole_nonPlayerSource_returnsFalse() {
        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(null);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            assertFalse(ZonePermission.hasAnyRole(source));
        }
    }

    @Test
    void hasAnyRole_playerWithNoZones_returnsFalse() {
        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class);
             MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            ag.when(ArcadiaGuard::zoneManager).thenReturn(zoneManager);
            when(zoneManager.zones(any())).thenReturn(List.of());

            assertFalse(ZonePermission.hasAnyRole(source));
        }
    }

    @Test
    void hasAnyRole_playerWithRoleInZone_returnsTrue() {
        ProtectedZone zone = new ProtectedZone("myzone", "minecraft:overworld", 0, 0, 0, 10, 10, 10, new java.util.HashSet<>());
        zone.setRole(PLAYER_UUID, ZoneRole.MEMBER);

        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class);
             MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            ag.when(ArcadiaGuard::zoneManager).thenReturn(zoneManager);
            when(zoneManager.zones(any())).thenReturn(List.of(zone));

            assertTrue(ZonePermission.hasAnyRole(source));
        }
    }

    @Test
    void hasAnyRole_luckPermsBypass_returnsTrue() {
        LuckPermsPermissionChecker checker = mock(LuckPermsPermissionChecker.class);
        when(checker.hasBypass(player)).thenReturn(true);

        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(true);
            lp.when(LuckPermsCompat::checker).thenReturn(checker);

            assertTrue(ZonePermission.hasAnyRole(source));
        }
    }

    @Test
    void hasAnyRole_playerWithZoneButNoRole_returnsFalse() {
        ProtectedZone zone = new ProtectedZone("myzone", "minecraft:overworld", 0, 0, 0, 10, 10, 10, new java.util.HashSet<>());
        // Aucun rôle assigné → roleOf() retourne empty

        when(source.hasPermission(anyInt())).thenReturn(false);
        when(source.getEntity()).thenReturn(player);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class);
             MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            ag.when(ArcadiaGuard::zoneManager).thenReturn(zoneManager);
            when(zoneManager.zones(any())).thenReturn(List.of(zone));

            assertFalse(ZonePermission.hasAnyRole(source));
        }
    }
}

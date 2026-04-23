package com.arcadia.arcadiaguard.guard;

import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsCompat;
import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsPermissionChecker;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.logging.ArcadiaGuardAuditLogger;
import com.arcadia.arcadiaguard.zone.DimensionFlagStore;
import com.arcadia.arcadiaguard.zone.ZoneManager;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuardServiceTest {

    @Mock private ServerPlayer player;
    @Mock private ZoneManager zoneManager;
    @Mock private ArcadiaGuardAuditLogger auditLogger;
    @Mock private DimensionFlagStore dimFlagStore;
    @Mock private MinecraftServer server;

    private GuardService guardService;

    private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final GameProfile GAME_PROFILE = new GameProfile(PLAYER_UUID, "TestPlayer");

    @BeforeAll
    static void setupConfig() throws Exception {
        Class<?> lmlClass = Class.forName("net.neoforged.fml.loading.LoadingModList");
        java.lang.reflect.Method ofMethod = lmlClass.getMethod(
            "of", List.class, List.class, List.class, List.class, java.util.Map.class);
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
            Class.forName("net.neoforged.fml.config.ModConfig"));
        ctor.setAccessible(true);
        IConfigSpec.ILoadedConfig loadedConfig = (IConfigSpec.ILoadedConfig) ctor.newInstance(cfg, null, null);
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
        guardService = new GuardService(zoneManager, auditLogger, dimFlagStore);
        lenient().when(player.getUUID()).thenReturn(PLAYER_UUID);
        lenient().when(player.getServer()).thenReturn(server);
        lenient().when(player.getGameProfile()).thenReturn(GAME_PROFILE);
    }

    // --- Mode debug désactive le bypass ---

    @Test
    void shouldBypass_debugMode_returnsFalse_regardlessOfPermissions() {
        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            guardService.toggleDebug(PLAYER_UUID);
            assertFalse(guardService.shouldBypass(player),
                "Le mode debug doit désactiver le bypass même pour un OP");
        }
    }

    @Test
    void shouldBypass_afterDebugCleared_resumesNormalBehavior() {
        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            when(server.getProfilePermissions(any())).thenReturn(2);

            guardService.toggleDebug(PLAYER_UUID);
            assertFalse(guardService.shouldBypass(player));

            guardService.clearDebug(PLAYER_UUID);
            assertTrue(guardService.shouldBypass(player));
        }
    }

    // --- Bypass OP via getProfilePermissions (ops.json direct) ---

    @Test
    void shouldBypass_opPlayer_returnsTrue() {
        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            when(server.getProfilePermissions(any())).thenReturn(2);
            assertTrue(guardService.shouldBypass(player));
        }
    }

    @Test
    void shouldBypass_nonOpPlayer_returnsFalse() {
        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            when(server.getProfilePermissions(any())).thenReturn(0);
            assertFalse(guardService.shouldBypass(player));
        }
    }

    @Test
    void shouldBypass_opLevelBelowThreshold_returnsFalse() {
        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            when(server.getProfilePermissions(any())).thenReturn(1);
            assertFalse(guardService.shouldBypass(player));
        }
    }

    // --- Protection contre le bypass via PermissionAPI tierce ---

    @Test
    void shouldBypass_thirdPartyModCannotGrantBypass() {
        // Reproduit le bug corrigé : un mod tiers pouvait intercepter hasPermissions()
        // et retourner true sans que le joueur soit OP dans ops.json.
        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            when(server.getProfilePermissions(any())).thenReturn(0);
            verify(player, never()).hasPermissions(anyInt());
            assertFalse(guardService.shouldBypass(player),
                "Un joueur sans OP dans ops.json ne doit pas bypasser même si un mod tiers dit true");
        }
    }

    // --- Exception → fail-safe retourne false ---

    @Test
    void shouldBypass_exceptionInProfilePermissions_returnsFalse() {
        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            when(player.getServer()).thenThrow(new RuntimeException("simulated error"));
            assertFalse(guardService.shouldBypass(player),
                "Une exception doit être attrapée et retourner false (fail-safe)");
        }
    }

    // --- Bypass LuckPerms ---

    @Test
    void shouldBypass_luckPermsGrantsBypass_returnsTrue() {
        LuckPermsPermissionChecker mockChecker = mock(LuckPermsPermissionChecker.class);
        when(mockChecker.hasBypass(player)).thenReturn(true);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(true);
            lp.when(LuckPermsCompat::checker).thenReturn(mockChecker);

            assertTrue(guardService.shouldBypass(player));
            verify(server, never()).getProfilePermissions(any());
        }
    }

    @Test
    void shouldBypass_luckPermsNoBypass_fallsBackToProfilePermissions() {
        LuckPermsPermissionChecker mockChecker = mock(LuckPermsPermissionChecker.class);
        when(mockChecker.hasBypass(player)).thenReturn(false);
        when(server.getProfilePermissions(any())).thenReturn(2);

        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(true);
            lp.when(LuckPermsCompat::checker).thenReturn(mockChecker);

            assertTrue(guardService.shouldBypass(player));
        }
    }

    // --- toggleDebug retourne l'état correct ---

    @Test
    void toggleDebug_firstCall_returnsTrue_debugIsOn() {
        assertTrue(guardService.toggleDebug(PLAYER_UUID));
        assertTrue(guardService.isDebugMode(PLAYER_UUID));
    }

    @Test
    void toggleDebug_secondCall_returnsFalse_debugIsOff() {
        guardService.toggleDebug(PLAYER_UUID);
        assertFalse(guardService.toggleDebug(PLAYER_UUID));
        assertFalse(guardService.isDebugMode(PLAYER_UUID));
    }
}

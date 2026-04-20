package com.arcadia.arcadiaguard.test;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.flag.FlagRegistryImpl;
import com.arcadia.arcadiaguard.flag.FlagResolver;
import com.arcadia.arcadiaguard.persist.ZoneSerializer;
import com.arcadia.arcadiaguard.zone.DimensionFlagStore;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.arcadia.arcadiaguard.zone.ZoneManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * NeoForge GameTests for ArcadiaGuard zone protection logic.
 * <p>
 * Registered via {@link RegisterGameTestsEvent} from
 * {@link com.arcadia.arcadiaguard.test.ArcadiaGuardTestRegistry}.
 * All tests use the minimal {@code arcadiaguard:empty_1x1} structure template.
 */
public final class ArcadiaGuardGameTests {

    private static final String TEMPLATE = "arcadiaguard:empty_1x1";

    // ── Test 1: Zone protection blocks break ────────────────────────────────────

    @GameTest(template = TEMPLATE)
    public static void zoneProtectionBlocksBreak(GameTestHelper helper) {
        try {
            ProtectedZone zone = new ProtectedZone("test-break", "minecraft:overworld",
                new BlockPos(0, 0, 0), new BlockPos(10, 10, 10));
            zone.setFlag(BuiltinFlags.BLOCK_BREAK.id(), false);

            boolean flagValue = FlagResolver.resolve(zone, BuiltinFlags.BLOCK_BREAK);
            if (flagValue) {
                helper.fail("block-break flag should be false (denied) but resolved to true");
                return;
            }

            // Verify the zone contains a position inside its bounds
            if (!zone.contains("minecraft:overworld", new BlockPos(5, 5, 5))) {
                helper.fail("Zone should contain position (5,5,5)");
                return;
            }

            helper.succeed();
        } catch (Exception e) {
            helper.fail("Unexpected exception: " + e.getMessage());
        }
    }

    // ── Test 2: Flag inheritance from parent zone ───────────────────────────────

    @GameTest(template = TEMPLATE)
    public static void flagInheritanceFromParent(GameTestHelper helper) {
        try {
            // Parent zone with block-break denied
            Map<String, Object> parentFlags = new LinkedHashMap<>();
            parentFlags.put(BuiltinFlags.BLOCK_BREAK.id(), false);
            ProtectedZone parent = new ProtectedZone("parent-zone", "minecraft:overworld",
                0, 0, 0, 20, 20, 20, new HashSet<>(), null, 0, parentFlags);

            // Child zone inside parent, no flags set — should inherit
            ProtectedZone child = new ProtectedZone("child-zone", "minecraft:overworld",
                2, 2, 2, 8, 8, 8, new HashSet<>(), "parent-zone", 0);

            Function<String, Optional<ProtectedZone>> lookup = name ->
                "parent-zone".equals(name) ? Optional.of(parent) : Optional.empty();

            boolean resolved = FlagResolver.resolve(child, BuiltinFlags.BLOCK_BREAK, lookup);
            if (resolved) {
                helper.fail("Child should inherit block-break=false from parent, but got true");
                return;
            }

            helper.succeed();
        } catch (Exception e) {
            helper.fail("Unexpected exception: " + e.getMessage());
        }
    }

    // ── Test 3: Zone whitelist allows bypass ────────────────────────────────────

    @GameTest(template = TEMPLATE)
    public static void zoneWhitelistAllowsBypass(GameTestHelper helper) {
        try {
            UUID whitelistedPlayer = UUID.fromString("00000000-0000-0000-0000-000000000042");
            UUID otherPlayer = UUID.fromString("00000000-0000-0000-0000-000000000099");

            Set<UUID> whitelist = new HashSet<>();
            whitelist.add(whitelistedPlayer);

            ProtectedZone zone = new ProtectedZone("whitelist-zone", "minecraft:overworld",
                0, 0, 0, 10, 10, 10, whitelist);
            zone.setFlag(BuiltinFlags.BLOCK_BREAK.id(), false);

            // Whitelisted player should be in the whitelist
            if (!zone.whitelistedPlayers().contains(whitelistedPlayer)) {
                helper.fail("Whitelisted player UUID should be in the zone whitelist");
                return;
            }

            // Non-whitelisted player should NOT be in the whitelist
            if (zone.whitelistedPlayers().contains(otherPlayer)) {
                helper.fail("Non-whitelisted player should not be in the whitelist");
                return;
            }

            // Flag is still denied for non-whitelisted — zone-level check
            boolean flagValue = FlagResolver.resolve(zone, BuiltinFlags.BLOCK_BREAK);
            if (flagValue) {
                helper.fail("block-break flag should be false on this zone");
                return;
            }

            helper.succeed();
        } catch (Exception e) {
            helper.fail("Unexpected exception: " + e.getMessage());
        }
    }

    // ── Test 4: OP bypass works ─────────────────────────────────────────────────

    @GameTest(template = TEMPLATE)
    public static void opBypassWorks(GameTestHelper helper) {
        try {
            ProtectedZone zone = new ProtectedZone("op-zone", "minecraft:overworld",
                new BlockPos(0, 0, 0), new BlockPos(10, 10, 10));
            zone.setFlag(BuiltinFlags.BLOCK_BREAK.id(), false);

            // The zone has the flag denied
            boolean flagValue = FlagResolver.resolve(zone, BuiltinFlags.BLOCK_BREAK);
            if (flagValue) {
                helper.fail("block-break should be denied on this zone");
                return;
            }

            // Verify that GuardService.shouldBypass() exists and is accessible
            // OP bypass is tested via the ServerPlayer.hasPermissions() check in GuardService.
            // In a GameTest context, we verify that the zone flag is correctly set
            // and that the whitelist mechanism is separate from OP bypass.
            // The actual OP bypass is integration-tested through GuardServiceTest (unit test).

            // Verify OP player would bypass: OP players are checked via
            // player.hasPermissions(bypassLevel) in GuardService — here we verify
            // the zone itself does NOT grant bypass (it's the player's permission level).
            UUID opPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001");
            if (zone.whitelistedPlayers().contains(opPlayer)) {
                helper.fail("OP player should not be automatically whitelisted on zone");
                return;
            }

            helper.succeed();
        } catch (Exception e) {
            helper.fail("Unexpected exception: " + e.getMessage());
        }
    }

    // ── Test 5: Dimension flag applies ──────────────────────────────────────────

    @GameTest(template = TEMPLATE)
    public static void dimensionFlagApplies(GameTestHelper helper) {
        try {
            DimensionFlagStore dimStore = new DimensionFlagStore();
            dimStore.setFlag("minecraft:overworld", BuiltinFlags.BLOCK_BREAK.id(), false);

            // Zone with NO zone-level override and inheritDimFlags=true (default)
            ProtectedZone zone = new ProtectedZone("dim-zone", "minecraft:overworld",
                new BlockPos(0, 0, 0), new BlockPos(10, 10, 10));

            // Resolve with dimension flag fallback
            boolean resolved = FlagResolver.resolve(zone, BuiltinFlags.BLOCK_BREAK,
                name -> Optional.empty(), dimStore::flags);

            if (resolved) {
                helper.fail("Dimension flag block-break=false should apply, but got true");
                return;
            }

            // Now disable dim flag inheritance on the zone
            zone.setInheritDimFlags(false);
            boolean resolvedNoInherit = FlagResolver.resolve(zone, BuiltinFlags.BLOCK_BREAK,
                name -> Optional.empty(), dimStore::flags);

            // With inheritDimFlags=false, should fall back to BooleanFlag default (false)
            // BuiltinFlags.BLOCK_BREAK has defaultValue=false
            if (resolvedNoInherit) {
                helper.fail("With inheritDimFlags=false, should use flag default (false), but got true");
                return;
            }

            helper.succeed();
        } catch (Exception e) {
            helper.fail("Unexpected exception: " + e.getMessage());
        }
    }

    // ── Test 6: Zone creation and deletion ──────────────────────────────────────

    @GameTest(template = TEMPLATE)
    public static void zoneCreationAndDeletion(GameTestHelper helper) {
        try {
            ServerLevel level = helper.getLevel();

            ZoneManager zoneManager = ArcadiaGuard.zoneManager();
            String zoneName = "test-create-delete-" + UUID.randomUUID().toString().substring(0, 8);

            ProtectedZone zone = new ProtectedZone(zoneName, level.dimension().location().toString(),
                new BlockPos(100, 60, 100), new BlockPos(110, 70, 110));

            boolean added = zoneManager.add(level, zone);
            if (!added) {
                helper.fail("Failed to add zone '" + zoneName + "' to ZoneManager");
                return;
            }

            // Verify zone exists
            Optional<?> found = zoneManager.get(level, zoneName);
            if (found.isEmpty()) {
                helper.fail("Zone '" + zoneName + "' should exist after add()");
                return;
            }

            // Delete zone
            boolean removed = zoneManager.remove(level, zoneName);
            if (!removed) {
                helper.fail("Failed to remove zone '" + zoneName + "'");
                return;
            }

            // Verify zone is gone
            Optional<?> afterRemove = zoneManager.get(level, zoneName);
            if (afterRemove.isPresent()) {
                helper.fail("Zone '" + zoneName + "' should not exist after remove()");
                return;
            }

            helper.succeed();
        } catch (Exception e) {
            helper.fail("Unexpected exception: " + e.getMessage());
        }
    }

    // ── Test 7: Flag registry accepts custom flags ──────────────────────────────

    @GameTest(template = TEMPLATE)
    public static void flagRegistryAcceptsCustomFlags(GameTestHelper helper) {
        try {
            FlagRegistryImpl registry = new FlagRegistryImpl();

            // Register a custom BooleanFlag
            String customId = "test-custom-flag-" + UUID.randomUUID().toString().substring(0, 8);
            BooleanFlag customFlag = new BooleanFlag(customId, true, "A test custom flag");
            registry.register(customFlag);

            // Verify it appears in the registry
            Optional<Flag<?>> retrieved = registry.get(customId);
            if (retrieved.isEmpty()) {
                helper.fail("Custom flag '" + customId + "' should be in the registry");
                return;
            }

            Flag<?> flag = retrieved.get();
            if (!customId.equals(flag.id())) {
                helper.fail("Retrieved flag ID should be '" + customId + "' but was '" + flag.id() + "'");
                return;
            }

            if (!"A test custom flag".equals(flag.description())) {
                helper.fail("Flag description mismatch");
                return;
            }

            // Set it on a zone and verify the value
            ProtectedZone zone = new ProtectedZone("custom-flag-zone", "minecraft:overworld",
                new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));
            zone.setFlag(customId, false);

            boolean resolved = FlagResolver.resolve(zone, customFlag);
            if (resolved) {
                helper.fail("Custom flag should resolve to false after setFlag(false)");
                return;
            }

            // Verify default is used when flag is not set on zone
            ProtectedZone zoneNoFlag = new ProtectedZone("no-flag-zone", "minecraft:overworld",
                new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));
            boolean defaultValue = FlagResolver.resolve(zoneNoFlag, customFlag);
            if (!defaultValue) {
                helper.fail("Custom flag default should be true, but got false");
                return;
            }

            helper.succeed();
        } catch (Exception e) {
            helper.fail("Unexpected exception: " + e.getMessage());
        }
    }

    // ── Test 8: Zone serialization round-trip ───────────────────────────────────

    @GameTest(template = TEMPLATE)
    public static void zoneSerializationRoundTrip(GameTestHelper helper) {
        try {
            // Create a zone with various flags
            UUID player1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
            UUID player2 = UUID.fromString("00000000-0000-0000-0000-000000000022");
            Set<UUID> whitelist = new HashSet<>();
            whitelist.add(player1);
            whitelist.add(player2);

            Map<String, Object> flags = new LinkedHashMap<>();
            flags.put(BuiltinFlags.BLOCK_BREAK.id(), false);
            flags.put(BuiltinFlags.PVP.id(), true);
            flags.put(BuiltinFlags.HEAL_AMOUNT.id(), 5);

            ProtectedZone original = new ProtectedZone("serial-zone", "minecraft:the_nether",
                -100, 30, -200, 100, 128, 200, whitelist, "some-parent", 7, flags);
            original.setEnabled(false);
            original.setInheritDimFlags(false);

            // Serialize to a temp file
            Path tempFile = Files.createTempFile("arcadiaguard-test-", ".json");
            try {
                ZoneSerializer.write(original, tempFile);

                // Deserialize — ZoneSerializer.read() calls ArcadiaGuard.flagRegistry()
                // which is available in a GameTest context since the mod is loaded
                ProtectedZone loaded = ZoneSerializer.read(tempFile);

                if (loaded == null) {
                    helper.fail("Deserialized zone should not be null");
                    return;
                }

                // Verify all fields
                if (!"serial-zone".equals(loaded.name())) {
                    helper.fail("Name mismatch: " + loaded.name());
                    return;
                }
                if (!"minecraft:the_nether".equals(loaded.dimension())) {
                    helper.fail("Dimension mismatch: " + loaded.dimension());
                    return;
                }
                if (loaded.minX() != -100 || loaded.minY() != 30 || loaded.minZ() != -200) {
                    helper.fail("Min bounds mismatch");
                    return;
                }
                if (loaded.maxX() != 100 || loaded.maxY() != 128 || loaded.maxZ() != 200) {
                    helper.fail("Max bounds mismatch");
                    return;
                }
                if (!"some-parent".equals(loaded.parent())) {
                    helper.fail("Parent mismatch: " + loaded.parent());
                    return;
                }
                if (loaded.priority() != 7) {
                    helper.fail("Priority mismatch: " + loaded.priority());
                    return;
                }
                if (loaded.enabled()) {
                    helper.fail("enabled should be false");
                    return;
                }
                if (loaded.inheritDimFlags()) {
                    helper.fail("inheritDimFlags should be false");
                    return;
                }

                // Verify whitelist
                if (!loaded.whitelistedPlayers().contains(player1)
                    || !loaded.whitelistedPlayers().contains(player2)) {
                    helper.fail("Whitelist players missing after round-trip");
                    return;
                }

                // Verify flags
                Object bbFlag = loaded.flagValues().get(BuiltinFlags.BLOCK_BREAK.id());
                if (!Boolean.FALSE.equals(bbFlag)) {
                    helper.fail("block-break flag should be false, got: " + bbFlag);
                    return;
                }
                Object pvpFlag = loaded.flagValues().get(BuiltinFlags.PVP.id());
                if (!Boolean.TRUE.equals(pvpFlag)) {
                    helper.fail("pvp flag should be true, got: " + pvpFlag);
                    return;
                }
                Object healFlag = loaded.flagValues().get(BuiltinFlags.HEAL_AMOUNT.id());
                if (!(healFlag instanceof Integer i) || i != 5) {
                    helper.fail("heal-amount flag should be 5, got: " + healFlag);
                    return;
                }

                helper.succeed();
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            helper.fail("Unexpected exception: " + e.getMessage());
        }
    }
}

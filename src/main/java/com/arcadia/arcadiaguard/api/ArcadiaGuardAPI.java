package com.arcadia.arcadiaguard.api;

import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.flag.FlagRegistry;
import com.arcadia.arcadiaguard.api.zone.IZone;
import com.arcadia.arcadiaguard.api.zone.ZoneCheckResult;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.zone.ZoneManager;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Public entry point for interacting with ArcadiaGuard from third-party mods.
 *
 * <p>Obtain the singleton instance via {@link #get()} after {@code FMLCommonSetupEvent}.
 * All methods are safe to call from the server thread only.
 *
 * <p>Example registration:
 * <pre>{@code
 * // In your mod's FMLCommonSetupEvent listener:
 * ArcadiaGuardAPI api = ArcadiaGuardAPI.get();
 * api.registerFlag(new BooleanFlag("mymod:custom-flag", true, "Allows custom action."));
 * }</pre>
 */
public final class ArcadiaGuardAPI {

    private static ArcadiaGuardAPI instance;

    private FlagRegistry flagRegistry;
    private IGuardService guardService;
    private IZoneManager zoneManager;

    ArcadiaGuardAPI() {}

    /**
     * Returns the singleton API instance.
     *
     * @return the ArcadiaGuardAPI singleton
     * @throws IllegalStateException if called before {@code FMLCommonSetupEvent}
     */
    public static ArcadiaGuardAPI get() {
        if (instance == null) throw new IllegalStateException("ArcadiaGuardAPI not yet initialized");
        return instance;
    }

    /**
     * Internal — called by the mod entrypoint during initialization.
     * Not intended for use by third-party mods.
     *
     * @param flagRegistry  the global flag registry
     * @param guardService  the guard service handling protection checks
     * @param zoneManager   the zone manager holding all loaded zones
     */
    public static void setup(FlagRegistry flagRegistry, GuardService guardService, ZoneManager zoneManager) {
        ArcadiaGuardAPI api = new ArcadiaGuardAPI();
        api.flagRegistry = flagRegistry;
        api.guardService = guardService;
        api.zoneManager = zoneManager;
        instance = api;
    }

    /**
     * Registers a custom flag so it becomes available in {@link FlagRegistry} and can be set
     * on zones via commands or the GUI.
     *
     * <p>Must be called during {@code FMLCommonSetupEvent}.
     *
     * @param flag the flag to register; its {@link Flag#id()} must be unique
     * @throws IllegalArgumentException if a flag with the same id is already registered
     */
    public void registerFlag(Flag<?> flag) {
        this.flagRegistry.register(flag);
    }

    /**
     * Returns the global flag registry containing all built-in and third-party flags.
     *
     * @return the global {@link FlagRegistry}
     */
    public FlagRegistry flagRegistry() {
        return this.flagRegistry;
    }

    /**
     * Checks whether the given {@code flag} denies an action at {@code pos} for {@code player}.
     *
     * <p>This is a pure read-only check: no message is sent to the player and nothing is logged.
     * Bypass permissions, whitelist membership, and flag inheritance are all respected.
     *
     * @param player the player performing the action
     * @param pos    the block position of the action
     * @param flag   the boolean flag to evaluate
     * @return {@link ZoneCheckResult#allowed()} if the action is permitted,
     *         or a blocked result containing the zone name if it is denied
     */
    public ZoneCheckResult checkFlag(ServerPlayer player, BlockPos pos, Flag<Boolean> flag) {
        return guardService.checkFlag(player, pos, flag);
    }

    /**
     * Returns the named zone in the given level as a read-only {@link IZone} view.
     *
     * @param level the server level to search in
     * @param name  the zone name (case-insensitive)
     * @return an {@link Optional} containing the zone, or empty if no zone with that name exists
     */
    public Optional<IZone> getZone(Level level, String name) {
        return zoneManager.get(level, name);
    }

    /**
     * Internal — kept for backward compatibility during setup.
     * Not intended for use by third-party mods.
     *
     * @param flagRegistry the flag registry to assign
     */
    /**
     * @deprecated Configure flags via {@link #registerFlag(Flag)} instead.
     *             This method will be removed in a future version.
     */
    @Deprecated
    public void setFlagRegistry(FlagRegistry flagRegistry) {
        this.flagRegistry = flagRegistry;
    }
}

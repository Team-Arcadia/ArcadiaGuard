package com.arcadia.arcadiaguard.api;

import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.zone.IZone;
import com.arcadia.arcadiaguard.api.zone.ZoneCheckResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public read-only guard service interface exposed by ArcadiaGuard.
 *
 * <p>Obtain an instance via {@link ArcadiaGuardAPI#get()}.
 * All methods are safe to call from the server thread only.
 */
public interface IGuardService {

    /**
     * Returns {@code true} if the player may bypass zone protection.
     *
     * <p>Bypass is granted when the player has the configured OP level or a LuckPerms bypass node.
     * Debug mode disables bypass even for players who would otherwise qualify.
     *
     * @param player the player to check
     * @return {@code true} if the player should bypass protection
     */
    boolean shouldBypass(ServerPlayer player);

    /**
     * Checks whether {@code flag} denies an action at {@code pos} for {@code player}.
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
    ZoneCheckResult checkFlag(ServerPlayer player, BlockPos pos, Flag<Boolean> flag);

    /**
     * Returns {@code true} if the player is whitelisted or has a member role in the given zone.
     *
     * @param player the player to check
     * @param zone   the zone to check membership in
     * @return {@code true} if the player is a member of the zone
     */
    boolean isZoneMember(ServerPlayer player, IZone zone);
}

package com.arcadia.arcadiaguard.command;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsCompat;
import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsPermissionChecker;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.arcadia.arcadiaguard.zone.ZoneRole;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Utility for checking whether a command source is allowed to act on a zone.
 * Access is granted if the source is OP (configured bypass level) OR if the player
 * holds at least {@code minRole} in the target zone (internal roles or LuckPerms nodes).
 */
public final class ZonePermission {

    private ZonePermission() {}

    /**
     * Returns {@code true} if the source may perform an action requiring {@code minRole}
     * on the named zone.
     *
     * <p>Access is granted when any of the following is true:
     * <ol>
     *   <li>The source has OP permission &ge; {@code bypass_op_level}.</li>
     *   <li>LuckPerms is loaded and the player holds {@code arcadiaguard.*} or
     *       {@code arcadiaguard.zone.bypass}.</li>
     *   <li>LuckPerms is loaded and the player holds a zone-specific node that
     *       resolves to at least {@code minRole}.</li>
     *   <li>The player has at least {@code minRole} in the zone's internal member map.</li>
     * </ol>
     *
     * @param source   the command source
     * @param zoneName the zone name to check against
     * @param minRole  minimum required role
     * @return {@code true} if access is granted
     */
    public static boolean hasAccess(CommandSourceStack source, String zoneName, ZoneRole minRole) {
        if (source.hasPermission(ArcadiaGuardConfig.BYPASS_OP_LEVEL.get())) return true;
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;

        // LuckPerms integration — capture the checker once so another thread
        // unloading LuckPerms between the availability check and the call
        // cannot turn a valid ref into a NPE.
        LuckPermsPermissionChecker lp = LuckPermsCompat.isAvailable() ? LuckPermsCompat.checker() : null;
        if (lp != null) {
            if (lp.hasBypass(player)) return true;
            String lpRole = lp.resolveRole(player, zoneName);
            if (lpRole != null) {
                ZoneRole resolved = switch (lpRole) {
                    case "owner"     -> ZoneRole.OWNER;
                    case "moderator" -> ZoneRole.MODERATOR;
                    default          -> ZoneRole.MEMBER;
                };
                if (resolved.atLeast(minRole)) return true;
            }
        }

        // Internal role map
        @SuppressWarnings("unchecked")
        Optional<ProtectedZone> zone = (Optional<ProtectedZone>)(Optional<?>) ArcadiaGuard.zoneManager().get(source.getLevel(), zoneName);
        if (zone.isEmpty()) return false;
        Optional<ZoneRole> roleOpt = zone.get().roleOf(player.getUUID());
        return roleOpt.isPresent() && roleOpt.get().atLeast(minRole);
    }

    /**
     * Returns {@code true} if the source is OP or holds any role (MEMBER or above)
     * in at least one zone. Used to show /ag in tab-completion for zone owners.
     */
    public static boolean hasAnyRole(CommandSourceStack source) {
        if (source.hasPermission(ArcadiaGuardConfig.BYPASS_OP_LEVEL.get())) return true;
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        LuckPermsPermissionChecker lp = LuckPermsCompat.isAvailable() ? LuckPermsCompat.checker() : null;
        if (lp != null) {
            if (lp.hasBypass(player)) return true;
            if (lp.hasViewAccess(player)) return true;
        }
        return ArcadiaGuard.zoneManager().zones(source.getLevel())
            .stream().anyMatch(z -> ((ProtectedZone) z).roleOf(player.getUUID()).isPresent());
    }

    /**
     * Returns {@code true} si le joueur a {@code arcadiaguard.view} mais pas de bypass/édition.
     * Ces joueurs peuvent voir toutes les zones et les logs, mais pas modifier.
     */
    public static boolean isViewOnly(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        LuckPermsPermissionChecker lp = LuckPermsCompat.isAvailable() ? LuckPermsCompat.checker() : null;
        if (lp == null) return false;
        return lp.hasViewAccess(player) && !lp.hasBypass(player);
    }
}

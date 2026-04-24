package com.arcadia.arcadiaguard.command.sub;

import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.compat.yawp.YawpMigrator;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class MigrateCommands {

    private MigrateCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("migrate")
            .then(literal("yawp").executes(MigrateCommands::migrateYawp));
    }

    private static int migrateYawp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.migrate.yawp_start"), false);
        List<ProtectedZone> zones = YawpMigrator.migrate(ctx.getSource().getServer());
        if (zones.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.migrate.yawp_none"), false);
            return 1;
        }
        int imported = 0;
        int skippedDup = 0;
        int skippedDim = 0;
        for (ProtectedZone zone : zones) {
            boolean matched = false;
            for (var level : ctx.getSource().getServer().getAllLevels()) {
                String dim = level.dimension().location().toString();
                if (dim.equals(zone.dimension())) {
                    matched = true;
                    boolean added = ArcadiaGuard.zoneManager().add(level, zone);
                    if (added) imported++;
                    else {
                        skippedDup++;
                        ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Zone YAWP '{}' (dim={}) non importee : nom deja utilise en ArcadiaGuard.",
                            zone.name(), zone.dimension());
                    }
                    break;
                }
            }
            if (!matched) {
                skippedDim++;
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Zone YAWP '{}' : dimension '{}' non chargee sur le serveur.",
                    zone.name(), zone.dimension());
            }
        }
        // Refresh GUI pour tous les admins connectes (pour voir les nouvelles zones).
        // Utilise getProfilePermissions() pour eviter qu'un mod tiers n'intercepte
        // hasPermissions() et provoque un broadcast involontaire (meme convention
        // que GuardService.computeBypass).
        var server = ctx.getSource().getServer();
        int threshold = com.arcadia.arcadiaguard.config.ArcadiaGuardConfig.BYPASS_OP_LEVEL.get();
        for (var p : server.getPlayerList().getPlayers()) {
            if (server.getProfilePermissions(p.getGameProfile()) >= threshold) {
                ArcadiaGuard.zoneManager().sendRefreshedList(p);
            }
        }
        final int count = imported;
        final int total = zones.size();
        final int dup = skippedDup;
        final int dimSkip = skippedDim;
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.migrate.yawp_done",
            String.valueOf(count), String.valueOf(total)), true);
        if (dup > 0 || dimSkip > 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "§e" + dup + " doublon(s) ignoré(s), " + dimSkip + " dim manquante(s). Voir logs serveur."), false);
        }
        return 1;
    }
}

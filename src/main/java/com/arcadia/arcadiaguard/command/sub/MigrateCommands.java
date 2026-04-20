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
        for (ProtectedZone zone : zones) {
            for (var level : ctx.getSource().getServer().getAllLevels()) {
                String dim = level.dimension().location().toString();
                if (dim.equals(zone.dimension())) {
                    boolean added = ArcadiaGuard.zoneManager().add(level, zone);
                    if (added) imported++;
                    break;
                }
            }
        }
        final int count = imported;
        final int total = zones.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.migrate.yawp_done",
            String.valueOf(count), String.valueOf(total)), true);
        return 1;
    }
}

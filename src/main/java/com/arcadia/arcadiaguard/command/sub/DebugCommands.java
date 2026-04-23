package com.arcadia.arcadiaguard.command.sub;

import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DebugCommands {

    private DebugCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("debug")
            .then(literal("stats").executes(DebugCommands::stats))
            .then(literal("whereami").executes(DebugCommands::whereami));
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) {
        String writerStats = ArcadiaGuard.asyncZoneWriter().stats();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "=== ArcadiaGuard Debug Stats ===\n[AsyncZoneWriter] " + writerStats), false);
        return 1;
    }

    private static int whereami(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (Exception e) { src.sendFailure(Component.literal("Player only.")); return 0; }

        String dim = player.level().dimension().location().toString();
        BlockPos pos = player.blockPosition();
        boolean bypass = ArcadiaGuard.guardService().shouldBypass(player);

        var zoneOpt = ArcadiaGuard.guardService().zoneManager().findZoneContaining(
            player.serverLevel(), pos);

        src.sendSuccess(() -> Component.literal(
            "=== ArcadiaGuard Whereami ===").withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal(
            "Dimension : " + dim), false);
        src.sendSuccess(() -> Component.literal(
            "Position  : " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
        src.sendSuccess(() -> Component.literal(
            "Bypass    : " + bypass).withStyle(bypass ? ChatFormatting.RED : ChatFormatting.GREEN), false);

        int zoneCount = ArcadiaGuard.guardService().zoneManager().zones(player.serverLevel()).size();
        src.sendSuccess(() -> Component.literal(
            "Zones dim : " + zoneCount), false);

        if (zoneOpt.isPresent()) {
            ProtectedZone z = (ProtectedZone) zoneOpt.get();
            src.sendSuccess(() -> Component.literal(
                "Zone      : " + z.name()
                + " [" + z.minX() + "," + z.minY() + "," + z.minZ()
                + " → " + z.maxX() + "," + z.maxY() + "," + z.maxZ() + "]"
            ).withStyle(ChatFormatting.YELLOW), false);
            src.sendSuccess(() -> Component.literal(
                "Flags     : " + z.flagValues()), false);
        } else {
            src.sendSuccess(() -> Component.literal(
                "Zone      : (aucune zone à cette position)").withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }
}

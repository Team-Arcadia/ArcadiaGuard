package com.arcadia.arcadiaguard.command.sub;

import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class DebugCommands {

    private DebugCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("debug")
            .then(literal("stats").executes(DebugCommands::stats));
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) {
        String writerStats = ArcadiaGuard.asyncZoneWriter().stats();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "=== ArcadiaGuard Debug Stats ===\n[AsyncZoneWriter] " + writerStats), false);
        return 1;
    }
}

package com.arcadia.arcadiaguard.command.sub;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.persist.DimFlagSerializer;
import com.arcadia.arcadiaguard.util.DimensionUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class DimFlagCommands {

    private DimFlagCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("dimflag")
            .requires(src -> src.hasPermission(2))
            .then(literal("list")
                .executes(DimFlagCommands::listAll)
                .then(argument("dimension", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                        ArcadiaGuard.dimFlagStore().all().keySet(), b))
                    .executes(DimFlagCommands::listDim)))
            .then(literal("clear")
                .then(argument("dimension", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                        ArcadiaGuard.dimFlagStore().all().keySet(), b))
                    .executes(DimFlagCommands::clearDim))
                .then(literal("all").executes(DimFlagCommands::clearAll)));
    }

    private static int listAll(CommandContext<CommandSourceStack> ctx) {
        var all = ArcadiaGuard.dimFlagStore().all();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.dimflag.none_configured").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.dimflag.header").withStyle(ChatFormatting.GOLD), false);
        for (var dim : all.entrySet()) {
            sendDimFlags(ctx, dim.getKey(), dim.getValue());
        }
        return 1;
    }

    private static int listDim(CommandContext<CommandSourceStack> ctx) {
        String dim = StringArgumentType.getString(ctx, "dimension");
        var flags = ArcadiaGuard.dimFlagStore().flags(dim);
        if (flags.isEmpty()) {
            ctx.getSource().sendSuccess(() ->
                Component.translatable("arcadiaguard.dimflag.none_for_dim", dim).withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() ->
            Component.translatable("arcadiaguard.dimflag.flags_for_dim", dim).withStyle(ChatFormatting.GOLD), false);
        sendDimFlags(ctx, dim, flags);
        return 1;
    }

    private static void sendDimFlags(CommandContext<CommandSourceStack> ctx, String dim, Map<String, Object> flags) {
        ctx.getSource().sendSuccess(() -> Component.literal("  " + dim + ":").withStyle(ChatFormatting.AQUA), false);
        for (var e : flags.entrySet()) {
            String color = Boolean.FALSE.equals(e.getValue()) ? ChatFormatting.RED.toString() : ChatFormatting.GREEN.toString();
            ctx.getSource().sendSuccess(() ->
                Component.literal("    " + ChatFormatting.GRAY + e.getKey() + " = " + color + e.getValue()), false);
        }
    }

    private static int clearDim(CommandContext<CommandSourceStack> ctx) {
        String dim = StringArgumentType.getString(ctx, "dimension");
        var flags = ArcadiaGuard.dimFlagStore().flags(dim);
        if (flags.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.dimflag.none_to_clear", dim));
            return 0;
        }
        new java.util.ArrayList<>(flags.keySet())
            .forEach(k -> ArcadiaGuard.dimFlagStore().resetFlag(dim, k));
        saveDimFlags();
        ctx.getSource().sendSuccess(() ->
            Component.translatable("arcadiaguard.dimflag.cleared", dim).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx) {
        ArcadiaGuard.dimFlagStore().clear();
        saveDimFlags();
        ctx.getSource().sendSuccess(() ->
            Component.translatable("arcadiaguard.dimflag.cleared_all").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static void saveDimFlags() {
        try {
            DimFlagSerializer.write(ArcadiaGuard.dimFlagStore(),
                com.arcadia.arcadiaguard.ArcadiaGuardPaths.dimFlagsFile());
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Erreur sauvegarde dim flags", e);
        }
    }
}

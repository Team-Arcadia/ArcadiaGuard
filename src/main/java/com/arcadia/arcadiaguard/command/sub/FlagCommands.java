package com.arcadia.arcadiaguard.command.sub;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.command.ZonePermission;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.flag.IntFlag;
import com.arcadia.arcadiaguard.api.flag.ListFlag;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.arcadia.arcadiaguard.zone.ZoneRole;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class FlagCommands {

    private FlagCommands() {}

    /** Returns the "flag" subtree to nest under "zone". */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("flag")
            .then(argument("name", StringArgumentType.word())
                .then(argument("flag", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                        ArcadiaGuard.flagRegistry().all().stream().map(Flag::id).toList(), b))
                    .then(literal("allow").executes(ctx -> setBoolean(ctx, true)))
                    .then(literal("deny").executes(ctx -> setBoolean(ctx, false)))
                    .then(literal("reset").executes(FlagCommands::reset))
                    .then(literal("set")
                        .then(argument("value", IntegerArgumentType.integer())
                            .executes(FlagCommands::setInt)))
                    .then(literal("add")
                        .then(argument("entry", StringArgumentType.greedyString())
                            .executes(FlagCommands::listAdd)))
                    .then(literal("remove")
                        .then(argument("entry", StringArgumentType.greedyString())
                            .executes(FlagCommands::listRemove)))));
    }

    private static int setBoolean(CommandContext<CommandSourceStack> ctx, boolean allow) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ZonePermission.hasAccess(ctx.getSource(), name, ZoneRole.OWNER)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.perm.denied_owner", name));
            return 0;
        }
        String flagId = StringArgumentType.getString(ctx, "flag");
        Flag<?> flag = ArcadiaGuard.flagRegistry().get(flagId).orElse(null);
        if (flag == null) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.flag.unknown", flagId));
            return 0;
        }
        if (!(flag instanceof BooleanFlag)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.flag.not_boolean", flagId));
            return 0;
        }
        boolean set = ArcadiaGuard.zoneManager().setFlag(ctx.getSource().getLevel(), name, flagId, allow);
        if (!set) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.not_found", name));
            return 0;
        }
        String verb = allow ? "allow" : "deny";
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.flag.set", flagId, verb, name), true);
        return 1;
    }

    private static int setInt(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ZonePermission.hasAccess(ctx.getSource(), name, ZoneRole.OWNER)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.perm.denied_owner", name));
            return 0;
        }
        String flagId = StringArgumentType.getString(ctx, "flag");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        Flag<?> flag = ArcadiaGuard.flagRegistry().get(flagId).orElse(null);
        if (flag == null) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.flag.unknown", flagId));
            return 0;
        }
        if (!(flag instanceof IntFlag)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.flag.not_integer", flagId));
            return 0;
        }
        boolean set = ArcadiaGuard.zoneManager().setFlag(ctx.getSource().getLevel(), name, flagId, value);
        if (!set) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.not_found", name));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.flag.set", flagId, String.valueOf(value), name), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ZonePermission.hasAccess(ctx.getSource(), name, ZoneRole.OWNER)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.perm.denied_owner", name));
            return 0;
        }
        String flagId = StringArgumentType.getString(ctx, "flag");
        boolean ok = ArcadiaGuard.zoneManager().resetFlag(ctx.getSource().getLevel(), name, flagId);
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.not_found", name));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.flag.reset", flagId, name), true);
        return 1;
    }

    @SuppressWarnings("unchecked")
    private static int listAdd(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ZonePermission.hasAccess(ctx.getSource(), name, ZoneRole.OWNER)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.perm.denied_owner", name));
            return 0;
        }
        String flagId = StringArgumentType.getString(ctx, "flag");
        String entry = StringArgumentType.getString(ctx, "entry");
        Flag<?> flag = ArcadiaGuard.flagRegistry().get(flagId).orElse(null);
        if (!(flag instanceof ListFlag)) {
            ctx.getSource().sendFailure(flag == null
                ? Component.translatable("arcadiaguard.command.flag.unknown", flagId)
                : Component.translatable("arcadiaguard.command.flag.not_list", flagId));
            return 0;
        }
        var zoneOpt = ArcadiaGuard.zoneManager().get(ctx.getSource().getLevel(), name);
        if (zoneOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.not_found", name));
            return 0;
        }
        List<String> current = new java.util.ArrayList<>((List<String>) ((ProtectedZone) zoneOpt.get()).flagValues().getOrDefault(flagId, List.of()));
        if (!current.contains(entry)) current.add(entry);
        ArcadiaGuard.zoneManager().setFlag(ctx.getSource().getLevel(), name, flagId, current);
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.flag.list_add", entry, flagId, name), true);
        return 1;
    }

    @SuppressWarnings("unchecked")
    private static int listRemove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ZonePermission.hasAccess(ctx.getSource(), name, ZoneRole.OWNER)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.perm.denied_owner", name));
            return 0;
        }
        String flagId = StringArgumentType.getString(ctx, "flag");
        String entry = StringArgumentType.getString(ctx, "entry");
        Flag<?> flag = ArcadiaGuard.flagRegistry().get(flagId).orElse(null);
        if (!(flag instanceof ListFlag)) {
            ctx.getSource().sendFailure(flag == null
                ? Component.translatable("arcadiaguard.command.flag.unknown", flagId)
                : Component.translatable("arcadiaguard.command.flag.not_list", flagId));
            return 0;
        }
        var zoneOpt = ArcadiaGuard.zoneManager().get(ctx.getSource().getLevel(), name);
        if (zoneOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.not_found", name));
            return 0;
        }
        List<String> current = new java.util.ArrayList<>((List<String>) ((ProtectedZone) zoneOpt.get()).flagValues().getOrDefault(flagId, List.of()));
        current.remove(entry);
        ArcadiaGuard.zoneManager().setFlag(ctx.getSource().getLevel(), name, flagId, current);
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.flag.list_remove", entry, flagId, name), true);
        return 1;
    }
}

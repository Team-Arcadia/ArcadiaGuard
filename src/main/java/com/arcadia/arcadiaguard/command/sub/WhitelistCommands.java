package com.arcadia.arcadiaguard.command.sub;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.command.ZonePermission;
import com.arcadia.arcadiaguard.zone.ZoneRole;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class WhitelistCommands {

    private static final List<String> ROLES = List.of("MEMBER", "MODERATOR", "OWNER");

    private WhitelistCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("whitelist")
            .then(literal("add")
                .then(argument("name", StringArgumentType.word())
                    .then(argument("player", EntityArgument.player())
                        .executes(WhitelistCommands::add))))
            .then(literal("remove")
                .then(argument("name", StringArgumentType.word())
                    .then(argument("player", EntityArgument.player())
                        .executes(WhitelistCommands::remove))))
            .then(literal("role")
                .then(argument("name", StringArgumentType.word())
                    .then(argument("player", EntityArgument.player())
                        .then(argument("role", StringArgumentType.word())
                            .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ROLES, b))
                            .executes(WhitelistCommands::setRole)))));
    }

    private static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return change(ctx, true);
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return change(ctx, false);
    }

    private static int change(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ZonePermission.hasAccess(ctx.getSource(), name, ZoneRole.MODERATOR)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.perm.denied_moderator", name));
            return 0;
        }
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        UUID id = player.getGameProfile().getId();
        String playerName = player.getGameProfile().getName();
        boolean changed = add
            ? ArcadiaGuard.zoneManager().whitelistAdd(ctx.getSource().getLevel(), name, id, playerName)
            : ArcadiaGuard.zoneManager().whitelistRemove(ctx.getSource().getLevel(), name, id, playerName);
        String key = add ? "arcadiaguard.command.whitelist.added" : "arcadiaguard.command.whitelist.removed";
        ctx.getSource().sendSuccess(() -> Component.translatable(key, playerName, name), true);
        return changed ? 1 : 0;
    }

    private static int setRole(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ZonePermission.hasAccess(ctx.getSource(), name, ZoneRole.OWNER)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.perm.denied_owner", name));
            return 0;
        }
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String roleStr = StringArgumentType.getString(ctx, "role").toUpperCase();

        ZoneRole role;
        try {
            role = ZoneRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.whitelist.invalid_role", roleStr));
            return 0;
        }

        var zoneOpt = ArcadiaGuard.zoneManager().get(ctx.getSource().getLevel(), name);
        if (zoneOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.not_found", name));
            return 0;
        }

        UUID playerId = player.getGameProfile().getId();
        String playerName = player.getGameProfile().getName();
        ArcadiaGuard.zoneManager().setMemberRole(ctx.getSource().getLevel(), name, playerId, role);
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.whitelist.role_set", role.name(), playerName, name), true);
        return 1;
    }
}

package com.arcadia.arcadiaguard.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import com.arcadia.arcadiaguard.zone.ExceptionZone;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ArcadiaGuardCommands {

    private static final List<String> FEATURES = List.of("ironsspellbooks", "arsnouveau", "simplyswords", "occultism", "supplementaries", "apotheosis", "betterarcheology");

    private ArcadiaGuardCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("arcadiaguard")
            .requires(src -> src.hasPermission(2))
            .then(literal("reload").executes(ArcadiaGuardCommands::reload))
            .then(literal("item")
                .then(literal("block")
                    .then(argument("item", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder))
                        .executes(ArcadiaGuardCommands::itemBlock)))
                .then(literal("unblock")
                    .then(argument("item", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(ArcadiaGuard.dynamicItemBlockList().list(), builder))
                        .executes(ArcadiaGuardCommands::itemUnblock)))
                .then(literal("list").executes(ArcadiaGuardCommands::itemList)))
            .then(literal("zone")
                .then(literal("add")
                    .then(argument("name", StringArgumentType.word())
                        .then(argument("x1", IntegerArgumentType.integer())
                            .then(argument("y1", IntegerArgumentType.integer())
                                .then(argument("z1", IntegerArgumentType.integer())
                                    .then(argument("x2", IntegerArgumentType.integer())
                                        .then(argument("y2", IntegerArgumentType.integer())
                                            .then(argument("z2", IntegerArgumentType.integer()).executes(ArcadiaGuardCommands::addZone)))))))))
                .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(ArcadiaGuardCommands::removeZone)))
                .then(literal("list").executes(ArcadiaGuardCommands::listZones))
                .then(literal("info").then(argument("name", StringArgumentType.word()).executes(ArcadiaGuardCommands::infoZone)))
                .then(literal("whitelist")
                    .then(literal("add")
                        .then(argument("name", StringArgumentType.word())
                            .then(argument("player", EntityArgument.player()).executes(ArcadiaGuardCommands::whitelistAdd))))
                    .then(literal("remove")
                        .then(argument("name", StringArgumentType.word())
                            .then(argument("player", EntityArgument.player()).executes(ArcadiaGuardCommands::whitelistRemove))))))
            .then(literal("exception")
                .then(literal("add")
                    .then(argument("name", StringArgumentType.word())
                        .then(argument("x1", IntegerArgumentType.integer())
                            .then(argument("y1", IntegerArgumentType.integer())
                                .then(argument("z1", IntegerArgumentType.integer())
                                    .then(argument("x2", IntegerArgumentType.integer())
                                        .then(argument("y2", IntegerArgumentType.integer())
                                            .then(argument("z2", IntegerArgumentType.integer()).executes(ArcadiaGuardCommands::addExceptionZone)))))))))
                .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(ArcadiaGuardCommands::removeExceptionZone)))
                .then(literal("list").executes(ArcadiaGuardCommands::listExceptionZones))
                .then(literal("info").then(argument("name", StringArgumentType.word()).executes(ArcadiaGuardCommands::infoExceptionZone)))
                .then(literal("allow")
                    .then(argument("name", StringArgumentType.word())
                        .then(argument("feature", StringArgumentType.word()).suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(FEATURES, builder)).executes(ArcadiaGuardCommands::allowExceptionFeature))))
                .then(literal("deny")
                    .then(argument("name", StringArgumentType.word())
                        .then(argument("feature", StringArgumentType.word()).suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(FEATURES, builder)).executes(ArcadiaGuardCommands::denyExceptionFeature))))));
    }

    private static int itemBlock(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "item");
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            ctx.getSource().sendFailure(Component.literal("Item inconnu: " + id));
            return 0;
        }
        boolean added = ArcadiaGuard.dynamicItemBlockList().add(id);
        ctx.getSource().sendSuccess(() -> Component.literal(
            added ? "Item bloque en zone protegee: " + id : "Deja bloque: " + id), true);
        return added ? 1 : 0;
    }

    private static int itemUnblock(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "item");
        boolean removed = ArcadiaGuard.dynamicItemBlockList().remove(id);
        ctx.getSource().sendSuccess(() -> Component.literal(
            removed ? "Item debloque: " + id : "Non trouve dans la liste: " + id), true);
        return removed ? 1 : 0;
    }

    private static int itemList(CommandContext<CommandSourceStack> ctx) {
        List<ResourceLocation> items = ArcadiaGuard.dynamicItemBlockList().list();
        if (items.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Aucun item bloque dynamiquement."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("Items bloques (" + items.size() + "):"), false);
            for (ResourceLocation id : items) {
                ctx.getSource().sendSuccess(() -> Component.literal("  - " + id), false);
            }
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ArcadiaGuard.zoneManager().reload(ctx.getSource().getServer());
        ArcadiaGuard.dynamicItemBlockList().load();
        ctx.getSource().sendSuccess(() -> Component.literal("ArcadiaGuard recharge."), true);
        return 1;
    }

    private static int addZone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        BlockPos a = new BlockPos(IntegerArgumentType.getInteger(ctx, "x1"), IntegerArgumentType.getInteger(ctx, "y1"), IntegerArgumentType.getInteger(ctx, "z1"));
        BlockPos b = new BlockPos(IntegerArgumentType.getInteger(ctx, "x2"), IntegerArgumentType.getInteger(ctx, "y2"), IntegerArgumentType.getInteger(ctx, "z2"));
        ProtectedZone zone = new ProtectedZone(name, dimensionKey(ctx.getSource().getLevel()), a, b);
        boolean added = ArcadiaGuard.zoneManager().add(ctx.getSource().getLevel(), zone);
        ctx.getSource().sendSuccess(() -> Component.literal(added ? "Zone ajoutee: " + name : "Zone deja existante: " + name), true);
        return added ? 1 : 0;
    }

    private static int removeZone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        boolean removed = ArcadiaGuard.zoneManager().remove(ctx.getSource().getLevel(), name);
        ctx.getSource().sendSuccess(() -> Component.literal(removed ? "Zone supprimee: " + name : "Zone introuvable: " + name), true);
        return removed ? 1 : 0;
    }

    private static int listZones(CommandContext<CommandSourceStack> ctx) {
        for (ProtectedZone zone : ArcadiaGuard.zoneManager().zones(ctx.getSource().getLevel())) {
            ctx.getSource().sendSuccess(() -> Component.literal(zone.name() + " [" + zone.minX() + " " + zone.minY() + " " + zone.minZ() + "] -> [" + zone.maxX() + " " + zone.maxY() + " " + zone.maxZ() + "] (" + zone.dimension() + ")"), false);
        }
        return 1;
    }

    private static int infoZone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        var zone = ArcadiaGuard.zoneManager().get(ctx.getSource().getLevel(), name);
        if (zone.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Zone introuvable: " + name));
            return 0;
        }
        ProtectedZone found = zone.get();
        ctx.getSource().sendSuccess(() -> Component.literal(found.name() + " dim=" + found.dimension() + " min=(" + found.minX() + "," + found.minY() + "," + found.minZ() + ") max=(" + found.maxX() + "," + found.maxY() + "," + found.maxZ() + ") whitelist=" + found.whitelistedPlayers().size()), false);
        return 1;
    }

    private static int whitelistAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return whitelist(ctx, true);
    }

    private static int whitelistRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return whitelist(ctx, false);
    }

    private static int whitelist(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        UUID id = playerId(player);
        boolean changed = add
            ? ArcadiaGuard.zoneManager().whitelistAdd(ctx.getSource().getLevel(), name, id, playerName(player))
            : ArcadiaGuard.zoneManager().whitelistRemove(ctx.getSource().getLevel(), name, id, playerName(player));
        ctx.getSource().sendSuccess(() -> Component.literal((add ? "Whitelist ajoutee: " : "Whitelist retiree: ") + playerName(player) + " zone=" + name), true);
        return changed ? 1 : 0;
    }

    private static int addExceptionZone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        BlockPos a = new BlockPos(IntegerArgumentType.getInteger(ctx, "x1"), IntegerArgumentType.getInteger(ctx, "y1"), IntegerArgumentType.getInteger(ctx, "z1"));
        BlockPos b = new BlockPos(IntegerArgumentType.getInteger(ctx, "x2"), IntegerArgumentType.getInteger(ctx, "y2"), IntegerArgumentType.getInteger(ctx, "z2"));
        ExceptionZone zone = new ExceptionZone(name, dimensionKey(ctx.getSource().getLevel()), a, b);
        boolean added = ArcadiaGuard.zoneManager().addException(ctx.getSource().getLevel(), zone);
        ctx.getSource().sendSuccess(() -> Component.literal(added ? "Zone d'exception ajoutee: " + name : "Zone d'exception deja existante: " + name), true);
        return added ? 1 : 0;
    }

    private static int removeExceptionZone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        boolean removed = ArcadiaGuard.zoneManager().removeException(ctx.getSource().getLevel(), name);
        ctx.getSource().sendSuccess(() -> Component.literal(removed ? "Zone d'exception supprimee: " + name : "Zone d'exception introuvable: " + name), true);
        return removed ? 1 : 0;
    }

    private static int listExceptionZones(CommandContext<CommandSourceStack> ctx) {
        for (ExceptionZone zone : ArcadiaGuard.zoneManager().exceptionZones(ctx.getSource().getLevel())) {
            ctx.getSource().sendSuccess(() -> Component.literal(zone.name() + " [" + zone.minX() + " " + zone.minY() + " " + zone.minZ() + "] -> [" + zone.maxX() + " " + zone.maxY() + " " + zone.maxZ() + "] allow=" + String.join(",", zone.allowedFeatures())), false);
        }
        return 1;
    }

    private static int infoExceptionZone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        var zone = ArcadiaGuard.zoneManager().getException(ctx.getSource().getLevel(), name);
        if (zone.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Zone d'exception introuvable: " + name));
            return 0;
        }
        ExceptionZone found = zone.get();
        ctx.getSource().sendSuccess(() -> Component.literal(found.name() + " dim=" + found.dimension() + " min=(" + found.minX() + "," + found.minY() + "," + found.minZ() + ") max=(" + found.maxX() + "," + found.maxY() + "," + found.maxZ() + ") allow=" + String.join(",", found.allowedFeatures())), false);
        return 1;
    }

    private static int allowExceptionFeature(CommandContext<CommandSourceStack> ctx) {
        return changeExceptionFeature(ctx, true);
    }

    private static int denyExceptionFeature(CommandContext<CommandSourceStack> ctx) {
        return changeExceptionFeature(ctx, false);
    }

    private static int changeExceptionFeature(CommandContext<CommandSourceStack> ctx, boolean allow) {
        String name = StringArgumentType.getString(ctx, "name");
        String feature = StringArgumentType.getString(ctx, "feature").toLowerCase();
        if (!FEATURES.contains(feature)) {
            ctx.getSource().sendFailure(Component.literal("Feature invalide: " + feature));
            return 0;
        }
        boolean changed = allow
            ? ArcadiaGuard.zoneManager().allowExceptionFeature(ctx.getSource().getLevel(), name, feature)
            : ArcadiaGuard.zoneManager().denyExceptionFeature(ctx.getSource().getLevel(), name, feature);
        ctx.getSource().sendSuccess(() -> Component.literal((allow ? "Feature autorisee: " : "Feature retiree: ") + feature + " zone=" + name), true);
        return changed ? 1 : 0;
    }

    private static String dimensionKey(Object level) {
        Object dimension = ReflectionHelper.invoke(level, "dimension", new Class<?>[0]).orElse(null);
        Object location = dimension == null ? null : ReflectionHelper.invoke(dimension, "location", new Class<?>[0]).orElse(null);
        return location == null ? "unknown" : location.toString();
    }

    private static UUID playerId(ServerPlayer player) {
        Object profile = ReflectionHelper.invoke(player, "getGameProfile", new Class<?>[0]).orElse(null);
        Object id = profile == null ? null : ReflectionHelper.invoke(profile, "getId", new Class<?>[0]).orElse(null);
        return id instanceof UUID uuid ? uuid : new UUID(0L, 0L);
    }

    private static String playerName(ServerPlayer player) {
        Object profile = ReflectionHelper.invoke(player, "getGameProfile", new Class<?>[0]).orElse(null);
        Object name = profile == null ? null : ReflectionHelper.invoke(profile, "getName", new Class<?>[0]).orElse(null);
        return name == null ? "unknown" : String.valueOf(name);
    }

}

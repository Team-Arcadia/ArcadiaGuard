package com.arcadia.arcadiaguard.command.sub;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.item.WandItem;
import com.arcadia.arcadiaguard.util.DimensionUtils;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ZoneCommands {

    private ZoneCommands() {}

    /** H10: local name validation — mirrors ArcadiaGuardPaths.isValidZoneName without touching that file. */
    private static final java.util.regex.Pattern VALID_NAME =
        java.util.regex.Pattern.compile("[a-z0-9_\\-]{1,64}");

    private static final int COORD_MIN = -30_000_000;
    private static final int COORD_MAX =  30_000_000;

    private static boolean coordsInBounds(int... coords) {
        for (int c : coords) { if (c < COORD_MIN || c > COORD_MAX) return false; }
        return true;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("zone")
            .then(literal("create")
                .executes(ZoneCommands::createFromWand)
                .then(argument("name", StringArgumentType.word())
                    .executes(ZoneCommands::createFromWand)))
            .then(literal("add")
                .then(argument("name", StringArgumentType.word())
                    .then(argument("x1", IntegerArgumentType.integer())
                        .then(argument("y1", IntegerArgumentType.integer())
                            .then(argument("z1", IntegerArgumentType.integer())
                                .then(argument("x2", IntegerArgumentType.integer())
                                    .then(argument("y2", IntegerArgumentType.integer())
                                        .then(argument("z2", IntegerArgumentType.integer())
                                            .executes(ZoneCommands::add)))))))))
            .then(literal("remove")
                .then(argument("name", StringArgumentType.word())
                    .executes(ZoneCommands::remove)))
            .then(literal("list").executes(ZoneCommands::list))
            .then(literal("info")
                .then(argument("name", StringArgumentType.word())
                    .executes(ZoneCommands::info)))
            .then(literal("dimensional")
                .then(argument("name", StringArgumentType.word())
                    .executes(ZoneCommands::dimensional)))
            .then(literal("copy")
                .then(argument("name", StringArgumentType.word())
                    .then(argument("newname", StringArgumentType.word())
                        .executes(ZoneCommands::copy))))
            .then(literal("parent")
                .then(literal("set")
                    .then(argument("name", StringArgumentType.word())
                        .then(argument("parent", StringArgumentType.word())
                            .executes(ZoneCommands::parentSet))))
                .then(literal("clear")
                    .then(argument("name", StringArgumentType.word())
                        .executes(ZoneCommands::parentClear))))
            .then(WhitelistCommands.build())
            .then(FlagCommands.build());
    }

    private static int createFromWand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name;
        try { name = StringArgumentType.getString(ctx, "name").toLowerCase(java.util.Locale.ROOT); }
        catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.usage_create"));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = WandItem.getPos1(player.getUUID());
        BlockPos p2 = WandItem.getPos2(player.getUUID());
        if (p1 == null || p2 == null) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.selection_incomplete"));
            return 0;
        }
        if (!coordsInBounds(p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ())) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.coords_out_of_bounds"));
            return 0;
        }
        if (!VALID_NAME.matcher(name).matches()) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.invalid_name", name));
            return 0;
        }
        ProtectedZone zone = new ProtectedZone(name, DimensionUtils.keyOf(ctx.getSource().getLevel()), p1, p2);
        boolean added = ArcadiaGuard.zoneManager().add(ctx.getSource().getLevel(), zone);
        if (added) {
            WandItem.clearSelection(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.created", name), true);
        } else {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.already_exists", name));
        }
        return added ? 1 : 0;
    }

    private static int add(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name").toLowerCase(java.util.Locale.ROOT);
        if (!VALID_NAME.matcher(name).matches()) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.invalid_name", name));
            return 0;
        }
        int x1 = IntegerArgumentType.getInteger(ctx, "x1");
        int y1 = IntegerArgumentType.getInteger(ctx, "y1");
        int z1 = IntegerArgumentType.getInteger(ctx, "z1");
        int x2 = IntegerArgumentType.getInteger(ctx, "x2");
        int y2 = IntegerArgumentType.getInteger(ctx, "y2");
        int z2 = IntegerArgumentType.getInteger(ctx, "z2");
        if (!coordsInBounds(x1, y1, z1, x2, y2, z2)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.coords_out_of_bounds"));
            return 0;
        }
        BlockPos a = new BlockPos(x1, y1, z1);
        BlockPos b = new BlockPos(x2, y2, z2);
        ProtectedZone zone = new ProtectedZone(name, DimensionUtils.keyOf(ctx.getSource().getLevel()), a, b);
        boolean added = ArcadiaGuard.zoneManager().add(ctx.getSource().getLevel(), zone);
        if (added) {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.created", name), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.already_exists", name), true);
        }
        return added ? 1 : 0;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name").toLowerCase(java.util.Locale.ROOT);
        boolean removed = ArcadiaGuard.zoneManager().remove(ctx.getSource().getLevel(), name);
        if (removed) {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.removed", name), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.not_found", name), true);
        }
        return removed ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var zones = ArcadiaGuard.zoneManager().zones(ctx.getSource().getLevel());
        if (zones.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.none_in_dim"), false);
        } else {
            for (var zone : zones) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    zone.name() + " [" + zone.minX() + " " + zone.minY() + " " + zone.minZ()
                    + "] → [" + zone.maxX() + " " + zone.maxY() + " " + zone.maxZ() + "]"), false);
            }
        }
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name").toLowerCase(java.util.Locale.ROOT);
        var zone = ArcadiaGuard.zoneManager().get(ctx.getSource().getLevel(), name);
        if (zone.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.not_found", name));
            return 0;
        }
        ProtectedZone z = (ProtectedZone) zone.get();
        ctx.getSource().sendSuccess(() -> Component.literal(
            z.name() + " | dim=" + z.dimension()
            + " | min=(" + z.minX() + "," + z.minY() + "," + z.minZ()
            + ") | max=(" + z.maxX() + "," + z.maxY() + "," + z.maxZ()
            + ") | whitelist=" + z.whitelistedPlayers().size()
            + (z.parent() != null ? " | parent=" + z.parent() : "")), false);
        return 1;
    }

    private static int dimensional(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name").toLowerCase(java.util.Locale.ROOT);
        if (!VALID_NAME.matcher(name).matches()) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.invalid_name", name));
            return 0;
        }
        ProtectedZone zone = new ProtectedZone(name, DimensionUtils.keyOf(ctx.getSource().getLevel()),
            Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            new java.util.HashSet<>(), null, -1);
        boolean added = ArcadiaGuard.zoneManager().add(ctx.getSource().getLevel(), zone);
        if (added) {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.dimensional_created", name), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.already_exists", name), true);
        }
        return added ? 1 : 0;
    }

    private static int copy(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name").toLowerCase(java.util.Locale.ROOT);
        String newName = StringArgumentType.getString(ctx, "newname").toLowerCase(java.util.Locale.ROOT);
        if (!VALID_NAME.matcher(newName).matches()) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.invalid_name", newName));
            return 0;
        }
        var source = ArcadiaGuard.zoneManager().get(ctx.getSource().getLevel(), name);
        if (source.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.not_found", name));
            return 0;
        }
        ProtectedZone orig = (ProtectedZone) source.get();
        ProtectedZone copy = new ProtectedZone(newName, orig.dimension(),
            orig.minX(), orig.minY(), orig.minZ(),
            orig.maxX(), orig.maxY(), orig.maxZ(),
            new java.util.HashSet<>(orig.whitelistedPlayers()),
            orig.parent(), orig.priority(),
            new java.util.LinkedHashMap<>(orig.flagValues()));
        boolean added = ArcadiaGuard.zoneManager().add(ctx.getSource().getLevel(), copy);
        if (added) {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.copied", name, newName), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.already_exists", newName), true);
        }
        return added ? 1 : 0;
    }

    private static int parentSet(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name").toLowerCase(java.util.Locale.ROOT);
        String parent = StringArgumentType.getString(ctx, "parent").toLowerCase(java.util.Locale.ROOT);
        if (name.equalsIgnoreCase(parent)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.parent_self"));
            return 0;
        }
        if (ArcadiaGuard.zoneManager().get(ctx.getSource().getLevel(), parent).isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.parent_not_found", parent));
            return 0;
        }
        boolean ok = ArcadiaGuard.zoneManager().setParent(ctx.getSource().getLevel(), name, parent);
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.not_found", name));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.parent_set", name, parent), true);
        return 1;
    }

    private static int parentClear(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name").toLowerCase(java.util.Locale.ROOT);
        boolean ok = ArcadiaGuard.zoneManager().setParent(ctx.getSource().getLevel(), name, null);
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.zone.not_found", name));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.zone.parent_cleared", name), true);
        return 1;
    }
}

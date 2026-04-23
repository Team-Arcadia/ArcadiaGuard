package com.arcadia.arcadiaguard.command;

import static net.minecraft.commands.Commands.literal;

import net.minecraft.ChatFormatting;
import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.command.sub.DebugCommands;
import com.arcadia.arcadiaguard.command.sub.DimFlagCommands;
import com.arcadia.arcadiaguard.command.sub.ItemCommands;
import com.arcadia.arcadiaguard.command.sub.LogCommands;
import com.arcadia.arcadiaguard.command.sub.MigrateCommands;
import com.arcadia.arcadiaguard.command.sub.ZoneCommands;
import com.arcadia.arcadiaguard.handler.GuiActionHandler;
import com.arcadia.arcadiaguard.item.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ArcadiaGuardCommands {

    private ArcadiaGuardCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = literal("arcadiaguard")
            .requires(src -> ZonePermission.hasAnyRole(src))
            .executes(ArcadiaGuardCommands::showHelp)
            .then(literal("reload").executes(ArcadiaGuardCommands::reload))
            .then(literal("gui").executes(ArcadiaGuardCommands::openGui))
            .then(literal("help").executes(ArcadiaGuardCommands::showHelp))
            .then(literal("wand")
                .then(literal("give").executes(ctx -> giveWand(ctx))))
            .then(ZoneCommands.build())
            .then(ItemCommands.build())
            .then(LogCommands.build())
            .then(MigrateCommands.build())
            .then(DebugCommands.build())
            .then(DimFlagCommands.build())
            .then(com.arcadia.arcadiaguard.selftest.SelfTestCommand.build())
            .then(com.arcadia.arcadiaguard.selftest.TestSetupCommand.build());

        dispatcher.register(root);
        dispatcher.register(literal("ag")
            .requires(src -> ZonePermission.hasAnyRole(src))
            .redirect(dispatcher.getRoot().getChild("arcadiaguard")));
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "arcadiaguard.help.header", ArcadiaGuard.MOD_ID).withStyle(ChatFormatting.GOLD), false);

        // Bouton GUI proeminent
        MutableComponent guiBtn = Component.translatable("arcadiaguard.help.open_gui_button")
            .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ag gui"))
                             .withUnderlined(true)
                             .withColor(ChatFormatting.GREEN));
        ctx.getSource().sendSuccess(() -> Component.literal("  ").append(guiBtn), false);

        sendSection(ctx, "arcadiaguard.help.section.zones");
        sendCmdI18n(ctx, "/ag zone list",                              "arcadiaguard.help.zone_list");
        sendCmdI18n(ctx, "/ag zone info <nom>",                        "arcadiaguard.help.zone_info");
        sendCmdI18n(ctx, "/ag zone create <nom>",                      "arcadiaguard.help.zone_create");
        sendCmdI18n(ctx, "/ag zone remove <nom>",                      "arcadiaguard.help.zone_remove");
        sendCmdI18n(ctx, "/ag zone copy <nom> <nouveau>",              "arcadiaguard.help.zone_copy");
        sendCmdI18n(ctx, "/ag zone flag <nom> <flag> allow|deny|reset","arcadiaguard.help.zone_flag");
        sendCmdI18n(ctx, "/ag zone whitelist add <zone> <joueur>",     "arcadiaguard.help.zone_whitelist_add");
        sendCmdI18n(ctx, "/ag zone whitelist role <zone> <joueur> <rôle>", "arcadiaguard.help.zone_whitelist_role");
        sendCmdI18n(ctx, "/ag zone parent set <sous-zone> <parent>",   "arcadiaguard.help.zone_parent_set");

        sendSection(ctx, "arcadiaguard.help.section.items");
        sendCmdI18n(ctx, "/ag item block <item>",   "arcadiaguard.help.item_block");
        sendCmdI18n(ctx, "/ag item unblock <item>", "arcadiaguard.help.item_unblock");
        sendCmdI18n(ctx, "/ag item list",           "arcadiaguard.help.item_list");

        sendSection(ctx, "arcadiaguard.help.section.logs");
        sendCmdI18n(ctx, "/ag log",                 "arcadiaguard.help.log");
        sendCmdI18n(ctx, "/ag log <zone>",          "arcadiaguard.help.log_zone");
        sendCmdI18n(ctx, "/ag log <zone> <joueur>", "arcadiaguard.help.log_zone_player");

        sendSection(ctx, "arcadiaguard.help.section.dimflags");
        sendCmdI18n(ctx, "/ag dimflag list [dimension]",     "arcadiaguard.help.dimflag_list");
        sendCmdI18n(ctx, "/ag dimflag clear <dimension>",    "arcadiaguard.help.dimflag_clear");
        sendCmdI18n(ctx, "/ag dimflag clear all",            "arcadiaguard.help.dimflag_clear_all");

        sendSection(ctx, "arcadiaguard.help.section.admin");
        sendCmdI18n(ctx, "/ag wand give editor|viewer", "arcadiaguard.help.wand_give");
        sendCmdI18n(ctx, "/ag migrate yawp",            "arcadiaguard.help.migrate_yawp");
        sendCmdI18n(ctx, "/ag reload",                  "arcadiaguard.help.reload");

        return 1;
    }

    private static void sendSection(CommandContext<CommandSourceStack> ctx, String key) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(" \u25b6 ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable(key).withStyle(ChatFormatting.GOLD)), false);
    }

    private static void sendCmdI18n(CommandContext<CommandSourceStack> ctx, String cmd, String descKey) {
        MutableComponent text = Component.literal(cmd).withStyle(s -> s
            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
            .withColor(ChatFormatting.YELLOW));
        MutableComponent line = Component.literal(" \u2192 ").withStyle(ChatFormatting.GRAY)
            .append(text)
            .append(Component.literal(" \u2014 ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
        ctx.getSource().sendSuccess(() -> line, false);
    }

    private static int openGui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!ZonePermission.hasAnyRole(ctx.getSource())) {
            ctx.getSource().sendFailure(Component.translatable("commands.help.failed"));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GuiActionHandler.sendRefreshedList(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.gui_opened"), false);
        return 1;
    }

    private static int giveWand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        player.getInventory().add(ModItems.ZONE_EDITOR.get().getDefaultInstance());
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.wand_given"), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ArcadiaGuard.zoneManager().reload(ctx.getSource().getServer());
        ArcadiaGuard.dynamicItemBlockList().load();
        // Relit aussi les dim flags du disque (permet d'editer dimension-flags.json
        // a chaud sans redemarrer le serveur).
        try {
            com.arcadia.arcadiaguard.persist.DimFlagSerializer.read(
                ArcadiaGuard.dimFlagStore(),
                com.arcadia.arcadiaguard.ArcadiaGuardPaths.dimFlagsFile());
        } catch (java.io.IOException e) {
            ArcadiaGuard.LOGGER.error("[ArcadiaGuard] /ag reload — failed to reload dimension flags", e);
        }
        // Invalide les caches statiques qui survivent a la reload des zones.
        com.arcadia.arcadiaguard.guard.GuardService.invalidateFrequencyCache();
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.reloaded"), true);
        return 1;
    }
}

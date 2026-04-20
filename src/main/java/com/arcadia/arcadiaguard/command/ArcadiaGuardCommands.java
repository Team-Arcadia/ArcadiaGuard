package com.arcadia.arcadiaguard.command;

import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.command.sub.DebugCommands;
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
            .then(DebugCommands.build());

        dispatcher.register(root);
        dispatcher.register(literal("ag").redirect(dispatcher.getRoot().getChild("arcadiaguard")));
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
            "\u00a76\u25ac\u25ac\u25ac ArcadiaGuard \u25ac\u25ac\u25ac \u00a77v" + ArcadiaGuard.MOD_ID), false);

        // Bouton GUI proéminent
        MutableComponent guiBtn = Component.literal("\u00a7a[\u00a7e \u2726 Ouvrir l'interface \u00a7a]")
            .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ag gui"))
                             .withUnderlined(true));
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a77  ").append(guiBtn), false);

        // Zones
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a76 \u25b6 Zones"), false);
        sendCmd(ctx, "/ag zone list",                              "Lister les zones de la dimension");
        sendCmd(ctx, "/ag zone info <nom>",                        "Détails + flags d'une zone");
        sendCmd(ctx, "/ag zone create <nom>",                      "Créer (après sélection baguette)");
        sendCmd(ctx, "/ag zone remove <nom>",                      "Supprimer une zone");
        sendCmd(ctx, "/ag zone copy <nom> <nouveau>",              "Dupliquer une zone");
        sendCmd(ctx, "/ag zone flag <nom> <flag> allow|deny|reset","Configurer un flag");
        sendCmd(ctx, "/ag zone whitelist add <zone> <joueur>",     "Ajouter à la whitelist");
        sendCmd(ctx, "/ag zone whitelist role <zone> <joueur> <rôle>", "Assigner OWNER/MODERATOR/MEMBER");
        sendCmd(ctx, "/ag zone parent set <sous-zone> <parent>",   "Définir la zone parente");

        // Items
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a76 \u25b6 Items"), false);
        sendCmd(ctx, "/ag item block <item>",   "Bloquer un item dans toutes les zones");
        sendCmd(ctx, "/ag item unblock <item>", "Débloquer un item");
        sendCmd(ctx, "/ag item list",           "Lister les items bloqués");

        // Log
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a76 \u25b6 Logs"), false);
        sendCmd(ctx, "/ag log",                 "Dernières entrées du journal");
        sendCmd(ctx, "/ag log <zone>",          "Filtrer par zone");
        sendCmd(ctx, "/ag log <zone> <joueur>", "Filtrer par zone et joueur");

        // Admin
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a76 \u25b6 Admin"), false);
        sendCmd(ctx, "/ag wand give editor|viewer", "Obtenir la baguette de sélection");
        sendCmd(ctx, "/ag migrate yawp",            "Importer les zones depuis YAWP");
        sendCmd(ctx, "/ag reload",                  "Recharger les zones et les items bloqués");

        return 1;
    }

    private static void sendCmd(CommandContext<CommandSourceStack> ctx, String cmd, String desc) {
        MutableComponent text = Component.literal("\u00a7e" + cmd)
            .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd)));
        MutableComponent line = Component.literal("\u00a77 \u2192 ").append(text)
            .append(Component.literal("\u00a78 — \u00a77" + desc));
        ctx.getSource().sendSuccess(() -> line, false);
    }

    private static int openGui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
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
        ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.reloaded"), true);
        return 1;
    }
}

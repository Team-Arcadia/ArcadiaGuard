package com.arcadia.arcadiaguard.selftest;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.item.ModItems;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commande /ag testsetup — aide les testeurs humains à configurer rapidement
 * les zones de test pour la procédure HTML de test manuel.
 *
 * <pre>
 * /ag testsetup all        → crée toutes les zones de test + donne le wand
 * /ag testsetup clean      → supprime toutes les zones "agt-*"
 * /ag testsetup tp <n>     → téléporte au centre de la zone n (1-23)
 * /ag testsetup list       → liste toutes les zones de test créées
 * </pre>
 */
public final class TestSetupCommand {

    private TestSetupCommand() {}

    /** Prefixe de toutes les zones creees par cette commande. */
    private static final String PREFIX = "agt-";

    /** Rayon par defaut de chaque zone de test (en blocs). */
    private static final int RADIUS = 10;

    /** Distance entre le centre de deux zones adjacentes. */
    private static final int SPACING = 30;

    // ── Définition de chaque zone de test ────────────────────────────────────

    private record ZoneDef(int num, String shortName, String desc, String flagId, Object flagValue) {}

    private static final List<ZoneDef> ZONES = List.of(
        // Mouvements
        new ZoneDef( 1, "entry",       "T-01 Blocage d'entrée",                BuiltinFlags.ENTRY.id(),            false),
        new ZoneDef( 2, "exit",        "T-02 Blocage de sortie",               BuiltinFlags.EXIT.id(),             false),
        new ZoneDef( 3, "fly",         "T-03 Blocage du vol",                  BuiltinFlags.FLY.id(),              false),
        new ZoneDef( 4, "elytra",      "T-04 Blocage de l'élytre",             BuiltinFlags.USE_ELYTRA.id(),       false),
        new ZoneDef( 5, "portal",      "T-05 Blocage des portails",            BuiltinFlags.USE_PORTAL.id(),       false),
        // Combat
        new ZoneDef( 6, "pvp",         "T-06 PvP bloqué",                      BuiltinFlags.PVP.id(),              false),
        new ZoneDef( 7, "enderpearl",  "T-07 Perle de l'End bloquée",          BuiltinFlags.ENDER_PEARL.id(),      false),
        new ZoneDef( 8, "heal",        "T-08 Regen santé (heal-amount=4)",      BuiltinFlags.HEAL_AMOUNT.id(),      4),
        // Inventaire & Chat
        new ZoneDef( 9, "itemdrop",    "T-09 Drop d'objet bloqué",             BuiltinFlags.ITEM_DROP.id(),        false),
        new ZoneDef(10, "chat",        "T-10 Chat bloqué",                     BuiltinFlags.SEND_CHAT.id(),        false),
        new ZoneDef(11, "command",     "T-11 Commande /home bloquée",          BuiltinFlags.EXEC_COMMAND_BLACKLIST.id(), List.of("home", "spawn", "tpa")),
        // Wand — zone vide pour pratiquer la selection
        new ZoneDef(12, "wand",        "T-12 Zone vide (pratique wand)",        null,                               null),
        // GUI — zone riche avec plusieurs flags pour tester l'interface
        new ZoneDef(13, "gui",         "T-13/14/16 Zone GUI multi-flags",       BuiltinFlags.BLOCK_BREAK.id(),      false),
        // Whitelist
        new ZoneDef(17, "whitelist",   "T-17 Whitelist bypass (entry=deny)",    BuiltinFlags.ENTRY.id(),            false),
        // Audit
        new ZoneDef(18, "audit",       "T-18 Journal d'audit (block-break=deny)",BuiltinFlags.BLOCK_BREAK.id(),     false),
        // Mods tiers
        new ZoneDef(19, "ars",         "T-19 Ars Nouveau sorts bloqués",        BuiltinFlags.ARS_SPELL_CAST.id(),   false),
        new ZoneDef(20, "irons",       "T-20 Iron's Spellbooks sorts bloqués",  BuiltinFlags.IRONS_SPELL_CAST.id(), false),
        new ZoneDef(21, "waystones",   "T-21 Waystones bloquées",               BuiltinFlags.WAYSTONE_USE.id(),     false),
        new ZoneDef(22, "carryon",     "T-22 Carry On bloqué",                  BuiltinFlags.CARRYON.id(),          false),
        new ZoneDef(23, "parcool",     "T-23 ParCool bloqué",                   BuiltinFlags.PARCOOL_ACTIONS.id(),  false)
    );

    // ── Brigadier ────────────────────────────────────────────────────────────

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("testsetup")
            .requires(src -> src.hasPermission(2))
            .executes(TestSetupCommand::showHelp)
            .then(literal("all")   .executes(TestSetupCommand::setupAll))
            .then(literal("clean") .executes(TestSetupCommand::clean))
            .then(literal("list")  .executes(TestSetupCommand::listZones))
            .then(literal("tp")
                .then(argument("num", IntegerArgumentType.integer(1, 23))
                    .executes(TestSetupCommand::tp)));
    }

    // ── Commandes ────────────────────────────────────────────────────────────

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("━━ ArcadiaGuard TestSetup ━━").withStyle(ChatFormatting.GOLD), false);
        sendCmd(src, "/ag testsetup all",    "Créer toutes les zones de test + donner le wand");
        sendCmd(src, "/ag testsetup clean",  "Supprimer toutes les zones agt-*");
        sendCmd(src, "/ag testsetup list",   "Lister les zones de test créées");
        sendCmd(src, "/ag testsetup tp <n>", "Téléporter au centre de la zone n (1-23)");
        return 1;
    }

    private static int setupAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level   = player.serverLevel();
        BlockPos origin     = player.blockPosition();
        String dim          = level.dimension().location().toString();

        int created = 0;
        int skipped = 0;

        // ── Zones numérotées en grille (5 colonnes, Z+SPACING par rangée) ──
        for (int i = 0; i < ZONES.size(); i++) {
            ZoneDef def = ZONES.get(i);
            int col = i % 5;
            int row = i / 5;
            BlockPos center = origin.offset(col * SPACING, 0, row * SPACING);
            String name = PREFIX + def.shortName();

            if (ArcadiaGuard.zoneManager().get(level, name).isPresent()) {
                skipped++;
                continue;
            }

            ProtectedZone zone = new ProtectedZone(name, dim,
                center.offset(-RADIUS, -RADIUS, -RADIUS),
                center.offset( RADIUS,  RADIUS,  RADIUS));

            if (def.flagId() != null) {
                zone.setFlag(def.flagId(), def.flagValue());
            }
            ArcadiaGuard.zoneManager().add(level, zone);
            created++;
        }

        // ── Zone GUI enrichie (T-13/14/16) — flags supplémentaires ──
        ArcadiaGuard.zoneManager().get(level, PREFIX + "gui").ifPresent(z -> {
            ProtectedZone pz = (ProtectedZone) z;
            pz.setFlag(BuiltinFlags.PVP.id(),          false);
            pz.setFlag(BuiltinFlags.DOOR.id(),          false);
            pz.setFlag(BuiltinFlags.CONTAINER_ACCESS.id(), false);
            pz.setFlag(BuiltinFlags.ITEM_DROP.id(),     false);
        });

        // ── Zones parent/enfant pour T-15 (badge INH) ──
        String parentName = PREFIX + "parent";
        String childName  = PREFIX + "child";
        if (ArcadiaGuard.zoneManager().get(level, parentName).isEmpty()) {
            int col = 3; int row = 3;
            BlockPos center = origin.offset(col * SPACING, 0, row * SPACING);
            ProtectedZone parent = new ProtectedZone(parentName, dim,
                center.offset(-15, -RADIUS, -15), center.offset(15, RADIUS, 15));
            parent.setFlag(BuiltinFlags.BLOCK_BREAK.id(), false);
            parent.setFlag(BuiltinFlags.DOOR.id(), false);
            ArcadiaGuard.zoneManager().add(level, parent);

            ProtectedZone child = new ProtectedZone(childName, dim,
                center.offset(-6, -RADIUS, -6), center.offset(6, RADIUS, 6));
            child.setParent(parentName);
            ArcadiaGuard.zoneManager().add(level, child);
            created += 2;
        }

        // ── Donner le wand ──
        player.getInventory().add(ModItems.ZONE_EDITOR.get().getDefaultInstance());

        // ── Résumé ──
        final int c = created, s = skipped;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "✅ TestSetup terminé : " + c + " zones créées" +
            (s > 0 ? ", " + s + " déjà existantes (ignorées)" : "") +
            " · Wand donné")
            .withStyle(ChatFormatting.GREEN), false);

        ctx.getSource().sendSuccess(() -> Component.literal(
            "📍 Zones créées à partir de " + origin.toShortString() +
            " · Utilise /ag testsetup tp <n> pour te téléporter")
            .withStyle(ChatFormatting.GRAY), false);

        // Afficher la table des zones
        return printZoneTable(ctx.getSource(), level, origin);
    }

    private static int clean(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        int removed = 0;

        // Zones numérotées
        for (ZoneDef def : ZONES) {
            if (ArcadiaGuard.zoneManager().remove(level, PREFIX + def.shortName())) removed++;
        }
        // Zones parent/enfant
        if (ArcadiaGuard.zoneManager().remove(level, PREFIX + "child"))  removed++;
        if (ArcadiaGuard.zoneManager().remove(level, PREFIX + "parent")) removed++;

        final int r = removed;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "🗑️ " + r + " zones de test supprimées (agt-*)")
            .withStyle(ChatFormatting.YELLOW), false);
        return r;
    }

    private static int listZones(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        // On cherche l'origin dans les zones existantes (on ne la connaît pas ici)
        return printZoneTable(ctx.getSource(), level, null);
    }

    private static int tp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int num = IntegerArgumentType.getInteger(ctx, "num");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level   = player.serverLevel();

        // Trouver la zone par numéro
        ZoneDef def = ZONES.stream().filter(z -> z.num() == num).findFirst().orElse(null);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("Zone n°" + num + " inconnue."));
            return 0;
        }

        String name = PREFIX + def.shortName();
        var found = ArcadiaGuard.zoneManager().get(level, name);
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                "Zone " + name + " introuvable — lance d'abord /ag testsetup all"));
            return 0;
        }

        ProtectedZone zone = (ProtectedZone) found.get();
        double cx = (zone.minX() + zone.maxX()) / 2.0;
        double cy =  zone.maxY() + 1;
        double cz = (zone.minZ() + zone.maxZ()) / 2.0;
        player.teleportTo(level, cx, cy, cz, player.getYRot(), player.getXRot());

        ctx.getSource().sendSuccess(() -> Component.literal(
            "📍 Téléportation → " + name + " — " + def.desc())
            .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int printZoneTable(CommandSourceStack src, ServerLevel level, BlockPos origin) {
        src.sendSuccess(() -> Component.literal(
            "┌─── Zones de test ArcadiaGuard (" + PREFIX + "*) ───┐")
            .withStyle(ChatFormatting.DARK_AQUA), false);

        for (ZoneDef def : ZONES) {
            String name = PREFIX + def.shortName();
            boolean exists = ArcadiaGuard.zoneManager().get(level, name).isPresent();
            ChatFormatting color = exists ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
            String status = exists ? "✅" : "✗ ";

            MutableComponent tpBtn = Component.literal(" [tp]")
                .withStyle(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/ag testsetup tp " + def.num()))
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true));

            MutableComponent line = Component.literal(
                "  " + status + " T-" + String.format("%02d", def.num()) +
                " · " + def.desc())
                .withStyle(color);

            if (exists) line = line.append(tpBtn);
            final MutableComponent finalLine = line;
            src.sendSuccess(() -> finalLine, false);
        }

        // Zones parent/enfant
        boolean parentExists = ArcadiaGuard.zoneManager().get(level, PREFIX + "parent").isPresent();
        boolean childExists  = ArcadiaGuard.zoneManager().get(level, PREFIX + "child").isPresent();
        ChatFormatting pc = parentExists ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
        src.sendSuccess(() -> Component.literal(
            "  " + (parentExists ? "✅" : "✗ ") + " T-15 · Badge INH — " + PREFIX + "parent + " + PREFIX + "child")
            .withStyle(pc), false);

        src.sendSuccess(() -> Component.literal("└────────────────────────────────────────────┘")
            .withStyle(ChatFormatting.DARK_AQUA), false);
        return 1;
    }

    private static void sendCmd(CommandSourceStack src, String cmd, String desc) {
        MutableComponent line = Component.literal("  ")
            .append(Component.literal(cmd).withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
                .withColor(ChatFormatting.YELLOW)))
            .append(Component.literal(" — " + desc).withStyle(ChatFormatting.GRAY));
        src.sendSuccess(() -> line, false);
    }
}

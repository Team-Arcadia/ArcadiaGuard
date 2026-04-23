package com.arcadia.arcadiaguard.selftest;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.flag.IntFlag;
import com.arcadia.arcadiaguard.api.flag.ListFlag;
import com.arcadia.arcadiaguard.item.ModItems;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commande /ag testsetup — aide les testeurs humains a configurer rapidement
 * un environnement dense pour eprouver le panel (scroll, hierarchie, flags varies).
 *
 * <pre>
 * /ag testsetup all        -> cree 100 zones hierarchiques (20 roots / 50 enfants / 30 petits-enfants),
 *                             un flag different par zone, en grille 10x10 autour du joueur
 * /ag testsetup removeall  -> supprime TOUTES les zones de TOUTES les dimensions (remise a zero)
 * /ag testsetup clean      -> supprime uniquement les zones creees par cette commande (prefixe "agt-")
 * /ag testsetup tp n       -> teleporte au centre de la zone agt-NNN
 * /ag testsetup list       -> liste les zones de test existantes
 * </pre>
 */
public final class TestSetupCommand {

    private TestSetupCommand() {}

    /** Prefixe de toutes les zones creees par cette commande. */
    private static final String PREFIX = "agt-";

    /** Nombre total de zones generees. */
    private static final int TOTAL_ZONES = 100;

    /** 20 zones "racines" (sans parent). */
    private static final int ROOT_COUNT = 20;

    /** 50 zones enfants (parent = une root). Indices [ROOT_COUNT, ROOT_COUNT+CHILD_COUNT). */
    private static final int CHILD_COUNT = 50;

    /** 30 zones petites-enfants (parent = un enfant). Indices [70, 100). */
    private static final int GRANDCHILD_COUNT = TOTAL_ZONES - ROOT_COUNT - CHILD_COUNT;

    /** Rayon par defaut de chaque zone (en blocs). */
    private static final int RADIUS = 10;

    /** Distance entre le centre de deux zones adjacentes dans la grille. */
    private static final int SPACING = 30;

    /** Colonnes de la grille (10 colonnes x 10 rangees = 100 zones). */
    private static final int GRID_COLS = 10;

    /**
     * Nombre de zones creees par tick serveur. Trade-off entre rapidite et TPS :
     * 5 zones/tick × 20 tps = 100 zones/sec, soit ~10 s pour 10 dims.
     * Le throttle evite un stall serveur sur un modpack a nombreuses dimensions.
     */
    private static final int ZONES_PER_TICK = 5;

    /** File d'attente partagee des operations de setup restantes. */
    private static final Deque<Runnable> PENDING = new ArrayDeque<>();

    /** Indique qu'une passe de setup est en cours (bloque les appels concurrents). */
    private static volatile boolean setupRunning = false;

    /** Expose la file pour permettre a d'autres commandes (ex: /ag diagnostic) de chainer leurs taches. */
    public static void enqueue(Runnable task) { PENDING.addLast(task); }

    /** True si la file contient encore au moins une tache. */
    public static boolean isQueueBusy() { return !PENDING.isEmpty(); }

    // ── Brigadier ────────────────────────────────────────────────────────────

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("testsetup")
            .requires(src -> src.hasPermission(2))
            .executes(TestSetupCommand::showHelp)
            .then(literal("all")       .executes(TestSetupCommand::setupAll))
            .then(literal("removeall") .executes(TestSetupCommand::removeAll))
            .then(literal("clean")     .executes(TestSetupCommand::clean))
            .then(literal("list")      .executes(TestSetupCommand::listZones))
            .then(literal("tp")
                .then(argument("num", IntegerArgumentType.integer(1, TOTAL_ZONES))
                    .executes(TestSetupCommand::tp)));
    }

    // ── Commandes ────────────────────────────────────────────────────────────

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("━━ ArcadiaGuard TestSetup ━━").withStyle(ChatFormatting.GOLD), false);
        sendCmd(src, "/ag testsetup all",       "Créer 100 zones hiérarchiques (root/enfant/petit-enfant) + donner le wand");
        sendCmd(src, "/ag testsetup removeall", "⚠ Supprimer TOUTES les zones de toutes les dimensions");
        sendCmd(src, "/ag testsetup clean",     "Supprimer uniquement les zones agt-*");
        sendCmd(src, "/ag testsetup list",      "Lister les zones de test créées");
        sendCmd(src, "/ag testsetup tp <n>",    "Téléporter au centre de la zone n (1-" + TOTAL_ZONES + ")");
        return 1;
    }

    private static int setupAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (setupRunning) {
            ctx.getSource().sendFailure(Component.literal(
                "⏳ Setup déjà en cours — attends la fin (ou /ag testsetup list)"));
            return 0;
        }
        CommandSourceStack src = ctx.getSource();
        int dims = scheduleSetupAll(player, () -> {
            player.getInventory().add(ModItems.ZONE_EDITOR.get().getDefaultInstance());
            src.sendSuccess(() -> Component.literal(
                "✅ TestSetup terminé · " + ROOT_COUNT + " root / " + CHILD_COUNT + " enfant / "
                + GRANDCHILD_COUNT + " petit-enfant par dim")
                .withStyle(ChatFormatting.GREEN), false);
        });
        int totalOps = PENDING.size();
        int estSeconds = Math.max(1, totalOps / ZONES_PER_TICK / 20);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "⏳ TestSetup en cours — " + TOTAL_ZONES + "×" + dims + " zones, "
            + ZONES_PER_TICK + "/tick, ≈" + estSeconds + " s")
            .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /**
     * Planifie la creation idempotente des 100 zones par dimension autour de {@code player}.
     * Toutes les taches sont ajoutees a la file {@link #PENDING} et executees au rythme
     * de {@link #ZONES_PER_TICK} par tick serveur. Le {@code onComplete} est execute a la fin.
     *
     * @return le nombre de dimensions qui seront traitees
     */
    public static int scheduleSetupAll(ServerPlayer player, Runnable onComplete) {
        ServerLevel currentLevel = player.serverLevel();
        MinecraftServer server = player.getServer();

        List<Flag<?>> flags = new ArrayList<>();
        for (Flag<?> f : ArcadiaGuard.flagRegistry().all()) {
            String mod = f.requiredMod();
            if (mod.isEmpty() || net.neoforged.fml.ModList.get().isLoaded(mod)) flags.add(f);
        }

        List<ServerLevel> targetLevels = new ArrayList<>();
        targetLevels.add(currentLevel);
        for (ServerLevel lvl : server.getAllLevels()) {
            if (lvl != currentLevel) targetLevels.add(lvl);
        }

        for (ServerLevel level : targetLevels) {
            BlockPos origin = (level == currentLevel) ? player.blockPosition() : new BlockPos(0, 64, 0);
            String dim = level.dimension().location().toString();
            String dimSuffix = shortDimSuffix(dim);

            for (int i = 0; i < TOTAL_ZONES; i++) {
                final int idx = i;
                PENDING.addLast(() -> {
                    String name = zoneName(idx, dimSuffix);
                    if (ArcadiaGuard.zoneManager().get(level, name).isPresent()) return;
                    int col = idx % GRID_COLS;
                    int row = idx / GRID_COLS;
                    BlockPos center = origin.offset(col * SPACING, 0, row * SPACING);
                    ProtectedZone zone = new ProtectedZone(name, dim,
                        center.offset(-RADIUS, -RADIUS, -RADIUS),
                        center.offset( RADIUS,  RADIUS,  RADIUS));
                    if (!flags.isEmpty()) {
                        Flag<?> flag = flags.get(idx % flags.size());
                        Object value = demoValueFor(flag);
                        if (value != null) zone.setFlag(flag.id(), value);
                    }
                    ArcadiaGuard.zoneManager().add(level, zone);
                });
            }
            PENDING.addLast(() -> linkHierarchy(level, dimSuffix));
        }

        PENDING.addLast(() -> {
            setupRunning = false;
            if (onComplete != null) onComplete.run();
        });
        setupRunning = true;
        return targetLevels.size();
    }

    /**
     * Applique les liens parent/enfant/petit-enfant sur les zones d'un level.
     * Appelee apres la creation des bounds (passe 2 du setup).
     */
    private static void linkHierarchy(ServerLevel level, String dimSuffix) {
        for (int i = ROOT_COUNT; i < ROOT_COUNT + CHILD_COUNT; i++) {
            int parentIdx = (i - ROOT_COUNT) % ROOT_COUNT;
            ArcadiaGuard.zoneManager().get(level, zoneName(i, dimSuffix))
                .ifPresent(z -> ((ProtectedZone) z).setParent(zoneName(parentIdx, dimSuffix)));
        }
        for (int i = ROOT_COUNT + CHILD_COUNT; i < TOTAL_ZONES; i++) {
            int parentIdx = ROOT_COUNT + ((i - ROOT_COUNT - CHILD_COUNT) % CHILD_COUNT);
            ArcadiaGuard.zoneManager().get(level, zoneName(i, dimSuffix))
                .ifPresent(z -> ((ProtectedZone) z).setParent(zoneName(parentIdx, dimSuffix)));
        }
    }

    /**
     * Hook tick serveur : depile jusqu'a {@link #ZONES_PER_TICK} operations par tick.
     * A brancher sur {@link net.neoforged.neoforge.event.tick.ServerTickEvent.Post} via
     * NeoForge EVENT_BUS — enregistrement effectue une seule fois dans {@link #build()}.
     */
    public static void onServerTick() {
        for (int i = 0; i < ZONES_PER_TICK && !PENDING.isEmpty(); i++) {
            try {
                PENDING.pollFirst().run();
            } catch (Exception e) {
                ArcadiaGuard.LOGGER.error("[ArcadiaGuard] TestSetup tick task failed", e);
            }
        }
    }

    /**
     * Supprime TOUTES les zones de TOUTES les dimensions — remise a zero totale.
     * A manipuler avec precaution : aucun filtre de prefixe, aucune confirmation.
     */
    private static int removeAll(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();

        // Collecte defensive : on liste toutes les zones avant suppression pour ne pas
        // muter la collection pendant l'iteration (remove() modifie la map sous-jacente).
        List<ZoneRef> targets = new ArrayList<>();
        for (ServerLevel lvl : server.getAllLevels()) {
            for (var iz : ArcadiaGuard.zoneManager().zones(lvl)) {
                targets.add(new ZoneRef(lvl, iz.name()));
            }
        }

        int removed = 0;
        for (ZoneRef t : targets) {
            if (ArcadiaGuard.zoneManager().remove(t.level, t.name)) removed++;
        }

        final int r = removed;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "🗑 RemoveAll : " + r + " zone" + (r > 1 ? "s" : "") + " supprimée" + (r > 1 ? "s" : "") +
            " (toutes dimensions confondues)")
            .withStyle(ChatFormatting.RED), true);
        return r;
    }

    private static int clean(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            String dimSuffix = shortDimSuffix(level.dimension().location().toString());
            for (int i = 0; i < TOTAL_ZONES; i++) {
                if (ArcadiaGuard.zoneManager().remove(level, zoneName(i, dimSuffix))) removed++;
            }
        }
        final int r = removed;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "🗑 " + r + " zones agt-* supprimées (toutes dimensions)")
            .withStyle(ChatFormatting.YELLOW), false);
        return r;
    }

    private static int listZones(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        int existing = 0;
        int dimCount = 0;
        for (ServerLevel level : server.getAllLevels()) {
            dimCount++;
            String dimSuffix = shortDimSuffix(level.dimension().location().toString());
            for (int i = 0; i < TOTAL_ZONES; i++) {
                if (ArcadiaGuard.zoneManager().get(level, zoneName(i, dimSuffix)).isPresent()) existing++;
            }
        }
        final int e = existing;
        final int total = TOTAL_ZONES * dimCount;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Zones agt-* présentes : " + e + " / " + total + " (toutes dimensions)")
            .withStyle(ChatFormatting.AQUA), false);
        return existing;
    }

    private static int tp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int num = IntegerArgumentType.getInteger(ctx, "num");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level   = player.serverLevel();
        String dimSuffix = shortDimSuffix(level.dimension().location().toString());

        String name = zoneName(num - 1, dimSuffix);
        var found = ArcadiaGuard.zoneManager().get(level, name);
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                "Zone " + name + " introuvable — lance /ag testsetup all"));
            return 0;
        }
        ProtectedZone zone = (ProtectedZone) found.get();
        double cx = (zone.minX() + zone.maxX()) / 2.0;
        double cy =  zone.maxY() + 1;
        double cz = (zone.minZ() + zone.maxZ()) / 2.0;
        player.teleportTo(level, cx, cy, cz, player.getYRot(), player.getXRot());

        ctx.getSource().sendSuccess(() -> Component.literal(
            "📍 Téléportation → " + name)
            .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Nom canonique d'une zone indexee dans une dimension donnee.
     * Ex : (0, "overworld") -> "agt-001-overworld", (56, "the_nether") -> "agt-057-the_nether".
     * Le suffixe de dimension garantit l'unicite cross-dim (sinon click "Détails"
     * en vue ALL du panel tombe sur la premiere dim trouvee, pas forcement la bonne).
     */
    private static String zoneName(int index, String dimSuffix) {
        return PREFIX + String.format("%03d", index + 1) + "-" + dimSuffix;
    }

    /**
     * Extrait un suffixe court et file-system-safe depuis un dim key.
     * Exemples : "minecraft:overworld" -> "overworld", "aether:the_aether" -> "the_aether",
     * "twilightforest:twilight_forest" -> "twilight_fores" (tronque a 16 chars).
     * Seuls [a-z0-9_-] sont conserves, pour rester compatible avec
     * {@link com.arcadia.arcadiaguard.ArcadiaGuardPaths#isValidZoneName(String)}.
     */
    private static String shortDimSuffix(String dimKey) {
        int sep = dimKey.indexOf(':');
        String path = sep >= 0 ? dimKey.substring(sep + 1) : dimKey;
        StringBuilder sb = new StringBuilder(Math.min(path.length(), 16));
        for (int i = 0; i < path.length() && sb.length() < 16; i++) {
            char c = Character.toLowerCase(path.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? "dim" : sb.toString();
    }

    /**
     * Valeur de demonstration a appliquer pour un flag donne, ou {@code null}
     * si le type du flag n'est pas supporte (pas de setter pertinent).
     */
    private static Object demoValueFor(Flag<?> flag) {
        if (flag instanceof BooleanFlag) return false;            // interdit l'action
        if (flag instanceof IntFlag) return 1;                    // valeur non-defaut triviale
        if (flag instanceof ListFlag) return List.of("demo");     // liste non vide
        return null;
    }

    /** Simple paire (level, zoneName) pour itérer les suppressions sans muter la collection source. */
    private record ZoneRef(ServerLevel level, String name) {}

    private static void sendCmd(CommandSourceStack src, String cmd, String desc) {
        MutableComponent line = Component.literal("  ")
            .append(Component.literal(cmd).withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
                .withColor(ChatFormatting.YELLOW)))
            .append(Component.literal(" — " + desc).withStyle(ChatFormatting.GRAY));
        src.sendSuccess(() -> line, false);
    }
}

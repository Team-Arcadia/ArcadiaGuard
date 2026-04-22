package com.arcadia.arcadiaguard.selftest;

import com.arcadia.arcadiaguard.selftest.scenarios.BlockFlagScenarios;
import com.arcadia.arcadiaguard.selftest.scenarios.CombatScenarios;
import com.arcadia.arcadiaguard.selftest.scenarios.ModIntegrationScenarios;
import com.arcadia.arcadiaguard.selftest.scenarios.ZoneCrudScenarios;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sub-commande /ag selftest qui execute les scenarios in-game.
 *
 * <p>Usage :
 * <pre>
 * /ag selftest          → tous les scenarios
 * /ag selftest all      → idem
 * /ag selftest blocks   → scenarios categorie "blocks"
 * /ag selftest mods.ars_nouveau → scenarios specifiques a Ars Nouveau
 * /ag selftest block-break-deny → scenario unique
 * </pre>
 */
public final class SelfTestCommand {

    private SelfTestCommand() {}

    /** Enregistre les scenarios builtin. Appele une fois au mod setup. */
    public static void registerBuiltinScenarios() {
        SelfTestRunner.register(
            // core
            ZoneCrudScenarios.ZONE_CREATE_DELETE,
            ZoneCrudScenarios.FLAG_REGISTRY_COUNT,
            ZoneCrudScenarios.FLAG_SET_RESET,
            // blocks
            BlockFlagScenarios.BLOCK_BREAK_DENY,
            BlockFlagScenarios.BLOCK_BREAK_ALLOW,
            BlockFlagScenarios.BLOCK_PLACE_DENY,
            // combat
            CombatScenarios.INVINCIBLE_FLAG,
            CombatScenarios.ATTACK_ANIMALS_DENY,
            CombatScenarios.FALL_DAMAGE_ALLOW,
            // mods tiers (skipped si mod absent)
            ModIntegrationScenarios.ARS_NOUVEAU,
            ModIntegrationScenarios.IRONS_SPELLBOOKS,
            ModIntegrationScenarios.SIMPLYSWORDS,
            ModIntegrationScenarios.PARCOOL,
            ModIntegrationScenarios.EMOTECRAFT,
            ModIntegrationScenarios.CARRYON,
            ModIntegrationScenarios.WAYSTONES,
            ModIntegrationScenarios.APOTHEOSIS,
            ModIntegrationScenarios.OCCULTISM,
            ModIntegrationScenarios.SUPPLEMENTARIES,
            ModIntegrationScenarios.RECHISELED,
            ModIntegrationScenarios.ARS_ADDITIONS,
            ModIntegrationScenarios.TWILIGHT_FOREST,
            ModIntegrationScenarios.MUTANT_MONSTERS,
            ModIntegrationScenarios.LUCKPERMS,
            ModIntegrationScenarios.YAWP
        );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("selftest")
            .requires(src -> src.hasPermission(2))
            .executes(SelfTestCommand::runAll)
            .then(Commands.argument("filter", StringArgumentType.word())
                .suggests(SUGGEST_FILTERS)
                .executes(SelfTestCommand::runFiltered));
    }

    private static int runAll(CommandContext<CommandSourceStack> ctx) {
        return doRun(ctx, "all");
    }

    private static int runFiltered(CommandContext<CommandSourceStack> ctx) {
        String filter = StringArgumentType.getString(ctx, "filter");
        return doRun(ctx, filter);
    }

    private static int doRun(CommandContext<CommandSourceStack> ctx, String filter) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal(
                "SelfTest requires a player context (not server console)")
                .withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal(
            "Running ArcadiaGuard selftest (filter=" + filter + ")...")
            .withStyle(ChatFormatting.YELLOW));
        var results = SelfTestRunner.runAll(player, filter);
        SelfTestRunner.summarize(player, results);
        return (int) results.stream().filter(r -> r.status() == ScenarioResult.Status.PASS).count();
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_FILTERS =
        (context, builder) -> {
            builder.suggest("all");
            java.util.Set<String> cats = new java.util.TreeSet<>();
            java.util.Set<String> ids = new java.util.TreeSet<>();
            for (var sc : SelfTestRunner.all()) {
                cats.add(sc.category());
                ids.add(sc.id());
            }
            cats.forEach(builder::suggest);
            ids.forEach(builder::suggest);
            return builder.buildFuture();
        };
}

package com.arcadia.arcadiaguard.selftest;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * Orchestre l'execution des scenarios de test. Filtre par categorie/mod, genere le
 * rapport JSON et affiche un resume au chat du joueur executant.
 */
public final class SelfTestRunner {

    private static final List<Scenario> REGISTRY = new ArrayList<>();

    private SelfTestRunner() {}

    public static void register(Scenario... scenarios) {
        REGISTRY.addAll(Arrays.asList(scenarios));
    }

    public static List<Scenario> all() { return List.copyOf(REGISTRY); }

    /** Execute tous les scenarios qui matchent le filtre (categorie ou "all"). */
    public static List<ScenarioResult> runAll(ServerPlayer player, String categoryFilter) {
        List<ScenarioResult> results = new ArrayList<>();
        TestContext ctx = new TestContext(player);

        for (Scenario sc : REGISTRY) {
            // Filtre par categorie.
            if (categoryFilter != null && !"all".equalsIgnoreCase(categoryFilter)
                && !sc.category().equalsIgnoreCase(categoryFilter)
                && !sc.id().equalsIgnoreCase(categoryFilter)) {
                continue;
            }
            // Skip si mod requis absent.
            String req = sc.requiredMod();
            if (req != null && !req.isEmpty() && !ModList.get().isLoaded(req)) {
                results.add(ScenarioResult.skip(sc.id(), "required mod '" + req + "' not loaded"));
                continue;
            }

            long start = System.nanoTime();
            try {
                ScenarioResult r = sc.run(ctx);
                results.add(r);
            } catch (Throwable t) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                results.add(ScenarioResult.fail(sc.id(),
                    "exception: " + t.getClass().getSimpleName() + ": " + t.getMessage(), ms));
            } finally {
                ctx.cleanup();
            }
        }

        return results;
    }

    /** Affiche un resume au chat + sauvegarde le rapport JSON. */
    public static void summarize(ServerPlayer player, List<ScenarioResult> results) {
        int pass = 0, fail = 0, skip = 0;
        for (var r : results) switch (r.status()) {
            case PASS -> pass++;
            case FAIL -> fail++;
            case SKIP -> skip++;
        }

        int total = results.size();
        player.sendSystemMessage(Component.literal("─── ArcadiaGuard SelfTest ───")
            .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(
            String.format("Total: %d · %d passed · %d failed · %d skipped", total, pass, fail, skip))
            .withStyle(ChatFormatting.WHITE));

        // Detail des fails.
        for (var r : results) {
            if (r.status() == ScenarioResult.Status.FAIL) {
                player.sendSystemMessage(Component.literal("  ✗ " + r.id() + " — " + r.notes())
                    .withStyle(ChatFormatting.RED));
            }
        }

        // Rapport JSON.
        try {
            Path file = writeJsonReport(results);
            player.sendSystemMessage(Component.literal("Rapport JSON : " + file.toString())
                .withStyle(ChatFormatting.GRAY));
        } catch (IOException e) {
            player.sendSystemMessage(Component.literal("Erreur ecriture rapport : " + e.getMessage())
                .withStyle(ChatFormatting.RED));
        }
    }

    private static Path writeJsonReport(List<ScenarioResult> results) throws IOException {
        Path dir = com.arcadia.arcadiaguard.ArcadiaGuardPaths.configRoot();
        Files.createDirectories(dir);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path file = dir.resolve("selftest-" + ts + ".json");

        JsonObject root = new JsonObject();
        root.addProperty("timestamp", LocalDateTime.now().toString());
        root.addProperty("modVersion", ArcadiaGuard.MOD_ID);
        root.addProperty("minecraft", net.minecraft.SharedConstants.getCurrentVersion().getName());

        JsonArray arr = new JsonArray();
        for (var r : results) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id());
            o.addProperty("status", r.status().name());
            o.addProperty("notes", r.notes());
            o.addProperty("durationMs", r.durationMs());
            arr.add(o);
        }
        root.add("results", arr);

        JsonObject summary = new JsonObject();
        summary.addProperty("total", results.size());
        summary.addProperty("passed", (int) results.stream().filter(r -> r.status() == ScenarioResult.Status.PASS).count());
        summary.addProperty("failed", (int) results.stream().filter(r -> r.status() == ScenarioResult.Status.FAIL).count());
        summary.addProperty("skipped", (int) results.stream().filter(r -> r.status() == ScenarioResult.Status.SKIP).count());
        root.add("summary", summary);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer w = Files.newBufferedWriter(file)) { gson.toJson(root, w); }
        return file;
    }
}

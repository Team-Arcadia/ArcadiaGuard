package com.arcadia.arcadiaguard.selftest;

import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.flag.IntFlag;
import com.arcadia.arcadiaguard.api.flag.ListFlag;
import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsCompat;
import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsPermissionChecker;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.FlagResolver;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * Commande unique pour tout valider avant release :
 * <pre>
 * /ag diagnostic
 * </pre>
 *
 * Execute en arriere-plan, etalee sur les ticks serveur pour ne pas lag :
 * <ol>
 *   <li>Cree 100 zones par dimension via {@link TestSetupCommand#scheduleSetupAll}</li>
 *   <li>Ecrit ~11 fichiers JSON dans {@code logs/arcadia/debug/<timestamp>/} :
 *       summary / flags / zones / integrity / permissions / runtime / handlers /
 *       persistence / audit_tail / type_coherence / benchmark</li>
 * </ol>
 *
 * Chat : seulement un message "en cours" au lancement, puis "OK" ou "KO" a la fin.
 */
public final class DiagnosticCommand {

    private DiagnosticCommand() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Nombre de lignes d'audit log remontees dans audit_tail.json. */
    private static final int AUDIT_TAIL_LINES = 200;

    /** Iterations de benchmark par joueur par scenario. */
    private static final int BENCH_ITERATIONS = 1000;

    private static volatile boolean running = false;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("diagnostic")
            .requires(src -> src.hasPermission(2))
            .executes(DiagnosticCommand::run);
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (running) {
            src.sendFailure(Component.literal("⏳ Diagnostic déjà en cours — patiente"));
            return 0;
        }
        ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (CommandSyntaxException e) {
            src.sendFailure(Component.literal("❌ /ag diagnostic doit être lancé par un joueur"));
            return 0;
        }
        MinecraftServer server = src.getServer();

        Path outDir;
        try {
            String ts = LocalDateTime.now().format(TS_FMT);
            outDir = Paths.get("logs", "arcadia", "debug", ts);
            Files.createDirectories(outDir);
        } catch (IOException e) {
            src.sendFailure(Component.literal("❌ Impossible de créer le dossier : " + e.getMessage()));
            return 0;
        }

        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        long startNs = System.nanoTime();

        // ── Phase 1 : planifier la creation des zones de test
        int dimCount = TestSetupCommand.scheduleSetupAll(player, null);

        // ── Phase 2 : pour chaque diag file, enqueue un runnable (1 par tick, cadence existante)
        enqueueWrite(outDir.resolve("summary.json"),        () -> buildSummary(server, outDir, dimCount, startNs), errors);
        enqueueWrite(outDir.resolve("flags.json"),          DiagnosticCommand::buildFlags, errors);
        enqueueWrite(outDir.resolve("zones.json"),          () -> buildZones(server), errors);
        enqueueWrite(outDir.resolve("integrity.json"),      () -> buildIntegrity(server), errors);
        enqueueWrite(outDir.resolve("permissions.json"),    () -> buildPermissions(server), errors);
        enqueueWrite(outDir.resolve("runtime.json"),        () -> buildRuntime(server), errors);
        enqueueWrite(outDir.resolve("handlers.json"),       DiagnosticCommand::buildHandlers, errors);
        enqueueWrite(outDir.resolve("persistence.json"),    () -> buildPersistence(server), errors);
        enqueueWrite(outDir.resolve("audit_tail.json"),     DiagnosticCommand::buildAuditTail, errors);
        enqueueWrite(outDir.resolve("type_coherence.json"), () -> buildTypeCoherence(server), errors);
        enqueueWrite(outDir.resolve("benchmark.json"),      () -> buildBenchmark(server), errors);

        // ── Phase 3 : message final
        TestSetupCommand.enqueue(() -> {
            running = false;
            long elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000L;
            if (!errors.isEmpty()) {
                src.sendFailure(Component.literal("❌ Diagnostic incomplet (" + errors.size()
                    + " erreur(s)) — dossier : " + outDir));
                for (String err : errors) {
                    src.sendFailure(Component.literal("  • " + err));
                }
                return;
            }
            src.sendSuccess(() -> Component.literal(
                "✅ Diagnostic OK (" + elapsedSec + " s) — zippe et envoie : " + outDir)
                .withStyle(ChatFormatting.GREEN), true);
        });

        running = true;
        src.sendSuccess(() -> Component.literal(
            "⏳ Diagnostic lancé — testsetup (" + dimCount + " dims × 100 zones) + "
            + "11 fichiers JSON, exécution throttlée. Tu seras notifié à la fin.")
            .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** Enqueue a JSON write task with lazy payload build (runs on a future tick). */
    private static void enqueueWrite(Path file, java.util.function.Supplier<JsonObject> builder, List<String> errors) {
        TestSetupCommand.enqueue(() -> {
            try {
                JsonObject payload = builder.get();
                Files.writeString(file, GSON.toJson(payload));
            } catch (Exception e) {
                ArcadiaGuard.LOGGER.error("[ArcadiaGuard] diag file {} failed", file.getFileName(), e);
                errors.add(file.getFileName() + " : " + e.getClass().getSimpleName() + " — " + e.getMessage());
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────
    // Builders
    // ────────────────────────────────────────────────────────────────────────

    private static JsonObject buildSummary(MinecraftServer server, Path outDir, int dimCount, long startNs) {
        JsonObject root = new JsonObject();
        root.addProperty("generated_at", LocalDateTime.now().toString());
        root.addProperty("output_dir", outDir.toString());
        root.addProperty("mod_id", ArcadiaGuard.MOD_ID);
        ModList.get().getModContainerById(ArcadiaGuard.MOD_ID).ifPresent(c ->
            root.addProperty("mod_version", c.getModInfo().getVersion().toString()));

        JsonObject srv = new JsonObject();
        srv.addProperty("server_version", server.getServerVersion());
        srv.addProperty("motd", server.getMotd());
        srv.addProperty("player_count", server.getPlayerCount());
        srv.addProperty("max_players", server.getMaxPlayers());
        srv.addProperty("dedicated", server.isDedicatedServer());
        srv.addProperty("tick_count", server.getTickCount());
        JsonArray dims = new JsonArray();
        int totalZones = 0;
        JsonObject zoneByDim = new JsonObject();
        for (ServerLevel lvl : server.getAllLevels()) {
            String key = lvl.dimension().location().toString();
            dims.add(key);
            int n = ArcadiaGuard.zoneManager().zones(lvl).size();
            zoneByDim.addProperty(key, n);
            totalZones += n;
        }
        srv.add("dimensions", dims);
        root.add("server", srv);
        zoneByDim.addProperty("_total", totalZones);
        root.add("zone_counts", zoneByDim);
        root.addProperty("dim_count_diagnosed", dimCount);

        // Flags stats
        JsonObject flagStats = new JsonObject();
        int flagTotal = 0, flagBool = 0, flagInt = 0, flagList = 0;
        for (Flag<?> f : ArcadiaGuard.flagRegistry().all()) {
            flagTotal++;
            if (f instanceof BooleanFlag) flagBool++;
            else if (f instanceof IntFlag) flagInt++;
            else if (f instanceof ListFlag) flagList++;
        }
        flagStats.addProperty("total", flagTotal);
        flagStats.addProperty("boolean", flagBool);
        flagStats.addProperty("int", flagInt);
        flagStats.addProperty("list", flagList);
        root.add("flag_registry", flagStats);

        // Config
        JsonObject cfg = new JsonObject();
        cfg.addProperty("bypass_op_level", ArcadiaGuardConfig.BYPASS_OP_LEVEL.get());
        cfg.addProperty("enable_logging", ArcadiaGuardConfig.ENABLE_LOGGING.get());
        cfg.addProperty("async_writer_policy", ArcadiaGuardConfig.ASYNC_WRITER_POLICY.get());
        JsonArray disabledFreqs = new JsonArray();
        for (String s : ArcadiaGuardConfig.DISABLED_FLAG_FREQUENCIES.get()) disabledFreqs.add(s);
        cfg.add("disabled_flag_frequencies", disabledFreqs);
        root.add("config", cfg);

        // LuckPerms
        JsonObject lp = new JsonObject();
        lp.addProperty("available", LuckPermsCompat.isAvailable());
        lp.addProperty("checker_initialized", LuckPermsCompat.checker() != null);
        root.add("luckperms", lp);

        root.addProperty("diagnostic_elapsed_seconds_at_summary_write",
            (System.nanoTime() - startNs) / 1_000_000_000L);
        return root;
    }

    private static JsonObject buildFlags() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Flag<?> f : ArcadiaGuard.flagRegistry().all()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id());
            o.addProperty("type", typeOf(f));
            o.addProperty("default", String.valueOf(f.defaultValue()));
            o.addProperty("description", f.description());
            o.addProperty("required_mod", f.requiredMod());
            o.addProperty("frequency", f.frequency().name());
            o.addProperty("required_mod_loaded",
                f.requiredMod().isEmpty() || ModList.get().isLoaded(f.requiredMod()));
            arr.add(o);
        }
        root.add("flags", arr);
        root.addProperty("count", arr.size());
        return root;
    }

    private static JsonObject buildZones(MinecraftServer server) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (ServerLevel lvl : server.getAllLevels()) {
            String dim = lvl.dimension().location().toString();
            for (var iz : ArcadiaGuard.zoneManager().zones(lvl)) {
                ProtectedZone z = (ProtectedZone) iz;
                JsonObject o = new JsonObject();
                o.addProperty("name", z.name());
                o.addProperty("dimension", dim);
                o.addProperty("enabled", z.enabled());
                o.addProperty("inherit_dim_flags", z.inheritDimFlags());
                o.addProperty("parent", z.parent());
                JsonObject min = new JsonObject();
                min.addProperty("x", z.minX()); min.addProperty("y", z.minY()); min.addProperty("z", z.minZ());
                JsonObject max = new JsonObject();
                max.addProperty("x", z.maxX()); max.addProperty("y", z.maxY()); max.addProperty("z", z.maxZ());
                o.add("min", min); o.add("max", max);
                long vol = (long)(z.maxX()-z.minX()+1)*(z.maxY()-z.minY()+1)*(z.maxZ()-z.minZ()+1);
                o.addProperty("volume_blocks", vol);
                JsonObject fo = new JsonObject();
                for (Map.Entry<String, Object> e : z.flagValues().entrySet()) {
                    fo.addProperty(e.getKey(), String.valueOf(e.getValue()));
                }
                o.add("flag_overrides", fo);
                JsonArray members = new JsonArray();
                z.whitelistedPlayers().forEach(u -> members.add(u.toString()));
                o.add("whitelisted_players", members);
                JsonArray blocked = new JsonArray();
                z.blockedItems().forEach(id -> blocked.add(id.toString()));
                o.add("blocked_items", blocked);
                arr.add(o);
            }
        }
        root.add("zones", arr);
        root.addProperty("total", arr.size());
        return root;
    }

    private static JsonObject buildIntegrity(MinecraftServer server) {
        JsonObject root = new JsonObject();
        JsonArray orphanParents = new JsonArray();
        JsonArray unknownFlags = new JsonArray();
        JsonArray invertedBounds = new JsonArray();
        JsonArray overlapping = new JsonArray();
        var registry = ArcadiaGuard.flagRegistry();

        for (ServerLevel lvl : server.getAllLevels()) {
            String dim = lvl.dimension().location().toString();
            List<ProtectedZone> zones = new ArrayList<>();
            for (var iz : ArcadiaGuard.zoneManager().zones(lvl)) zones.add((ProtectedZone) iz);
            Map<String, ProtectedZone> byName = new HashMap<>();
            for (ProtectedZone z : zones) byName.put(z.name().toLowerCase(Locale.ROOT), z);

            for (ProtectedZone z : zones) {
                if (z.parent() != null && !z.parent().isEmpty()
                        && !byName.containsKey(z.parent().toLowerCase(Locale.ROOT))) {
                    JsonObject issue = new JsonObject();
                    issue.addProperty("zone", z.name());
                    issue.addProperty("dimension", dim);
                    issue.addProperty("missing_parent", z.parent());
                    orphanParents.add(issue);
                }
                for (String flagId : z.flagValues().keySet()) {
                    if (registry.get(flagId).isEmpty()) {
                        JsonObject issue = new JsonObject();
                        issue.addProperty("zone", z.name());
                        issue.addProperty("dimension", dim);
                        issue.addProperty("unknown_flag", flagId);
                        unknownFlags.add(issue);
                    }
                }
                if (z.minX() > z.maxX() || z.minY() > z.maxY() || z.minZ() > z.maxZ()) {
                    JsonObject issue = new JsonObject();
                    issue.addProperty("zone", z.name());
                    issue.addProperty("dimension", dim);
                    invertedBounds.add(issue);
                }
            }

            int n = zones.size();
            if (n <= 500) {
                for (int i = 0; i < n; i++) {
                    ProtectedZone a = zones.get(i);
                    for (int j = i + 1; j < n; j++) {
                        ProtectedZone b = zones.get(j);
                        if (isParentOf(a, b) || isParentOf(b, a)) continue;
                        if (boxesOverlap(a, b)) {
                            JsonObject issue = new JsonObject();
                            issue.addProperty("dimension", dim);
                            issue.addProperty("a", a.name());
                            issue.addProperty("b", b.name());
                            overlapping.add(issue);
                        }
                    }
                }
            }
        }
        root.add("orphan_parents", orphanParents);
        root.add("unknown_flag_ids", unknownFlags);
        root.add("inverted_bounds", invertedBounds);
        root.add("overlapping_unrelated", overlapping);
        root.addProperty("total_issues",
            orphanParents.size() + unknownFlags.size() + invertedBounds.size() + overlapping.size());
        return root;
    }

    @SuppressWarnings("unchecked")
    private static JsonObject buildPermissions(MinecraftServer server) {
        JsonObject root = new JsonObject();
        JsonArray players = new JsonArray();
        LuckPermsPermissionChecker lp = LuckPermsCompat.checker();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            JsonObject po = new JsonObject();
            GameProfile profile = p.getGameProfile();
            po.addProperty("uuid", profile.getId().toString());
            po.addProperty("name", profile.getName());

            JsonObject pos = new JsonObject();
            BlockPos bp = p.blockPosition();
            pos.addProperty("x", bp.getX()); pos.addProperty("y", bp.getY()); pos.addProperty("z", bp.getZ());
            pos.addProperty("dim", p.serverLevel().dimension().location().toString());
            po.add("position", pos);

            int opLevel = server.getProfilePermissions(profile);
            po.addProperty("op_level", opLevel);
            po.addProperty("op_threshold", ArcadiaGuardConfig.BYPASS_OP_LEVEL.get());
            po.addProperty("op_grants_bypass", opLevel >= ArcadiaGuardConfig.BYPASS_OP_LEVEL.get());
            po.addProperty("effective_bypass", ArcadiaGuard.guardService().shouldBypass(p));
            po.addProperty("debug_mode", ArcadiaGuard.guardService().isDebugMode(profile.getId()));

            // Zone(s) qui contiennent le joueur
            JsonArray containing = new JsonArray();
            Optional<com.arcadia.arcadiaguard.api.zone.IZone> zo =
                ArcadiaGuard.zoneManager().findZoneContaining(p.serverLevel(), bp);
            zo.ifPresent(iz -> {
                ProtectedZone z = (ProtectedZone) iz;
                JsonObject zi = new JsonObject();
                zi.addProperty("name", z.name());
                zi.addProperty("parent", z.parent());
                containing.add(zi);
            });
            po.add("containing_zone", containing);

            // Flags effectifs a la position du joueur (pour tous les BooleanFlag connus)
            JsonObject effective = new JsonObject();
            if (zo.isPresent()) {
                ProtectedZone z = (ProtectedZone) zo.get();
                Function<String, Optional<ProtectedZone>> lookup = n ->
                    (Optional<ProtectedZone>)(Optional<?>) ArcadiaGuard.zoneManager().get(p.serverLevel(), n);
                for (Flag<?> f : ArcadiaGuard.flagRegistry().all()) {
                    if (f instanceof BooleanFlag bf) {
                        try {
                            boolean v = FlagResolver.resolve(z, bf, lookup);
                            effective.addProperty(f.id(), v);
                        } catch (Exception ignored) {}
                    }
                }
            }
            po.add("effective_boolean_flags", effective);

            // Items en main (utile pour tests items bloques)
            JsonObject hands = new JsonObject();
            hands.addProperty("main", p.getMainHandItem().getItem().toString());
            hands.addProperty("off", p.getOffhandItem().getItem().toString());
            po.add("hands", hands);

            // LuckPerms details
            JsonObject lpInfo = new JsonObject();
            lpInfo.addProperty("available", lp != null);
            if (lp != null) {
                lpInfo.addProperty("has_bypass", lp.hasBypass(p));
                lpInfo.addProperty("has_view", lp.hasViewAccess(p));
                JsonObject roles = new JsonObject();
                for (ServerLevel lvl : server.getAllLevels()) {
                    for (var iz : ArcadiaGuard.zoneManager().zones(lvl)) {
                        String role = lp.resolveRole(p, iz.name());
                        if (role != null) {
                            roles.addProperty(lvl.dimension().location() + ":" + iz.name(), role);
                        }
                    }
                }
                lpInfo.add("zone_roles", roles);
            }
            po.add("luckperms", lpInfo);

            players.add(po);
        }
        root.add("players", players);
        root.addProperty("count", players.size());
        return root;
    }

    private static JsonObject buildRuntime(MinecraftServer server) {
        JsonObject root = new JsonObject();
        Runtime rt = Runtime.getRuntime();
        JsonObject jvm = new JsonObject();
        jvm.addProperty("java_version", System.getProperty("java.version"));
        jvm.addProperty("java_vendor", System.getProperty("java.vendor"));
        jvm.addProperty("jvm_name", System.getProperty("java.vm.name"));
        jvm.addProperty("os_name", System.getProperty("os.name"));
        jvm.addProperty("os_arch", System.getProperty("os.arch"));
        jvm.addProperty("os_version", System.getProperty("os.version"));
        jvm.addProperty("cpu_cores", rt.availableProcessors());
        root.add("jvm", jvm);

        JsonObject mem = new JsonObject();
        mem.addProperty("heap_total_mb", rt.totalMemory() / (1024 * 1024));
        mem.addProperty("heap_free_mb", rt.freeMemory() / (1024 * 1024));
        mem.addProperty("heap_used_mb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        mem.addProperty("heap_max_mb", rt.maxMemory() / (1024 * 1024));
        root.add("memory", mem);

        JsonObject tick = new JsonObject();
        tick.addProperty("tick_count", server.getTickCount());
        tick.addProperty("avg_tick_ms_overall",
            server.getAverageTickTimeNanos() / 1_000_000.0);
        root.add("ticks", tick);

        // Liste COMPLETE des mods
        JsonArray mods = new JsonArray();
        ModList.get().getMods().forEach(m -> {
            JsonObject mo = new JsonObject();
            mo.addProperty("id", m.getModId());
            mo.addProperty("name", m.getDisplayName());
            mo.addProperty("version", m.getVersion().toString());
            mods.add(mo);
        });
        root.add("loaded_mods", mods);
        root.addProperty("loaded_mods_count", mods.size());
        return root;
    }

    private static JsonObject buildHandlers() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        // Introspection de HandlerRegistry.handlers (champ prive) via reflection.
        try {
            Object registry = ArcadiaGuard.services().handlerRegistry();
            var field = registry.getClass().getDeclaredField("handlers");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Object> handlers = (List<Object>) field.get(registry);
            for (Object h : handlers) {
                JsonObject o = new JsonObject();
                o.addProperty("class", h.getClass().getSimpleName());
                o.addProperty("fqn", h.getClass().getName());
                // Best-effort : detecter un eventuel mod requis via le nom
                String fqn = h.getClass().getSimpleName().toLowerCase(Locale.ROOT);
                String modGuess = "";
                for (String m : new String[]{"ars","irons","simply","occult","supplement","apotheos",
                        "betterarch","carryon","parcool","waystone","emotecraft","rechisel",
                        "sophisticated","twilight","mutant"}) {
                    if (fqn.contains(m)) { modGuess = m; break; }
                }
                o.addProperty("guessed_compat_keyword", modGuess);
                arr.add(o);
            }
        } catch (Exception e) {
            root.addProperty("introspection_error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        root.add("handlers", arr);
        root.addProperty("count", arr.size());
        return root;
    }

    private static JsonObject buildPersistence(MinecraftServer server) {
        JsonObject root = new JsonObject();
        JsonArray missingOnDisk = new JsonArray();
        JsonArray parseFailures = new JsonArray();
        JsonArray orphanFiles = new JsonArray();

        // 1) Memoire -> disque
        int checked = 0;
        for (ServerLevel lvl : server.getAllLevels()) {
            String dim = lvl.dimension().location().toString();
            for (var iz : ArcadiaGuard.zoneManager().zones(lvl)) {
                checked++;
                try {
                    Path f = ArcadiaGuardPaths.zoneFile(dim, iz.name());
                    if (!Files.exists(f)) {
                        JsonObject o = new JsonObject();
                        o.addProperty("zone", iz.name());
                        o.addProperty("dimension", dim);
                        o.addProperty("expected_path", f.toString());
                        missingOnDisk.add(o);
                    } else {
                        try { Files.readString(f); } catch (Exception e) {
                            JsonObject o = new JsonObject();
                            o.addProperty("zone", iz.name());
                            o.addProperty("dimension", dim);
                            o.addProperty("error", e.getMessage());
                            parseFailures.add(o);
                        }
                    }
                } catch (Exception e) {
                    // nom invalide ou autre — deja couvert par integrity
                }
            }
        }

        // 2) Disque -> memoire (orphelins)
        Path zonesRoot = ArcadiaGuardPaths.zonesRoot();
        if (Files.isDirectory(zonesRoot)) {
            try (var dimStream = Files.list(zonesRoot)) {
                dimStream.filter(Files::isDirectory).forEach(dimDir -> {
                    try (var fileStream = Files.list(dimDir)) {
                        fileStream.filter(p -> p.toString().endsWith(".json")).forEach(file -> {
                            String fileName = file.getFileName().toString();
                            String zoneName = fileName.substring(0, fileName.length() - 5);
                            String dimKeyEnc = dimDir.getFileName().toString(); // ex: minecraft-overworld
                            String dimKey = dimKeyEnc.replace('-', ':');        // heuristic reverse
                            ServerLevel match = null;
                            for (ServerLevel lvl : server.getAllLevels()) {
                                String lvlDim = lvl.dimension().location().toString();
                                if (ArcadiaGuardPaths.sanitizeDimKey(lvlDim).equalsIgnoreCase(dimKeyEnc)) {
                                    match = lvl; break;
                                }
                            }
                            boolean found = match != null
                                && ArcadiaGuard.zoneManager().get(match, zoneName).isPresent();
                            if (!found) {
                                JsonObject o = new JsonObject();
                                o.addProperty("file", file.toString());
                                o.addProperty("zone", zoneName);
                                o.addProperty("dim_dir", dimKeyEnc);
                                o.addProperty("dim_resolved", dimKey);
                                orphanFiles.add(o);
                            }
                        });
                    } catch (IOException ignored) {}
                });
            } catch (IOException e) {
                root.addProperty("scan_error", e.getMessage());
            }
        } else {
            root.addProperty("zones_root_missing", zonesRoot.toString());
        }

        root.addProperty("memory_zones_checked", checked);
        root.add("missing_on_disk", missingOnDisk);
        root.add("parse_failures", parseFailures);
        root.add("orphan_files_on_disk", orphanFiles);

        // Async writer stats
        try {
            root.addProperty("async_writer_stats", ArcadiaGuard.asyncZoneWriter().stats());
        } catch (Exception e) {
            root.addProperty("async_writer_stats_error", e.getMessage());
        }

        // Taille de dimension-flags.json + blocked-items.json
        JsonObject sidecars = new JsonObject();
        addFileInfo(sidecars, "dimension_flags", ArcadiaGuardPaths.dimFlagsFile());
        addFileInfo(sidecars, "blocked_items",   ArcadiaGuardPaths.blockedItemsFile());
        root.add("sidecar_files", sidecars);

        int total = missingOnDisk.size() + parseFailures.size() + orphanFiles.size();
        root.addProperty("total_issues", total);
        return root;
    }

    private static JsonObject buildAuditTail() {
        JsonObject root = new JsonObject();
        Path auditFile = ArcadiaGuardPaths.logsRoot().resolve("arcadiaguard-audit.log");
        root.addProperty("audit_file", auditFile.toString());
        if (!Files.exists(auditFile)) {
            root.addProperty("exists", false);
            root.add("lines", new JsonArray());
            return root;
        }
        root.addProperty("exists", true);
        try {
            List<String> all = Files.readAllLines(auditFile);
            int from = Math.max(0, all.size() - AUDIT_TAIL_LINES);
            JsonArray lines = new JsonArray();
            for (int i = from; i < all.size(); i++) lines.add(all.get(i));
            root.add("lines", lines);
            root.addProperty("total_lines", all.size());
            root.addProperty("returned_lines", lines.size());
        } catch (IOException e) {
            root.addProperty("error", e.getMessage());
        }
        return root;
    }

    private static JsonObject buildTypeCoherence(MinecraftServer server) {
        JsonObject root = new JsonObject();
        JsonArray mismatches = new JsonArray();
        var registry = ArcadiaGuard.flagRegistry();
        int checked = 0;
        for (ServerLevel lvl : server.getAllLevels()) {
            String dim = lvl.dimension().location().toString();
            for (var iz : ArcadiaGuard.zoneManager().zones(lvl)) {
                ProtectedZone z = (ProtectedZone) iz;
                for (Map.Entry<String, Object> e : z.flagValues().entrySet()) {
                    checked++;
                    Optional<Flag<?>> opt = registry.get(e.getKey());
                    if (opt.isEmpty()) continue; // couvert par integrity
                    Flag<?> f = opt.get();
                    Object v = e.getValue();
                    boolean ok;
                    String expectedType;
                    if (f instanceof BooleanFlag) { expectedType = "Boolean"; ok = v instanceof Boolean; }
                    else if (f instanceof IntFlag) { expectedType = "Integer"; ok = v instanceof Integer; }
                    else if (f instanceof ListFlag) { expectedType = "List"; ok = v instanceof List<?>; }
                    else { expectedType = f.getClass().getSimpleName(); ok = true; }
                    if (!ok) {
                        JsonObject m = new JsonObject();
                        m.addProperty("zone", z.name());
                        m.addProperty("dimension", dim);
                        m.addProperty("flag_id", e.getKey());
                        m.addProperty("expected_type", expectedType);
                        m.addProperty("actual_type", v == null ? "null" : v.getClass().getSimpleName());
                        m.addProperty("actual_value", String.valueOf(v));
                        mismatches.add(m);
                    }
                }
            }
        }
        root.addProperty("overrides_checked", checked);
        root.add("mismatches", mismatches);
        root.addProperty("total_issues", mismatches.size());
        return root;
    }

    @SuppressWarnings("unchecked")
    private static JsonObject buildBenchmark(MinecraftServer server) {
        JsonObject root = new JsonObject();
        JsonArray perPlayer = new JsonArray();
        Map<String, BooleanFlag> boolFlags = new LinkedHashMap<>();
        for (Flag<?> f : ArcadiaGuard.flagRegistry().all()) {
            if (f instanceof BooleanFlag bf) { boolFlags.put(f.id(), bf); }
        }
        BooleanFlag blockBreak = boolFlags.entrySet().stream()
            .filter(e -> e.getKey().equals("block-break")).findFirst()
            .map(Map.Entry::getValue).orElse(boolFlags.values().stream().findFirst().orElse(null));

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            JsonObject po = new JsonObject();
            po.addProperty("name", p.getGameProfile().getName());

            // Bench 1 : shouldBypass
            long[] bypassNs = new long[BENCH_ITERATIONS];
            for (int i = 0; i < BENCH_ITERATIONS; i++) {
                long t = System.nanoTime();
                ArcadiaGuard.guardService().shouldBypass(p);
                bypassNs[i] = System.nanoTime() - t;
            }
            po.add("shouldBypass_ns", percentiles(bypassNs));

            // Bench 2 : checkFlag (block-break) a la position du joueur
            if (blockBreak != null) {
                BlockPos pos = p.blockPosition();
                long[] checkNs = new long[BENCH_ITERATIONS];
                for (int i = 0; i < BENCH_ITERATIONS; i++) {
                    long t = System.nanoTime();
                    ArcadiaGuard.guardService().checkFlag(p, pos, blockBreak);
                    checkNs[i] = System.nanoTime() - t;
                }
                po.add("checkFlag_block_break_ns", percentiles(checkNs));
            }
            perPlayer.add(po);
        }
        root.add("per_player", perPlayer);
        root.addProperty("iterations_per_scenario", BENCH_ITERATIONS);
        root.addProperty("bypass_ttl_ms_note", "first call computes, TTL rearms within 5s window — results include cache hits");
        return root;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JsonObject percentiles(long[] samples) {
        long[] sorted = samples.clone();
        java.util.Arrays.sort(sorted);
        long p50 = sorted[sorted.length / 2];
        long p95 = sorted[(int) (sorted.length * 0.95)];
        long p99 = sorted[(int) (sorted.length * 0.99)];
        long max = sorted[sorted.length - 1];
        long sum = 0; for (long l : sorted) sum += l;
        JsonObject o = new JsonObject();
        o.addProperty("count", sorted.length);
        o.addProperty("p50", p50);
        o.addProperty("p95", p95);
        o.addProperty("p99", p99);
        o.addProperty("max", max);
        o.addProperty("avg", sum / sorted.length);
        return o;
    }

    private static void addFileInfo(JsonObject target, String key, Path file) {
        JsonObject o = new JsonObject();
        o.addProperty("path", file.toString());
        o.addProperty("exists", Files.exists(file));
        if (Files.exists(file)) {
            try { o.addProperty("size_bytes", Files.size(file)); }
            catch (IOException e) { o.addProperty("size_error", e.getMessage()); }
        }
        target.add(key, o);
    }

    private static String typeOf(Flag<?> f) {
        if (f instanceof BooleanFlag) return "boolean";
        if (f instanceof IntFlag)     return "int";
        if (f instanceof ListFlag)    return "list";
        return f.getClass().getSimpleName();
    }

    private static boolean isParentOf(ProtectedZone ancestor, ProtectedZone descendant) {
        if (descendant.parent() == null) return false;
        return descendant.parent().equalsIgnoreCase(ancestor.name());
    }

    private static boolean boxesOverlap(ProtectedZone a, ProtectedZone b) {
        if (!a.dimension().equals(b.dimension())) return false;
        return a.minX() <= b.maxX() && a.maxX() >= b.minX()
            && a.minY() <= b.maxY() && a.maxY() >= b.minY()
            && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
    }
}

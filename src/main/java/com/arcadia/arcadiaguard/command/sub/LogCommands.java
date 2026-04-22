package com.arcadia.arcadiaguard.command.sub;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class LogCommands {

    private static final int PAGE_SIZE = 10;

    private LogCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("log")
            .executes(ctx -> showLog(ctx, 1, "", ""))
            .then(argument("page", IntegerArgumentType.integer(1))
                .executes(ctx -> showLog(ctx, IntegerArgumentType.getInteger(ctx, "page"), "", "")))
            .then(literal("zone")
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> showLog(ctx, 1, StringArgumentType.getString(ctx, "name"), ""))))
            .then(literal("player")
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> showLog(ctx, 1, "", StringArgumentType.getString(ctx, "name")))));
    }

    private static int showLog(CommandContext<CommandSourceStack> ctx, int page, String zoneFilter, String playerFilter) {
        Path logsDir = ArcadiaGuardPaths.logsRoot();
        try {
            List<String> entries = readRecentEntries(logsDir, zoneFilter, playerFilter);
            if (entries.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.log.empty"), false);
                return 1;
            }
            int totalPages = (int) Math.ceil((double) entries.size() / PAGE_SIZE);
            int clampedPage = Math.min(page, totalPages);
            int from = (clampedPage - 1) * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, entries.size());
            ctx.getSource().sendSuccess(() -> Component.literal(
                "=== ArcadiaGuard Log (page " + clampedPage + "/" + totalPages + ") ==="), false);
            for (int i = from; i < to; i++) {
                String line = entries.get(i);
                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            }
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Log read error", e);
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.log.read_error"));
            return 0;
        }
        return 1;
    }

    private static List<String> readRecentEntries(Path logsDir, String zoneFilter, String playerFilter) throws IOException {
        if (!Files.exists(logsDir)) return List.of();
        List<String> results = new ArrayList<>();
        Path current = logsDir.resolve("arcadiaguard-audit.log");
        if (Files.exists(current)) {
            collectFromFile(current, zoneFilter, playerFilter, results);
        }
        try (var stream = Files.list(logsDir)) {
            stream.filter(p -> {
                    String fn = p.getFileName().toString();
                    return fn.startsWith("arcadiaguard-audit-") && fn.endsWith(".log");
                })
                .sorted(Collections.reverseOrder())
                .limit(6)
                .forEach(p -> {
                    try { collectFromFile(p, zoneFilter, playerFilter, results); }
                    catch (IOException e) {
                        com.arcadia.arcadiaguard.ArcadiaGuard.LOGGER.warn(
                            "[ArcadiaGuard] Failed to read audit log file {}: {}", p, e.toString());
                    }
                });
        }
        return results;
    }

    /** M7: use streaming + bounded Deque to avoid loading the entire file into memory. */
    private static void collectFromFile(Path file, String zoneFilter, String playerFilter, List<String> out) throws IOException {
        final int MAX_LINES = 5000; // guard against very large log files
        java.util.Deque<String> deque = new java.util.ArrayDeque<>(MAX_LINES);
        try (var stream = Files.lines(file, StandardCharsets.UTF_8)) {
            stream.forEach(line -> {
                if (!zoneFilter.isEmpty() && !line.contains("zone=" + zoneFilter)) return;
                if (!playerFilter.isEmpty() && !line.contains("player=" + playerFilter)) return;
                if (deque.size() == MAX_LINES) deque.pollFirst();
                deque.offerLast(line);
            });
        }
        out.addAll(deque);
    }
}

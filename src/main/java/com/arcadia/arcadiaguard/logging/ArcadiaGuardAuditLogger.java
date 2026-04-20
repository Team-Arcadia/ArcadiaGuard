package com.arcadia.arcadiaguard.logging;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * Writes audit entries on a dedicated background thread so the server tick
 * loop is never blocked by disk I/O. Log lines are still printed to the
 * main logger synchronously (very cheap), but file writes + rotation run
 * on the worker thread.
 *
 * <p>The BufferedWriter is kept open across writes and only re-opened on date
 * rotation, avoiding the per-line FD open/close cost (C9).
 *
 * <p>Dropped entries are counted and periodically warned (H12).
 */
public final class ArcadiaGuardAuditLogger {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LOG_FILE_NAME = "arcadiaguard-audit.log";
    private static final int QUEUE_CAPACITY = 4096;
    /** Warn about dropped entries at most every N writes (approx). */
    private static final int DROPPED_WARN_INTERVAL = 512;

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private volatile Thread worker;
    private volatile Path logsDir;
    private volatile LocalDate activeDate;

    /** Kept open between writes; only replaced on rotation or after init. */
    private BufferedWriter openWriter;

    public void onServerStarted(MinecraftServer server) {
        this.logsDir = ArcadiaGuardPaths.logsRoot();
        this.activeDate = LocalDate.now(ZoneOffset.UTC);
        if (running.compareAndSet(false, true)) {
            this.worker = new Thread(this::runLoop, "ArcadiaGuard-AuditLogger");
            this.worker.setDaemon(true);
            this.worker.start();
        }
    }

    public void onServerStopped() {
        if (!running.compareAndSet(true, false)) return;
        Thread t = this.worker;
        if (t != null) {
            t.interrupt();
            try { t.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        this.worker = null;
        this.logsDir = null;
        this.activeDate = null;
        this.queue.clear();
        closeWriter();
    }

    public void logBlockedAction(String playerName, String actionName, String zoneName, BlockPos pos) {
        // M1: test ENABLE_LOGGING before String.format
        if (ArcadiaGuardConfig.ENABLE_LOGGING.get()) {
            String line = String.format(
                "[ArcadiaGuard] player=%s action=%s zone=%s pos=%d %d %d",
                playerName,
                actionName,
                zoneName,
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ()
            );
            ArcadiaGuard.LOGGER.info(line);
        }
        if (!ArcadiaGuardConfig.LOG_TO_FILE.get() || this.logsDir == null) return;

        String line = String.format(
            "[ArcadiaGuard] player=%s action=%s zone=%s pos=%d %d %d",
            playerName,
            actionName,
            zoneName,
            pos == null ? 0 : pos.getX(),
            pos == null ? 0 : pos.getY(),
            pos == null ? 0 : pos.getZ()
        );
        String payload = "[" + TS.format(LocalDateTime.now(ZoneOffset.UTC)) + "] " + line + System.lineSeparator();
        // H12: count drops silently; log warning in background thread
        if (!queue.offer(payload)) {
            droppedCount.incrementAndGet();
        }
    }

    private void runLoop() {
        long writeCount = 0;
        while (running.get() || !queue.isEmpty()) {
            try {
                String payload = queue.poll(500, TimeUnit.MILLISECONDS);
                if (payload == null) continue;
                writeLine(payload);
                writeCount++;
                // H12: warn about drops periodically
                if (writeCount % DROPPED_WARN_INTERVAL == 0) {
                    long dropped = droppedCount.getAndSet(0);
                    if (dropped > 0) {
                        ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Dropped {} audit entries due to queue backpressure", dropped);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Drain remaining entries before exiting.
                String rest;
                while ((rest = queue.poll()) != null) writeLine(rest);
                break;
            }
        }
        // Final dropped-count flush on shutdown
        long remaining = droppedCount.getAndSet(0);
        if (remaining > 0) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Dropped {} audit entries due to queue backpressure (shutdown)", remaining);
        }
        closeWriter();
    }

    /** C9: write to persistent BufferedWriter, creating dirs once, rotating on date change. */
    private void writeLine(String payload) {
        Path dir = this.logsDir;
        if (dir == null) return;
        try {
            // createDirectories is idempotent but cheap — skip if writer already open
            if (openWriter == null) {
                Files.createDirectories(dir);
            }
            rotateIfNeeded(dir);
            if (openWriter == null) {
                openWriter = Files.newBufferedWriter(
                    dir.resolve(LOG_FILE_NAME), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            if (openWriter == null) return;
            openWriter.write(payload);
            openWriter.flush();
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.warn("Failed to write ArcadiaGuard audit log", e);
            closeWriter(); // force re-open on next write
        }
    }

    private void closeWriter() {
        BufferedWriter w = openWriter;
        openWriter = null;
        if (w != null) {
            try { w.close(); } catch (IOException ignored) {}
        }
    }

    /** Entrée de log parsée. Utilisée par le GUI. */
    public record LogEntry(String timestamp, String player, String action, String zone, String pos) {}

    private static final java.util.regex.Pattern LOG_LINE = java.util.regex.Pattern.compile(
        "\\[(?<ts>[^\\]]+)\\] \\[ArcadiaGuard\\] player=(?<player>\\S+) action=(?<action>\\S+) zone=(?<zone>\\S+) pos=(?<pos>\\S+ \\S+ \\S+)");

    /**
     * Lit les entrées récentes depuis le fichier de log courant (+ hier si besoin)
     * en ne gardant que celles qui matchent {@code zoneFilter} (obligatoire) et
     * {@code playerFilter}/{@code actionFilter} (optionnels, case-insensitive contains).
     * Retourne au plus {@code limit} entrées, les plus récentes en premier.
     *
     * <p>Utilise {@link Files#lines} + ArrayDeque borné pour ne jamais charger
     * l'intégralité du fichier en mémoire (C4). Doit être appelé depuis un thread
     * async (ex. ForkJoinPool) pour ne pas bloquer le thread principal.
     */
    public List<LogEntry> tail(String zoneFilter, String playerFilter,
                               String actionFilter, int limit) {
        Path dir = this.logsDir;
        if (dir == null) return List.of();
        // Fichier courant + éventuellement archive d'hier
        Path[] candidates = {
            dir.resolve(LOG_FILE_NAME),
            dir.resolve("arcadiaguard-audit-" + LocalDate.now(ZoneOffset.UTC).minusDays(1) + ".log")
        };
        // Collect matching entries via streaming — bounded deque avoids full-file load
        ArrayDeque<LogEntry> out = new ArrayDeque<>(limit);
        String pf = (playerFilter != null) ? playerFilter.toLowerCase(Locale.ROOT) : null;
        String af = (actionFilter != null) ? actionFilter.toLowerCase(Locale.ROOT) : null;
        for (Path file : candidates) {
            if (!Files.isRegularFile(file)) continue;
            try (var stream = Files.lines(file, StandardCharsets.UTF_8)) {
                stream.forEach(line -> {
                    if (out.size() >= limit * 2) return; // soft cap to avoid huge intermediate deque
                    LogEntry e = parseLine(line);
                    if (e == null) return;
                    if (zoneFilter != null && !zoneFilter.isEmpty()
                        && !e.zone().equalsIgnoreCase(zoneFilter)) return;
                    if (pf != null && !pf.isBlank()
                        && !e.player().toLowerCase(Locale.ROOT).contains(pf)) return;
                    if (af != null && !af.isBlank()
                        && !e.action().toLowerCase(Locale.ROOT).contains(af)) return;
                    if (out.size() == limit) out.pollFirst();
                    out.offerLast(e);
                });
            } catch (IOException e) {
                ArcadiaGuard.LOGGER.warn("Failed to read ArcadiaGuard audit log for tail()", e);
            }
            if (out.size() >= limit) break;
        }
        return new ArrayList<>(out);
    }

    /**
     * Non-blocking variant of {@link #tail}: dispatches work on the common ForkJoinPool
     * and delivers results via {@code callback} on that pool's thread (C4).
     * The callback must marshal results to the main thread if it touches world state.
     */
    public CompletableFuture<Void> tailAsync(String zoneFilter, String playerFilter,
                                             String actionFilter, int limit,
                                             Consumer<List<LogEntry>> callback) {
        return CompletableFuture.runAsync(() -> callback.accept(
            tail(zoneFilter, playerFilter, actionFilter, limit)));
    }

    private static LogEntry parseLine(String line) {
        java.util.regex.Matcher m = LOG_LINE.matcher(line);
        if (!m.find()) return null;
        return new LogEntry(m.group("ts"), m.group("player"), m.group("action"),
            m.group("zone"), m.group("pos"));
    }

    private void rotateIfNeeded(Path dir) throws IOException {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        LocalDate active = this.activeDate;
        if (active == null) { this.activeDate = now; return; }
        if (active.equals(now)) return;
        // Date changed: close the open writer and rotate the file
        closeWriter();
        Path current = dir.resolve(LOG_FILE_NAME);
        if (Files.exists(current)) {
            Path archived = dir.resolve("arcadiaguard-audit-" + active + ".log");
            Files.move(current, archived, StandardCopyOption.REPLACE_EXISTING);
        }
        this.activeDate = now;
        // openWriter will be recreated by writeLine() after this
    }
}

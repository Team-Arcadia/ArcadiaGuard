package com.arcadia.arcadiaguard.logging;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Manages daily log file rotation and cleanup for the audit logger.
 * Creates a new file per day and deletes files older than the configured retention period.
 *
 * @deprecated Non utilisé en production — la rotation/lecture des logs passe par
 * {@link ArcadiaGuardAuditLogger}. La méthode {@link #queryLog(int, String, String)}
 * est <b>synchrone et bloquante</b> (charge le fichier entier en RAM via
 * {@link Files#readAllLines}) — ne JAMAIS appeler depuis le thread tick. À supprimer
 * dans une release future. Garde uniquement pour ses tests.
 */
@Deprecated
public final class LogFileManager {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Path logDir;
    private final int retentionDays;

    public LogFileManager(Path logDir, int retentionDays) {
        this.logDir = logDir;
        this.retentionDays = retentionDays;
    }

    /** Returns the path for today's log file. Creates parent directories if needed. */
    public Path todayFile() throws IOException {
        Files.createDirectories(logDir);
        return logDir.resolve("audit-" + LocalDate.now().format(DATE_FMT) + ".log");
    }

    /** Deletes log files older than {@code retentionDays}. */
    public void purgeOldLogs() {
        if (!Files.exists(logDir)) return;
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        try (Stream<Path> files = Files.list(logDir)) {
            files.filter(p -> p.getFileName().toString().startsWith("audit-")
                           && p.getFileName().toString().endsWith(".log"))
                 .forEach(p -> {
                     String datePart = p.getFileName().toString()
                         .replace("audit-", "").replace(".log", "");
                     try {
                         LocalDate fileDate = LocalDate.parse(datePart, DATE_FMT);
                         if (fileDate.isBefore(cutoff)) Files.deleteIfExists(p);
                     } catch (DateTimeParseException | IOException ignored) {}
                 });
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Failed to purge old logs", e);
        }
    }

    /**
     * Returns the last {@code n} entries from recent log files, filtered by
     * optional zone and player name (empty string = no filter).
     */
    public List<String> queryLog(int n, String zoneFilter, String playerFilter) throws IOException {
        List<String> results = new ArrayList<>();
        LocalDate day = LocalDate.now();
        for (int attempt = 0; attempt < 7 && results.size() < n; attempt++, day = day.minusDays(1)) {
            Path file = logDir.resolve("audit-" + day.format(DATE_FMT) + ".log");
            if (!Files.exists(file)) continue;
            List<String> lines = Files.readAllLines(file);
            for (int i = lines.size() - 1; i >= 0 && results.size() < n; i--) {
                String line = lines.get(i);
                if (!zoneFilter.isEmpty() && !line.contains(zoneFilter)) continue;
                if (!playerFilter.isEmpty() && !line.contains(playerFilter)) continue;
                results.add(line);
            }
        }
        Collections.reverse(results);
        return results;
    }
}

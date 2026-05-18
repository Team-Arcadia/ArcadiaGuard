package com.arcadia.arcadiaguard.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Couvre FR20 (partiel) : rotation journalière et nettoyage des fichiers de log.
 * Utilise un dossier temporaire pour isoler les tests du système de fichiers réel.
 */
class LogFileManagerTest {

    @TempDir
    Path tempDir;

    private LogFileManager manager;

    @BeforeEach
    void setUp() {
        manager = new LogFileManager(tempDir, 7);
    }

    // ── todayFile() ──────────────────────────────────────────────────────────

    @Test
    void todayFile_createsParentDirectory() throws IOException {
        Path sub = tempDir.resolve("nested/logs");
        LogFileManager nested = new LogFileManager(sub, 7);
        nested.todayFile();
        assertTrue(Files.isDirectory(sub), "Le répertoire parent doit être créé");
    }

    @Test
    void todayFile_nameContainsTodayDate() throws IOException {
        Path file = manager.todayFile();
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        assertTrue(file.getFileName().toString().contains(today),
            "Le fichier log doit contenir la date du jour");
    }

    @Test
    void todayFile_nameStartsWithAuditPrefix() throws IOException {
        Path file = manager.todayFile();
        assertTrue(file.getFileName().toString().startsWith("audit-"),
            "Le fichier log doit commencer par 'audit-'");
    }

    @Test
    void todayFile_nameEndsWithDotLog() throws IOException {
        Path file = manager.todayFile();
        assertTrue(file.getFileName().toString().endsWith(".log"),
            "Le fichier log doit se terminer par '.log'");
    }

    @Test
    void todayFile_idempotent_returnsSamePath() throws IOException {
        Path first = manager.todayFile();
        Path second = manager.todayFile();
        assertEquals(first, second, "todayFile() doit toujours retourner le même chemin pour la même journée");
    }

    // ── purgeOldLogs() ───────────────────────────────────────────────────────

    @Test
    void purgeOldLogs_deletesFilesOlderThanRetention() throws IOException {
        // Créer un fichier old (8 jours avant la date de rétention)
        LocalDate old = LocalDate.now().minusDays(8);
        Path oldFile = tempDir.resolve("audit-" + old.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".log");
        Files.writeString(oldFile, "old entry");

        manager.purgeOldLogs();

        assertFalse(Files.exists(oldFile), "Le fichier trop ancien doit être supprimé");
    }

    @Test
    void purgeOldLogs_keepsFilesWithinRetention() throws IOException {
        LocalDate recent = LocalDate.now().minusDays(3);
        Path recentFile = tempDir.resolve("audit-" + recent.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".log");
        Files.writeString(recentFile, "recent entry");

        manager.purgeOldLogs();

        assertTrue(Files.exists(recentFile), "Le fichier récent ne doit pas être supprimé");
    }

    @Test
    void purgeOldLogs_keepsTodayFile() throws IOException {
        Path today = manager.todayFile();
        Files.writeString(today, "today's entry");

        manager.purgeOldLogs();

        assertTrue(Files.exists(today), "Le fichier du jour ne doit pas être supprimé");
    }

    @Test
    void purgeOldLogs_noopOnEmptyDirectory() {
        // Ne doit pas lancer d'exception sur un dossier vide
        manager.purgeOldLogs();
    }

    @Test
    void purgeOldLogs_noopOnNonExistentDirectory() {
        LogFileManager absent = new LogFileManager(tempDir.resolve("absent"), 7);
        absent.purgeOldLogs(); // ne doit pas lancer
    }

    @Test
    void purgeOldLogs_ignoresNonAuditFiles() throws IOException {
        Path other = tempDir.resolve("other.log");
        Files.writeString(other, "unrelated");

        manager.purgeOldLogs();

        assertTrue(Files.exists(other), "Les fichiers non-audit ne doivent pas être supprimés");
    }

    @Test
    void purgeOldLogs_deletesMultipleOldFiles() throws IOException {
        for (int i = 8; i <= 15; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            Files.writeString(
                tempDir.resolve("audit-" + date.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".log"),
                "old");
        }

        manager.purgeOldLogs();

        long remaining = Files.list(tempDir)
            .filter(p -> p.getFileName().toString().startsWith("audit-"))
            .count();
        assertEquals(0, remaining, "Tous les fichiers trop anciens doivent être supprimés");
    }

    @Test
    void purgeOldLogs_retentionBoundary_exactlyAtLimit_kept() throws IOException {
        // Exactement à la limite de rétention (7 jours = cutoff = today - 7)
        LocalDate boundary = LocalDate.now().minusDays(7);
        Path borderFile = tempDir.resolve("audit-" + boundary.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".log");
        Files.writeString(borderFile, "boundary");

        manager.purgeOldLogs();

        // Le fichier à la limite exacte doit être conservé (isBefore = strict)
        assertTrue(Files.exists(borderFile),
            "Le fichier exactement à la limite de rétention doit être conservé");
    }

    // ── queryLog() ───────────────────────────────────────────────────────────

    @Test
    void queryLog_returnsMatchingEntries() throws IOException {
        Path today = manager.todayFile();
        Files.writeString(today,
            "[2026-04-23 12:00:00] [ArcadiaGuard] player=Curveo action=block-break zone=spawn pos=42 64 -18\n" +
            "[2026-04-23 12:00:01] [ArcadiaGuard] player=Admin action=pvp zone=arena pos=0 70 0\n",
            StandardCharsets.UTF_8);

        var entries = manager.queryLog(10, "spawn", "");

        assertEquals(1, entries.size());
        assertTrue(entries.get(0).contains("player=Curveo"), "Doit contenir player=Curveo");
        assertTrue(entries.get(0).contains("action=block-break"), "Doit contenir action=block-break");
        assertTrue(entries.get(0).contains("zone=spawn"), "Doit contenir zone=spawn");
    }

    @Test
    void queryLog_noFilter_returnsAllEntries() throws IOException {
        Path today = manager.todayFile();
        Files.writeString(today,
            "[2026-04-23 10:00:00] [ArcadiaGuard] player=Alice action=pvp zone=arena pos=1 64 1\n" +
            "[2026-04-23 10:00:01] [ArcadiaGuard] player=Bob action=block-break zone=base pos=2 64 2\n",
            StandardCharsets.UTF_8);

        var entries = manager.queryLog(10, "", "");

        assertEquals(2, entries.size());
    }

    @Test
    void queryLog_playerFilter_exactMatch() throws IOException {
        Path today = manager.todayFile();
        Files.writeString(today,
            "[2026-04-23 10:00:00] [ArcadiaGuard] player=Curveo action=pvp zone=arena pos=1 64 1\n",
            StandardCharsets.UTF_8);

        // queryLog utilise line.contains() — filtre sensible à la casse
        var entries = manager.queryLog(10, "", "Curveo");

        assertEquals(1, entries.size());
        assertTrue(entries.get(0).contains("player=Curveo"), "Doit contenir player=Curveo");
    }

    @Test
    void queryLog_emptyFile_returnsEmpty() throws IOException {
        Files.createFile(manager.todayFile());
        var entries = manager.queryLog(10, "", "");
        assertTrue(entries.isEmpty());
    }

    @Test
    void queryLog_noMatchingZone_returnsEmpty() throws IOException {
        Path today = manager.todayFile();
        Files.writeString(today,
            "[2026-04-23 10:00:00] [ArcadiaGuard] player=Player action=pvp zone=arena pos=1 64 1\n",
            StandardCharsets.UTF_8);

        var entries = manager.queryLog(10, "nonexistent", "");
        assertTrue(entries.isEmpty());
    }

    @Test
    void queryLog_limit_truncatesResults() throws IOException {
        Path today = manager.todayFile();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("[2026-04-23 10:00:0").append(i < 10 ? "0" + i : i)
              .append("] [ArcadiaGuard] player=P action=pvp zone=spawn pos=0 64 0\n");
        }
        Files.writeString(today, sb.toString(), StandardCharsets.UTF_8);

        var entries = manager.queryLog(5, "spawn", "");
        assertTrue(entries.size() <= 5, "Le nombre d'entrées retournées doit respecter la limite");
    }
}

package com.arcadia.arcadiaguard.logging;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

public final class ArcadiaGuardAuditLogger {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LOG_FILE_NAME = "arcadiaguard-audit.log";

    private Path logsDir;
    private LocalDate activeDate;

    public synchronized void onServerStarted(MinecraftServer server) {
        this.logsDir = ArcadiaGuardPaths.logsRoot();
        this.activeDate = LocalDate.now();
    }

    public synchronized void onServerStopped() {
        this.logsDir = null;
        this.activeDate = null;
    }

    public synchronized void logBlockedAction(String playerName, String actionName, String zoneName, BlockPos pos) {
        String line = String.format(
            "[ArcadiaGuard] player=%s action=%s zone=%s pos=%d %d %d",
            playerName,
            actionName,
            zoneName,
            pos == null ? 0 : ReflectionHelper.intMethod(pos, "getX"),
            pos == null ? 0 : ReflectionHelper.intMethod(pos, "getY"),
            pos == null ? 0 : ReflectionHelper.intMethod(pos, "getZ")
        );
        if (ArcadiaGuardConfig.ENABLE_LOGGING.get()) {
            ArcadiaGuard.LOGGER.info(line);
        }
        if (!ArcadiaGuardConfig.LOG_TO_FILE.get() || this.logsDir == null) {
            return;
        }
        try {
            Files.createDirectories(this.logsDir);
            rotateIfNeeded();
            Path logFile = this.logsDir.resolve(LOG_FILE_NAME);
            String payload = "[" + TS.format(LocalDateTime.now()) + "] " + line + System.lineSeparator();
            Files.writeString(logFile, payload, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.warn("Failed to write ArcadiaGuard audit log", e);
        }
    }

    private void rotateIfNeeded() throws IOException {
        LocalDate now = LocalDate.now();
        if (this.activeDate == null) {
            this.activeDate = now;
            return;
        }
        if (this.activeDate.equals(now)) {
            return;
        }
        Path current = this.logsDir.resolve(LOG_FILE_NAME);
        if (Files.exists(current)) {
            Path archived = this.logsDir.resolve("arcadiaguard-audit-" + this.activeDate + ".log");
            Files.move(current, archived, StandardCopyOption.REPLACE_EXISTING);
        }
        this.activeDate = now;
    }
}

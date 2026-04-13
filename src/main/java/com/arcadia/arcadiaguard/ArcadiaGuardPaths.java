package com.arcadia.arcadiaguard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ArcadiaGuardPaths {

    private static final Path CONFIG_ROOT = Path.of("config").resolve("arcadia").resolve("arcadiaguard");
    private static final Path LOG_ROOT = Path.of("logs").resolve("arcadia").resolve("arcadiaguard");

    private ArcadiaGuardPaths() {}

    public static String commonConfigSpecPath() {
        return "arcadia/arcadiaguard/arcadiaguard-common.toml";
    }

    public static Path configRoot() {
        return CONFIG_ROOT;
    }

    public static Path zonesFile() {
        return CONFIG_ROOT.resolve("arcadiaguard-zones.json");
    }

    public static Path exceptionsFile() {
        return CONFIG_ROOT.resolve("arcadiaguard-exceptions.json");
    }

    public static Path logsRoot() {
        return LOG_ROOT;
    }

    public static void migrateLegacyFiles() {
        migrate(Path.of("config").resolve("arcadiaguard-common.toml"), CONFIG_ROOT.resolve("arcadiaguard-common.toml"));
        migrate(Path.of("config").resolve("arcadiaguard-zones.json"), zonesFile());
        migrate(Path.of("config").resolve("arcadiaguard-exceptions.json"), exceptionsFile());
    }

    private static void migrate(Path legacy, Path target) {
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target) || !Files.exists(legacy)) return;
            Files.move(legacy, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.warn("Failed to migrate ArcadiaGuard file from {} to {}", legacy, target, e);
        }
    }
}

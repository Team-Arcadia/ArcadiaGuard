package com.arcadia.arcadiaguard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ArcadiaGuardPaths {

    private static final Path CONFIG_ROOT = Path.of("config").resolve("arcadia").resolve("ArcadiaGuard");
    private static final Path LOG_ROOT = Path.of("logs").resolve("arcadia").resolve("ArcadiaGuard");

    /** Regex for accepted zone names — keeps files scoped inside their dimension directory. */
    private static final java.util.regex.Pattern VALID_ZONE_NAME =
        java.util.regex.Pattern.compile("[a-z0-9_\\-]{1,64}");

    private ArcadiaGuardPaths() {}

    /**
     * Returns true if {@code zoneName} is safe to use as a filename component.
     * Reject early at ingestion to prevent path traversal and OS-specific issues.
     */
    public static boolean isValidZoneName(String zoneName) {
        if (zoneName == null) return false;
        return VALID_ZONE_NAME.matcher(zoneName.toLowerCase()).matches();
    }

    public static String commonConfigSpecPath() {
        return "arcadia/ArcadiaGuard/arcadiaguard-common.toml";
    }

    public static Path configRoot() {
        return CONFIG_ROOT;
    }

    // --- Zones (1 file per zone) ---

    public static Path zonesRoot() {
        return CONFIG_ROOT.resolve("zones");
    }

    /** Returns the directory for zones in the given dimension (e.g. "minecraft-overworld"). */
    public static Path zonesDir(String dimKey) {
        return zonesRoot().resolve(sanitizeDimKey(dimKey));
    }

    /** Returns the JSON file path for a given zone. */
    public static Path zoneFile(String dimKey, String zoneName) {
        if (!isValidZoneName(zoneName)) {
            throw new IllegalArgumentException("Invalid zone name: '" + zoneName + "'");
        }
        Path dir = zonesDir(dimKey).toAbsolutePath().normalize();
        Path resolved = dir.resolve(zoneName.toLowerCase() + ".json").normalize();
        if (!resolved.startsWith(dir)) {
            throw new IllegalArgumentException("Zone path escape detected: " + zoneName);
        }
        return resolved;
    }

    // --- Other files ---

    public static Path blockedItemsFile() {
        return CONFIG_ROOT.resolve("blocked-items.json");
    }

    public static Path dimFlagsFile() {
        return CONFIG_ROOT.resolve("dimension-flags.json");
    }

    public static Path logsRoot() {
        return LOG_ROOT;
    }

    /** Replaces ':' with '-' so dimension keys are valid directory names on all OS. */
    public static String sanitizeDimKey(String dimKey) {
        return dimKey.replace(':', '-');
    }

    public static void migrateLegacyFiles() {
        // Depuis la racine config/ (très ancienne version)
        migrate(Path.of("config").resolve("arcadiaguard-common.toml"), CONFIG_ROOT.resolve("arcadiaguard-common.toml"));
        // Depuis config/arcadia/arcadiaguard/ (sous-dossier minuscule)
        Path legacyRoot = Path.of("config").resolve("arcadia").resolve("arcadiaguard");
        migrate(legacyRoot.resolve("arcadiaguard-common.toml"), CONFIG_ROOT.resolve("arcadiaguard-common.toml"));
        // TODO: migrate legacy single-file zones format (arcadiaguard-zones.json) to 1-file-per-zone
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

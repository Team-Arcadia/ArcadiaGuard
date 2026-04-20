package com.arcadia.arcadiaguard.api.zone;

/**
 * Result of a zone protection check.
 *
 * @param blocked      true if the action was blocked by a zone
 * @param zoneName     the name of the blocking zone, or empty string if not blocked
 * @param providerName the zone provider that issued the verdict, or empty string
 */
public record ZoneCheckResult(boolean blocked, String zoneName, String providerName) {

    public ZoneCheckResult(boolean blocked, String zoneName) {
        this(blocked, zoneName, "");
    }

    public static ZoneCheckResult allowed() {
        return new ZoneCheckResult(false, "", "");
    }

    public static ZoneCheckResult blocked(String zoneName) {
        return new ZoneCheckResult(true, zoneName, "");
    }

    public static ZoneCheckResult blocked(String zoneName, String providerName) {
        return new ZoneCheckResult(true, zoneName, providerName);
    }
}

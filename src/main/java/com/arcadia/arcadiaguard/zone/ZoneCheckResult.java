package com.arcadia.arcadiaguard.zone;

public record ZoneCheckResult(boolean blocked, String zoneName, String providerName) {
    public static ZoneCheckResult allowed() {
        return new ZoneCheckResult(false, "", "");
    }
}

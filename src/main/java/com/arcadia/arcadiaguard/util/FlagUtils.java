package com.arcadia.arcadiaguard.util;

public final class FlagUtils {
    private FlagUtils() {}

    public static String formatFlagLabel(String id) {
        String[] parts = id.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}

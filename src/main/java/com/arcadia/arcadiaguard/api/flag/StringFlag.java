package com.arcadia.arcadiaguard.api.flag;

import java.util.List;

/**
 * A {@link Flag} whose value is free text or one value from an optional string set.
 */
public final class StringFlag implements Flag<String> {

    private final String id;
    private final String defaultValue;
    private final int maxLength;
    private final List<String> allowedValues;
    private final String description;
    private final String requiredMod;

    public StringFlag(String id, String defaultValue, String description) {
        this(id, defaultValue, 512, List.of(), description, "");
    }

    public StringFlag(String id, String defaultValue, int maxLength, String description) {
        this(id, defaultValue, maxLength, List.of(), description, "");
    }

    public StringFlag(String id, String defaultValue, int maxLength, List<String> allowedValues, String description) {
        this(id, defaultValue, maxLength, allowedValues, description, "");
    }

    public StringFlag(String id, String defaultValue, int maxLength, List<String> allowedValues,
                      String description, String requiredMod) {
        this.id = id;
        this.defaultValue = defaultValue == null ? "" : defaultValue;
        this.maxLength = Math.max(1, maxLength);
        this.allowedValues = List.copyOf(allowedValues == null ? List.of() : allowedValues);
        this.description = description == null ? "" : description;
        this.requiredMod = requiredMod == null ? "" : requiredMod;
    }

    public int maxLength() { return maxLength; }
    public List<String> allowedValues() { return allowedValues; }

    @Override public String id() { return id; }
    @Override public String defaultValue() { return defaultValue; }
    @Override public String description() { return description; }
    @Override public String requiredMod() { return requiredMod; }
}

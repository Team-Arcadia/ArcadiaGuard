package com.arcadia.arcadiaguard.api.flag;

/**
 * A {@link Flag} whose value is an integer (e.g. priority, heal-amount).
 */
public final class IntFlag implements Flag<Integer> {

    private final String id;
    private final int defaultValue;
    private final String description;

    public IntFlag(String id, int defaultValue) {
        this(id, defaultValue, "");
    }

    public IntFlag(String id, int defaultValue, String description) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.description = description;
    }

    @Override
    public String id() { return this.id; }

    @Override
    public Integer defaultValue() { return this.defaultValue; }

    @Override
    public String description() { return this.description; }
}

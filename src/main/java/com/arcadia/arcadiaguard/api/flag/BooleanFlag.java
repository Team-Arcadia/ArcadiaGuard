package com.arcadia.arcadiaguard.api.flag;

/**
 * A {@link Flag} whose value is a boolean (allow/deny semantics).
 */
public final class BooleanFlag implements Flag<Boolean> {

    private final String id;
    private final boolean defaultValue;
    private final String description;

    public BooleanFlag(String id, boolean defaultValue) {
        this(id, defaultValue, "");
    }

    public BooleanFlag(String id, boolean defaultValue, String description) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.description = description;
    }

    @Override
    public String id() { return this.id; }

    @Override
    public Boolean defaultValue() { return this.defaultValue; }

    @Override
    public String description() { return this.description; }
}

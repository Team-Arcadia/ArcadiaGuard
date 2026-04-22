package com.arcadia.arcadiaguard.api.flag;

/**
 * A {@link Flag} whose value is an integer (e.g. priority, heal-amount).
 * Supports optional {@code min}/{@code max} bounds enforced client-side
 * (validation) and server-side (clamp on set).
 */
public final class IntFlag implements Flag<Integer> {

    private final String id;
    private final int defaultValue;
    private final int min;
    private final int max;
    private final String description;
    private final String requiredMod;

    public IntFlag(String id, int defaultValue) {
        this(id, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE, "", "");
    }

    public IntFlag(String id, int defaultValue, String description) {
        this(id, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE, description, "");
    }

    public IntFlag(String id, int defaultValue, String description, String requiredMod) {
        this(id, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE, description, requiredMod);
    }

    public IntFlag(String id, int defaultValue, int min, int max, String description) {
        this(id, defaultValue, min, max, description, "");
    }

    public IntFlag(String id, int defaultValue, int min, int max, String description, String requiredMod) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.description = description;
        this.requiredMod = requiredMod;
    }

    public int min() { return this.min; }
    public int max() { return this.max; }

    @Override public String id() { return this.id; }
    @Override public Integer defaultValue() { return this.defaultValue; }
    @Override public String description() { return this.description; }
    @Override public String requiredMod() { return this.requiredMod; }
}

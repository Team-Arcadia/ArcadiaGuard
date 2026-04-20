package com.arcadia.arcadiaguard.api.flag;

import java.util.List;

/**
 * A {@link Flag} whose value is a list of strings (e.g. blacklists, whitelists, spawn lists).
 */
public final class ListFlag implements Flag<List<String>> {

    private final String id;
    private final String description;

    public ListFlag(String id) {
        this(id, "");
    }

    public ListFlag(String id, String description) {
        this.id = id;
        this.description = description;
    }

    @Override
    public String id() { return this.id; }

    @Override
    public List<String> defaultValue() { return List.of(); }

    @Override
    public String description() { return this.description; }
}

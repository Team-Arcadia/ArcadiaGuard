package com.arcadia.arcadiaguard.zone;

/** Role hierarchy for zone members. Higher ordinal = more privileges. */
public enum ZoneRole {
    MEMBER,
    MODERATOR,
    OWNER;

    public boolean atLeast(ZoneRole required) {
        return this.ordinal() >= required.ordinal();
    }
}

package com.arcadia.arcadiaguard.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * Applies forward migrations to zone JSON when the stored {@code format_version} is lower
 * than the current expected version. Ensures backward compatibility across mod updates.
 */
public final class ZoneMigrator {

    /** The current JSON format version. Increment when the schema changes. */
    public static final int CURRENT_VERSION = 2;

    private interface MigrationStep {
        int fromVersion();
        int toVersion();
        void apply(JsonObject json);
    }

    private static final List<MigrationStep> STEPS = List.of(

        // v0 → v1: zones created before format_version existed lacked enabled/priority/whitelist/flags
        new MigrationStep() {
            public int fromVersion() { return 0; }
            public int toVersion()   { return 1; }
            public void apply(JsonObject json) {
                if (!json.has("enabled"))   json.addProperty("enabled",  true);
                if (!json.has("priority"))  json.addProperty("priority", 0);
                if (!json.has("whitelist")) json.add("whitelist", new JsonArray());
                if (!json.has("flags"))     json.add("flags",     new JsonObject());
            }
        },

        // v1 → v2: added inherit_dim_flags and member_roles
        new MigrationStep() {
            public int fromVersion() { return 1; }
            public int toVersion()   { return 2; }
            public void apply(JsonObject json) {
                if (!json.has("inherit_dim_flags"))
                    json.addProperty("inherit_dim_flags", true);
                if (!json.has("member_roles"))
                    json.add("member_roles", new JsonObject());
            }
        }
    );

    private ZoneMigrator() {}

    /**
     * Migrates {@code json} from its current {@code format_version} to {@link #CURRENT_VERSION}.
     * The object is mutated in-place and the {@code format_version} field is updated.
     *
     * @param json the raw zone JSON object
     * @return true if a migration was applied (file should be re-saved)
     */
    public static boolean migrate(JsonObject json) {
        int version = json.has("format_version") ? json.get("format_version").getAsInt() : 0;
        if (version >= CURRENT_VERSION) return false;

        for (MigrationStep step : STEPS) {
            if (version == step.fromVersion()) {
                step.apply(json);
                version = step.toVersion();
            }
        }
        json.addProperty("format_version", CURRENT_VERSION);
        return true;
    }
}

package com.arcadia.arcadiaguard.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ArcadiaGuardConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_LOGGING;
    public static final ModConfigSpec.BooleanValue LOG_TO_FILE;
    public static final ModConfigSpec.BooleanValue DEBUG;
    public static final ModConfigSpec.IntValue BYPASS_OP_LEVEL;
    public static final ModConfigSpec.BooleanValue ENABLE_BLOCK_BREAK;
    public static final ModConfigSpec.BooleanValue ENABLE_BLOCK_PLACE;
    public static final ModConfigSpec.BooleanValue ENABLE_IRONSSPELLBOOKS;
    public static final ModConfigSpec.BooleanValue ENABLE_ARS_NOUVEAU;
    public static final ModConfigSpec.BooleanValue ENABLE_SIMPLYSWORDS;
    public static final ModConfigSpec.BooleanValue ENABLE_OCCULTISM;
    public static final ModConfigSpec.BooleanValue ENABLE_SUPPLEMENTARIES;
    public static final ModConfigSpec.BooleanValue ENABLE_APOTHEOSIS_ENCHANTS;
    public static final ModConfigSpec.BooleanValue ENABLE_BETTERARCHEOLOGY;
    public static final ModConfigSpec.BooleanValue ENABLE_SPAWN_BOOK_PROTECTION;
    public static final ModConfigSpec.BooleanValue ENABLE_LEAD_PROTECTION;
    public static final ModConfigSpec.BooleanValue ENABLE_SPAWN_EGG_PROTECTION;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_BLOCK_BREAK;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_BLOCK_PLACE;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_IRONSSPELLBOOKS;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_ARS_NOUVEAU;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_SIMPLYSWORDS;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_OCCULTISM;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_SUPPLEMENTARIES;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_APOTHEOSIS;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_BETTERARCHEOLOGY;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_SPAWN_BOOK;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_LEAD;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_SPAWN_EGG;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_DYNAMIC_ITEM;

    public static final ModConfigSpec.IntValue ASYNC_WRITER_CAPACITY;
    public static final ModConfigSpec.ConfigValue<String> ASYNC_WRITER_POLICY;

    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> DISABLED_FLAG_FREQUENCIES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        ENABLE_LOGGING = builder.define("enable_logging", true);
        LOG_TO_FILE = builder.define("log_to_file", true);
        builder.comment("Mode debug : si true, affiche tous les messages INFO d'ArcadiaGuard",
            "(boot, registrations handlers, migrations YAWP, force-loaded chunks...) ET",
            "laisse passer les warnings 'marked as removed' causes par d'autres mods (Hominid).",
            "Si false (defaut), seuls les WARN et ERROR sont affiches et les warnings tiers",
            "sont silencieusement supprimes.");
        DEBUG = builder.define("debug", false);
        BYPASS_OP_LEVEL = builder.defineInRange("bypass_op_level", 2, 0, 4);
        builder.pop();

        builder.push("toggles");
        ENABLE_BLOCK_BREAK = builder.define("enable_block_break", true);
        ENABLE_BLOCK_PLACE = builder.define("enable_block_place", true);
        ENABLE_IRONSSPELLBOOKS = builder.define("enable_ironsspellbooks", true);
        ENABLE_ARS_NOUVEAU = builder.define("enable_arsnouveau", true);
        ENABLE_SIMPLYSWORDS = builder.define("enable_simplyswords", true);
        ENABLE_OCCULTISM = builder.define("enable_occultism", true);
        ENABLE_SUPPLEMENTARIES = builder.define("enable_supplementaries", true);
        ENABLE_APOTHEOSIS_ENCHANTS = builder.define("enable_apotheosis_enchants", true);
        ENABLE_BETTERARCHEOLOGY = builder.define("enable_betterarcheology", true);
        ENABLE_SPAWN_BOOK_PROTECTION = builder.define("enable_spawn_book_protection", true);
        ENABLE_LEAD_PROTECTION = builder.define("enable_lead_protection", true);
        ENABLE_SPAWN_EGG_PROTECTION = builder.define("enable_spawn_egg_protection", true);
        builder.pop();

        builder.push("messages");
        MESSAGE_BLOCK_BREAK = builder.define("message_block_break", "Vous ne pouvez pas casser des blocs ici.");
        MESSAGE_BLOCK_PLACE = builder.define("message_block_place", "Vous ne pouvez pas placer des blocs ici.");
        MESSAGE_IRONSSPELLBOOKS = builder.define("message_ironsspellbooks", "Vous ne pouvez pas lancer ce sort ici.");
        MESSAGE_ARS_NOUVEAU = builder.define("message_arsnouveau", "Vous ne pouvez pas utiliser Ars Nouveau ici.");
        MESSAGE_SIMPLYSWORDS = builder.define("message_simplyswords", "Vous ne pouvez pas utiliser cette capacite ici.");
        MESSAGE_OCCULTISM = builder.define("message_occultism", "Vous ne pouvez pas utiliser cette magie ici.");
        MESSAGE_SUPPLEMENTARIES = builder.define("message_supplementaries", "Vous ne pouvez pas lancer cet objet ici.");
        MESSAGE_APOTHEOSIS = builder.define("message_apotheosis", "Vous ne pouvez pas casser ces blocs ici.");
        MESSAGE_BETTERARCHEOLOGY = builder.define("message_betterarcheology", "Vous ne pouvez pas utiliser Tunneling ici.");
        MESSAGE_SPAWN_BOOK = builder.define("message_spawn_book", " Vous ne pouvez pas placer de livres en zone spawn ! | You cannot place books in the spawn area!");
        MESSAGE_LEAD = builder.define("message_lead", "Vous ne pouvez pas utiliser une laisse ici.");
        MESSAGE_SPAWN_EGG = builder.define("message_spawn_egg", "Vous ne pouvez pas utiliser un oeuf de spawn ici.");
        MESSAGE_DYNAMIC_ITEM = builder.define("message_dynamic_item", "Vous ne pouvez pas utiliser cet objet ici.");
        builder.pop();

        builder.push("async_writer");
        ASYNC_WRITER_CAPACITY = builder.defineInRange("async_writer_capacity", 1024, 64, 65536);
        ASYNC_WRITER_POLICY   = builder.define("async_writer_policy", "BLOCK",
            v -> v instanceof String s && (s.equalsIgnoreCase("BLOCK")
                || s.equalsIgnoreCase("FAIL_FAST") || s.equalsIgnoreCase("DROP")));
        builder.pop();

        builder.push("performance");
        builder.comment("Classes de frequence de flags a desactiver globalement (kill-switch).",
            "Valeurs acceptees: NEGLIGIBLE, LOW, NORMAL, HIGH, VERY_HIGH.",
            "Exemple: [\"VERY_HIGH\"] desactive leaf-decay, water-spread, sculk-spread, etc.");
        DISABLED_FLAG_FREQUENCIES = builder.defineList("disabled_flag_frequencies",
            java.util.List.of(),
            () -> "NORMAL",
            v -> v instanceof String s && (s.equalsIgnoreCase("NEGLIGIBLE")
                || s.equalsIgnoreCase("LOW") || s.equalsIgnoreCase("NORMAL")
                || s.equalsIgnoreCase("HIGH") || s.equalsIgnoreCase("VERY_HIGH")));
        builder.pop();

        SPEC = builder.build();
    }

    private ArcadiaGuardConfig() {}
}

package com.arcadia.arcadiaguard.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ArcadiaGuardConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_LOGGING;
    public static final ModConfigSpec.BooleanValue LOG_TO_FILE;
    public static final ModConfigSpec.IntValue BYPASS_OP_LEVEL;
    public static final ModConfigSpec.BooleanValue ENABLE_IRONSSPELLBOOKS;
    public static final ModConfigSpec.BooleanValue ENABLE_ARS_NOUVEAU;
    public static final ModConfigSpec.BooleanValue ENABLE_SIMPLYSWORDS;
    public static final ModConfigSpec.BooleanValue ENABLE_OCCULTISM;
    public static final ModConfigSpec.BooleanValue ENABLE_SUPPLEMENTARIES;
    public static final ModConfigSpec.BooleanValue ENABLE_APOTHEOSIS_ENCHANTS;
    public static final ModConfigSpec.BooleanValue ENABLE_BETTERARCHEOLOGY;
    public static final ModConfigSpec.BooleanValue ENABLE_SPAWN_BOOK_PROTECTION;
    public static final ModConfigSpec.ConfigValue<String> SPAWN_BOOK_DIMENSION;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_IRONSSPELLBOOKS;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_ARS_NOUVEAU;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_SIMPLYSWORDS;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_OCCULTISM;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_SUPPLEMENTARIES;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_APOTHEOSIS;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_BETTERARCHEOLOGY;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_SPAWN_BOOK;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        ENABLE_LOGGING = builder.define("enable_logging", true);
        LOG_TO_FILE = builder.define("log_to_file", true);
        BYPASS_OP_LEVEL = builder.defineInRange("bypass_op_level", 2, 0, 4);
        builder.pop();

        builder.push("toggles");
        ENABLE_IRONSSPELLBOOKS = builder.define("enable_ironsspellbooks", true);
        ENABLE_ARS_NOUVEAU = builder.define("enable_arsnouveau", true);
        ENABLE_SIMPLYSWORDS = builder.define("enable_simplyswords", true);
        ENABLE_OCCULTISM = builder.define("enable_occultism", true);
        ENABLE_SUPPLEMENTARIES = builder.define("enable_supplementaries", true);
        ENABLE_APOTHEOSIS_ENCHANTS = builder.define("enable_apotheosis_enchants", true);
        ENABLE_BETTERARCHEOLOGY = builder.define("enable_betterarcheology", true);
        ENABLE_SPAWN_BOOK_PROTECTION = builder.define("enable_spawn_book_protection", true);
        SPAWN_BOOK_DIMENSION = builder.define("spawn_book_dimension", "arcadia:spawn");
        builder.pop();

        builder.push("messages");
        MESSAGE_IRONSSPELLBOOKS = builder.define("message_ironsspellbooks", "Vous ne pouvez pas lancer ce sort ici.");
        MESSAGE_ARS_NOUVEAU = builder.define("message_arsnouveau", "Vous ne pouvez pas utiliser Ars Nouveau ici.");
        MESSAGE_SIMPLYSWORDS = builder.define("message_simplyswords", "Vous ne pouvez pas utiliser cette capacite ici.");
        MESSAGE_OCCULTISM = builder.define("message_occultism", "Vous ne pouvez pas utiliser cette magie ici.");
        MESSAGE_SUPPLEMENTARIES = builder.define("message_supplementaries", "Vous ne pouvez pas lancer cet objet ici.");
        MESSAGE_APOTHEOSIS = builder.define("message_apotheosis", "Vous ne pouvez pas casser ces blocs ici.");
        MESSAGE_BETTERARCHEOLOGY = builder.define("message_betterarcheology", "Vous ne pouvez pas utiliser Tunneling ici.");
        MESSAGE_SPAWN_BOOK = builder.define("message_spawn_book", " Vous ne pouvez pas placer de livres en zone spawn ! | You cannot place books in the spawn area!");
        builder.pop();

        SPEC = builder.build();
    }

    private ArcadiaGuardConfig() {}
}

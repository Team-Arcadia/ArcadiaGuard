package com.arcadia.arcadiaguard.flag;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.IntFlag;
import com.arcadia.arcadiaguard.api.flag.ListFlag;

/**
 * Declares all ~60 built-in flags shipped with ArcadiaGuard.
 * Registered into FlagRegistryImpl during FMLCommonSetupEvent.
 */
public final class BuiltinFlags {

    private BuiltinFlags() {}

    // --- Blocks ---
    public static final BooleanFlag BLOCK_BREAK       = new BooleanFlag("block-break",       false, "Autorise/refuse la destruction de blocs dans la zone.");
    public static final BooleanFlag BLOCK_PLACE        = new BooleanFlag("block-place",        false, "Autorise/refuse la pose de blocs dans la zone.");
    public static final BooleanFlag BLOCK_INTERACT     = new BooleanFlag("block-interact",     false, "Autorise/refuse l'interaction avec les blocs (clic droit).");
    public static final BooleanFlag CONTAINER_ACCESS   = new BooleanFlag("container-access",   false, "Autorise/refuse l'ouverture des coffres, tonneaux et autres conteneurs.");
    public static final BooleanFlag DOOR               = new BooleanFlag("door",               false, "Autorise/refuse l'ouverture et la fermeture des portes.");
    public static final BooleanFlag TRAPDOOR           = new BooleanFlag("trapdoor",           false, "Autorise/refuse l'activation des trappes.");
    public static final BooleanFlag BUTTON             = new BooleanFlag("button",             false, "Autorise/refuse l'utilisation des boutons.");
    public static final BooleanFlag LEVER              = new BooleanFlag("lever",              false, "Autorise/refuse l'activation des leviers.");
    public static final BooleanFlag PRESSURE_PLATE     = new BooleanFlag("pressure-plate",     false, "Autorise/refuse le déclenchement des plaques de pression.");
    public static final BooleanFlag GATE               = new BooleanFlag("gate",               false, "Autorise/refuse l'ouverture des portails en bois.");

    // --- Combat ---
    public static final BooleanFlag PVP                = new BooleanFlag("pvp",                false, "Autorise/refuse les combats joueur contre joueur (PvP).");
    public static final BooleanFlag PLAYER_DAMAGE      = new BooleanFlag("player-damage",      false, "Autorise/refuse que les joueurs subissent des dégâts dans la zone.");
    public static final BooleanFlag MOB_DAMAGE         = new BooleanFlag("mob-damage",         false, "Autorise/refuse que les mobs subissent des dégâts dans la zone.");
    public static final BooleanFlag FALL_DAMAGE        = new BooleanFlag("fall-damage",        false, "Autorise/refuse les dégâts de chute pour les joueurs.");
    public static final BooleanFlag INVINCIBLE         = new BooleanFlag("invincible",         false, "Rend les joueurs complètement invulnérables dans la zone.");
    public static final BooleanFlag ATTACK_ANIMALS     = new BooleanFlag("attack-animals",     false, "Autorise/refuse d'attaquer les animaux passifs.");
    public static final BooleanFlag ATTACK_MONSTERS    = new BooleanFlag("attack-monsters",    false, "Autorise/refuse d'attaquer les monstres hostiles.");
    public static final BooleanFlag ENDER_PEARL        = new BooleanFlag("ender-pearl",        false, "Autorise/refuse l'utilisation des perles de l'End.");
    public static final BooleanFlag CHORUS_FRUIT       = new BooleanFlag("chorus-fruit",       false, "Autorise/refuse la consommation du fruit de chorus (téléportation).");

    // --- Mobs ---
    public static final BooleanFlag MOB_SPAWN          = new BooleanFlag("mob-spawn",          false, "Autorise/refuse l'apparition de tout type de mob dans la zone.");
    public static final BooleanFlag ANIMAL_SPAWN       = new BooleanFlag("animal-spawn",       false, "Autorise/refuse l'apparition des animaux passifs.");
    public static final BooleanFlag MONSTER_SPAWN      = new BooleanFlag("monster-spawn",      false, "Autorise/refuse l'apparition des monstres hostiles.");
    public static final BooleanFlag VILLAGER_SPAWN     = new BooleanFlag("villager-spawn",     false, "Autorise/refuse l'apparition des villageois.");
    public static final BooleanFlag VEHICLE_PLACE      = new BooleanFlag("vehicle-place",      false, "Autorise/refuse la pose de bateaux et de minecarts.");
    public static final BooleanFlag VEHICLE_DESTROY    = new BooleanFlag("vehicle-destroy",    false, "Autorise/refuse la destruction de bateaux et de minecarts.");
    public static final BooleanFlag LEASH              = new BooleanFlag("leash",              false, "Autorise/refuse l'utilisation des laisses sur les entités.");
    public static final BooleanFlag SPAWN_EGG          = new BooleanFlag("spawn-egg",          false, "Autorise/refuse l'utilisation des œufs d'apparition.");

    // --- Explosions & Spread ---
    public static final BooleanFlag CREEPER_EXPLOSION  = new BooleanFlag("creeper-explosion",  false, "Autorise/refuse les dégâts des explosions de creepers.");
    public static final BooleanFlag TNT_EXPLOSION      = new BooleanFlag("tnt-explosion",      false, "Autorise/refuse les dégâts des explosions de TNT.");
    public static final BooleanFlag BLOCK_EXPLOSION    = new BooleanFlag("block-explosion",    false, "Autorise/refuse les explosions générées par des blocs.");
    public static final BooleanFlag FIRE_SPREAD        = new BooleanFlag("fire-spread",        false, "Autorise/refuse la propagation du feu sur les blocs.");
    public static final BooleanFlag LAVA_FIRE          = new BooleanFlag("lava-fire",          false, "Autorise/refuse l'allumage de feux par la lave.");
    public static final BooleanFlag LAVA_SPREAD        = new BooleanFlag("lava-spread",        false, "Autorise/refuse l'écoulement de la lave.");
    public static final BooleanFlag WATER_SPREAD       = new BooleanFlag("water-spread",       false, "Autorise/refuse l'écoulement de l'eau.");
    public static final BooleanFlag LEAF_DECAY         = new BooleanFlag("leaf-decay",         false, "Autorise/refuse la dégradation naturelle des feuilles.");

    // --- Environment ---
    public static final BooleanFlag CROP_GROWTH        = new BooleanFlag("crop-growth",        false, "Autorise/refuse la pousse des cultures (blé, carottes…).");
    public static final BooleanFlag TREE_GROWTH        = new BooleanFlag("tree-growth",        false, "Autorise/refuse la pousse des arbres depuis des plants.");
    public static final BooleanFlag FARMLAND_TRAMPLE   = new BooleanFlag("farmland-trample",   false, "Autorise/refuse le piétinement des terres cultivées.");
    public static final BooleanFlag GRASS_SPREAD       = new BooleanFlag("grass-spread",       false, "Autorise/refuse la propagation de l'herbe sur la terre.");
    public static final BooleanFlag VINE_GROWTH        = new BooleanFlag("vine-growth",        false, "Autorise/refuse la propagation des lianes.");
    public static final BooleanFlag SCULK_SPREAD       = new BooleanFlag("sculk-spread",       false, "Autorise/refuse la propagation des blocs Sculk.");
    public static final BooleanFlag SNOW_MELT          = new BooleanFlag("snow-melt",          false, "Autorise/refuse la fonte de la neige.");
    public static final BooleanFlag ICE_MELT           = new BooleanFlag("ice-melt",           false, "Autorise/refuse la fonte de la glace.");

    // --- Items ---
    public static final BooleanFlag ITEM_DROP          = new BooleanFlag("item-drop",          false, "Autorise/refuse le lâcher d'objets par les joueurs.");
    public static final BooleanFlag ITEM_PICKUP        = new BooleanFlag("item-pickup",        false, "Autorise/refuse le ramassage d'objets par les joueurs.");
    public static final BooleanFlag ITEM_THROW         = new BooleanFlag("item-throw",         false, "Autorise/refuse le lancer d'objets (comme les œufs ou les snowballs).");
    public static final BooleanFlag EXP_DROP           = new BooleanFlag("exp-drop",           false, "Autorise/refuse l'apparition de points d'expérience.");

    // --- Entry / Exit ---
    public static final BooleanFlag ENTRY              = new BooleanFlag("entry",              false, "Autorise/refuse l'entrée dans la zone pour les joueurs.");
    public static final BooleanFlag EXIT               = new BooleanFlag("exit",               false, "Autorise/refuse la sortie de la zone pour les joueurs.");

    // --- Magic mods (global on/off + blacklist/whitelist) ---
    public static final BooleanFlag ARS_SPELL_CAST      = new BooleanFlag("ars-spell-cast",      false, "[Ars Nouveau] Autorise/refuse le lancement de sorts dans la zone.",            "ars_nouveau");
    public static final ListFlag    ARS_SPELL_BLACKLIST  = new ListFlag("ars-spell-blacklist",                "[Ars Nouveau] Liste de sorts interdits dans la zone.",                    "ars_nouveau");
    public static final ListFlag    ARS_SPELL_WHITELIST  = new ListFlag("ars-spell-whitelist",               "[Ars Nouveau] Liste de sorts autorisés (les autres sont bloqués).",       "ars_nouveau");
    public static final BooleanFlag IRONS_SPELL_CAST    = new BooleanFlag("irons-spell-cast",    false, "[Iron's Spellbooks] Autorise/refuse le lancement de sorts dans la zone.",      "irons_spellbooks");
    public static final ListFlag    IRONS_SPELL_BLACKLIST = new ListFlag("irons-spell-blacklist",            "[Iron's Spellbooks] Liste de sorts interdits dans la zone.",              "irons_spellbooks");
    public static final ListFlag    IRONS_SPELL_WHITELIST = new ListFlag("irons-spell-whitelist",            "[Iron's Spellbooks] Liste de sorts autorisés (les autres sont bloqués).", "irons_spellbooks");
    public static final BooleanFlag SIMPLYSWORDS_ABILITY = new BooleanFlag("simplyswords-ability", false, "[Simply Swords] Autorise/refuse les capacités spéciales des épées.",         "simplyswords");

    // --- Custom flags ---
    public static final BooleanFlag SPELL_MOVEMENT     = new BooleanFlag("spell-movement",     false, "Autorise/refuse les déplacements magiques (vol, téléport par sort).");
    public static final BooleanFlag NPC_INTERACT        = new BooleanFlag("npc-interact",       false, "Autorise/refuse l'interaction avec les PNJ personnalisés.");
    public static final BooleanFlag MOB_BUCKET          = new BooleanFlag("mob-bucket",         false, "Autorise/refuse la capture de mobs aquatiques dans des seaux.");
    public static final BooleanFlag ANIMAL_INVINCIBLE   = new BooleanFlag("animal-invincible",  false, "Rend les animaux passifs invulnérables dans la zone.");
    public static final BooleanFlag OCCULTISM_USE       = new BooleanFlag("occultism-use",      false, "[Occultism] Autorise/refuse l'utilisation des rituels et gemmes d'âme dans la zone.",       "occultism");
    public static final BooleanFlag SUPPLEMENTARIES_THROW = new BooleanFlag("supplementaries-throw", false, "[Supplementaries] Autorise/refuse le lancer de briques dans la zone.",                "supplementaries");
    public static final BooleanFlag PARCOOL_ACTIONS     = new BooleanFlag("parcool-actions",    false, "[ParCool] Autorise/refuse les actions parkour dans la zone.",                               "parcool");
    public static final BooleanFlag EMOTE_USE           = new BooleanFlag("emote-use",          false, "[Emotecraft] Autorise/refuse les emotes dans la zone.",                                     "emotecraft");
    public static final BooleanFlag MUTANT_MOB_SPAWN    = new BooleanFlag("mutant-mob-spawn",   false, "[Mutant Monsters] Autorise/refuse le spawn de monstres mutants dans la zone.",             "mutantmonsters");
    public static final BooleanFlag TF_PROJECTILE       = new BooleanFlag("tf-projectile",      false, "[Twilight Forest] Autorise/refuse les projectiles de boss TF dans la zone.",               "twilightforest");
    public static final BooleanFlag CHARM_USE           = new BooleanFlag("charm-use",          false, "[Apotheosis] Autorise/refuse l'activation des charmes Apotheosis dans la zone.",           "apotheosis");
    public static final BooleanFlag WAYSTONE_USE        = new BooleanFlag("waystone-use",        false, "[Waystones] Autorise/refuse l'utilisation des waystones dans la zone.",                  "waystones");
    public static final BooleanFlag RECHISELED_USE      = new BooleanFlag("rechiseled-use",      false, "[Rechiseled] Autorise/refuse l'utilisation des blocs Rechiseled dans la zone.",          "rechiseled");
    public static final BooleanFlag ARS_ADDITIONS_SCROLL = new BooleanFlag("ars-additions-scroll", false, "[Ars Additions] Autorise/refuse l'utilisation des parchemins Ars Additions.",         "ars_additions");
    public static final BooleanFlag CHUNKLOAD            = new BooleanFlag("chunkload",          false, "Force le chargement permanent des chunks de la zone (ON vert = tickets ArcadiaGuard).");
    public static final BooleanFlag FLY                  = new BooleanFlag("fly",                false, "Bloque le vol vanilla (mayfly) dans la zone. Creative/spectator/OP ignores.");
    public static final BooleanFlag APOTHEOSIS_FLY       = new BooleanFlag("apotheosis-fly",     false, "[Apotheosis] Bloque le vol accorde par les affixes Apotheosis.",                              "apotheosis");

    // --- Zone configuration ---
    public static final IntFlag     HEAL_AMOUNT        = new IntFlag("heal-amount",            0,    "Points de vie régénérés par seconde dans la zone (0 = désactivé).");
    public static final IntFlag     FEED_AMOUNT        = new IntFlag("feed-amount",            0,    "Points de nourriture restaurés par seconde dans la zone (0 = désactivé).");
}

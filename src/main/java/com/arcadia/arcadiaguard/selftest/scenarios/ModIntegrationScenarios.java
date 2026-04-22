package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import net.neoforged.fml.ModList;

/**
 * Scenarios "smoke test" pour les integrations mods tiers : verifie que le mod est
 * detecte, que le flag AG correspondant existe dans le registry et peut etre set/read
 * sur une zone. Un vrai test end-to-end (spell cast Ars, pickup CarryOn) necessite
 * d'invoquer les API du mod tier — fait en complement manuel.
 */
public final class ModIntegrationScenarios {

    private ModIntegrationScenarios() {}

    /** Factory : cree un scenario qui verifie que (mod loaded) + (flag existe). */
    public static Scenario modFlag(String modId, String flagId, String scenarioId) {
        return new Scenario() {
            @Override public String id() { return scenarioId; }
            @Override public String category() { return "mods." + modId; }
            @Override public String requiredMod() { return modId; }
            @Override public ScenarioResult run(TestContext ctx) {
                long start = System.nanoTime();
                // 1) Mod charge
                if (!ModList.get().isLoaded(modId)) {
                    return ScenarioResult.skip(id(), "mod " + modId + " absent");
                }
                // 2) Flag registered
                var f = ArcadiaGuard.flagRegistry().get(flagId);
                long ms = (System.nanoTime() - start) / 1_000_000;
                if (f.isEmpty()) {
                    return ScenarioResult.fail(id(),
                        "flag " + flagId + " absent du registry malgre " + modId + " charge", ms);
                }
                // 3) Flag settable sur zone
                ctx.setupZone(flagId, false, 3);
                var zone = (com.arcadia.arcadiaguard.zone.ProtectedZone)
                    ArcadiaGuard.zoneManager().get(ctx.level(), ctx.zoneName()).orElseThrow();
                Object v = zone.flagValues().get(flagId);
                if (!Boolean.FALSE.equals(v)) {
                    return ScenarioResult.fail(id(),
                        "flag " + flagId + " non defini apres setFlag: " + v, ms);
                }
                return ScenarioResult.pass(id(), "mod " + modId + " detecte + flag OK", ms);
            }
        };
    }

    // Scenarios pre-definis pour chaque mod integre par ArcadiaGuard.
    public static final Scenario ARS_NOUVEAU = modFlag("ars_nouveau",
        BuiltinFlags.ARS_SPELL_CAST.id(), "mod-ars-nouveau");
    public static final Scenario IRONS_SPELLBOOKS = modFlag("irons_spellbooks",
        BuiltinFlags.IRONS_SPELL_CAST.id(), "mod-irons-spellbooks");
    public static final Scenario SIMPLYSWORDS = modFlag("simplyswords",
        BuiltinFlags.SIMPLYSWORDS_ABILITY.id(), "mod-simplyswords");
    public static final Scenario PARCOOL = modFlag("parcool",
        BuiltinFlags.PARCOOL_ACTIONS.id(), "mod-parcool");
    public static final Scenario EMOTECRAFT = modFlag("emotecraft",
        BuiltinFlags.EMOTE_USE.id(), "mod-emotecraft");
    public static final Scenario CARRYON = modFlag("carryon",
        BuiltinFlags.CARRYON.id(), "mod-carryon");
    public static final Scenario WAYSTONES = modFlag("waystones",
        BuiltinFlags.WAYSTONE_USE.id(), "mod-waystones");
    public static final Scenario APOTHEOSIS = modFlag("apotheosis",
        BuiltinFlags.CHARM_USE.id(), "mod-apotheosis");
    public static final Scenario OCCULTISM = modFlag("occultism",
        BuiltinFlags.OCCULTISM_USE.id(), "mod-occultism");
    public static final Scenario SUPPLEMENTARIES = modFlag("supplementaries",
        BuiltinFlags.SUPPLEMENTARIES_THROW.id(), "mod-supplementaries");
    public static final Scenario RECHISELED = modFlag("rechiseled",
        BuiltinFlags.RECHISELED_USE.id(), "mod-rechiseled");
    public static final Scenario ARS_ADDITIONS = modFlag("ars_additions",
        BuiltinFlags.ARS_ADDITIONS_SCROLL.id(), "mod-ars-additions");
    public static final Scenario TWILIGHT_FOREST = modFlag("twilightforest",
        BuiltinFlags.TF_PROJECTILE.id(), "mod-twilightforest");
    public static final Scenario MUTANT_MONSTERS = modFlag("mutantmonsters",
        BuiltinFlags.MUTANT_MOB_SPAWN.id(), "mod-mutantmonsters");
    public static final Scenario LUCKPERMS = modFlag("luckperms",
        // LuckPerms n'a pas de flag AG dedie ; on reuse PVP comme smoke test
        // (verifie que le flag registry repond quand LP est charge).
        BuiltinFlags.PVP.id(), "mod-luckperms");
    public static final Scenario YAWP = modFlag("yawp",
        BuiltinFlags.BLOCK_BREAK.id(), "mod-yawp");
}

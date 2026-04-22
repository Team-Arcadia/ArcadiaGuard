package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.IntFlag;
import com.arcadia.arcadiaguard.api.flag.ListFlag;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.ArrayList;
import java.util.List;

/**
 * Scenarios "smoke" auto-generes pour TOUS les flags du registry. Chaque scenario :
 *  1. Verifie que le flag est present dans le registry
 *  2. Le set sur une zone temporaire avec une valeur typee (false / 0 / liste vide)
 *  3. Verifie que la valeur est lue correctement
 *
 * <p>Garantit qu'aucun flag n'est "orphelin" (declare mais inutilisable). Ne valide PAS
 * le comportement runtime du flag (pour ca, voir les scenarios specialises).
 */
public final class AllFlagsSmokeScenarios {

    private AllFlagsSmokeScenarios() {}

    /** Genere un scenario smoke par flag du registry, retourne la liste complete. */
    public static List<Scenario> generateAll() {
        List<Scenario> out = new ArrayList<>();
        for (var flag : ArcadiaGuard.flagRegistry().all()) {
            String mod = flag.requiredMod();
            // requiredMod absent -> categorie selon prefix
            String cat = "smoke." + (mod.isEmpty() ? "vanilla" : mod);
            String id = "smoke-" + flag.id();

            Object value;
            if (flag instanceof BooleanFlag) value = false;
            else if (flag instanceof IntFlag) value = 0;
            else if (flag instanceof ListFlag) value = List.of("test_entry");
            else continue; // skip unknown flag types

            final String flagId = flag.id();
            final Object setValue = value;
            out.add(new Scenario() {
                @Override public String id() { return id; }
                @Override public String category() { return cat; }
                @Override public String requiredMod() { return mod; }
                @Override public ScenarioResult run(TestContext ctx) {
                    long start = System.nanoTime();
                    var f = ArcadiaGuard.flagRegistry().get(flagId);
                    if (f.isEmpty()) {
                        return ScenarioResult.fail(id(), "flag absent du registry", elapsed(start));
                    }
                    ctx.setupZone(flagId, setValue, 3);
                    var z = (ProtectedZone) ArcadiaGuard.zoneManager()
                        .get(ctx.level(), ctx.zoneName()).orElse(null);
                    if (z == null) {
                        return ScenarioResult.fail(id(), "zone non creee", elapsed(start));
                    }
                    Object got = z.flagValues().get(flagId);
                    if (!java.util.Objects.equals(setValue, got)) {
                        return ScenarioResult.fail(id(),
                            "expected " + setValue + " got " + got, elapsed(start));
                    }
                    return ScenarioResult.pass(id(), elapsed(start));
                }
            });
        }
        return out;
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}

package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Scenarios verifiant le CRUD zone + FlagRegistry + FlagResolver. */
public final class ZoneCrudScenarios {

    private ZoneCrudScenarios() {}

    public static final Scenario ZONE_CREATE_DELETE = new Scenario() {
        @Override public String id() { return "zone-create-delete"; }
        @Override public String category() { return "core"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            String name = "selftest-crud-" + UUID.randomUUID().toString().substring(0, 8);
            BlockPos p = ctx.player().blockPosition();
            ProtectedZone z = new ProtectedZone(name,
                ctx.level().dimension().location().toString(),
                p.offset(-5, -5, -5), p.offset(5, 5, 5));

            if (!ArcadiaGuard.zoneManager().add(ctx.level(), z)) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                return ScenarioResult.fail(id(), "zoneManager.add returned false", ms);
            }
            if (ArcadiaGuard.zoneManager().get(ctx.level(), name).isEmpty()) {
                ArcadiaGuard.zoneManager().remove(ctx.level(), name);
                long ms = (System.nanoTime() - start) / 1_000_000;
                return ScenarioResult.fail(id(), "zone introuvable apres add", ms);
            }
            if (!ArcadiaGuard.zoneManager().remove(ctx.level(), name)) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                return ScenarioResult.fail(id(), "zoneManager.remove returned false", ms);
            }
            if (ArcadiaGuard.zoneManager().get(ctx.level(), name).isPresent()) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                return ScenarioResult.fail(id(), "zone presente apres remove", ms);
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            return ScenarioResult.pass(id(), ms);
        }
    };

    public static final Scenario FLAG_REGISTRY_COUNT = new Scenario() {
        @Override public String id() { return "flag-registry-count"; }
        @Override public String category() { return "core"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            int count = ArcadiaGuard.flagRegistry().all().size();
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (count < 80) {
                return ScenarioResult.fail(id(),
                    "flag count=" + count + " (expected >= 80)", ms);
            }
            return ScenarioResult.pass(id(), count + " flags registered", ms);
        }
    };

    public static final Scenario FLAG_SET_RESET = new Scenario() {
        @Override public String id() { return "flag-set-reset"; }
        @Override public String category() { return "core"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            ctx.setupEmptyZone(5);
            ctx.setFlag(BuiltinFlags.PVP.id(), false);

            var zone = (ProtectedZone) ArcadiaGuard.zoneManager()
                .get(ctx.level(), ctx.zoneName()).orElseThrow();
            Object v = zone.flagValues().get(BuiltinFlags.PVP.id());
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (!Boolean.FALSE.equals(v)) {
                return ScenarioResult.fail(id(), "flag value != false: " + v, ms);
            }

            ArcadiaGuard.zoneManager().resetFlag(ctx.level(), ctx.zoneName(), BuiltinFlags.PVP.id());
            if (zone.flagValues().containsKey(BuiltinFlags.PVP.id())) {
                return ScenarioResult.fail(id(), "flag toujours present apres reset", ms);
            }
            return ScenarioResult.pass(id(), ms);
        }
    };
}

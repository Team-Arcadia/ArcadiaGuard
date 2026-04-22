package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * Scenarios pour les flags blocks (break/place/interact).
 * Utilise le player executant pour casser/poser un bloc dans une zone temporaire et
 * verifier que l'action est bien bloquee (deny) ou autorisee (allow).
 */
public final class BlockFlagScenarios {

    private BlockFlagScenarios() {}

    // ── block-break deny ──────────────────────────────────────────────────────

    public static final Scenario BLOCK_BREAK_DENY = new Scenario() {
        @Override public String id() { return "block-break-deny"; }
        @Override public String category() { return "blocks"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            // Skip si player bypass (OP sans Debug actif) : le handler ne s'applique
            // pas et le test rapporterait un faux fail.
            if (com.arcadia.arcadiaguard.ArcadiaGuard.guardService().shouldBypass(ctx.player())) {
                return ScenarioResult.skip(id(),
                    "player bypass actif (OP sans Debug). Active Debug dans GUI pour tester.");
            }
            ctx.setupZone(BuiltinFlags.BLOCK_BREAK.id(), false, 8);

            BlockPos pos = ctx.testPos();
            var snapshot = ctx.snapshotBlock(pos);
            ctx.setBlock(pos, Blocks.STONE);
            boolean destroyed = ctx.level().destroyBlock(pos, false, ctx.player());

            long ms = (System.nanoTime() - start) / 1_000_000;
            ctx.restoreBlock(pos, snapshot);

            if (destroyed && ctx.level().getBlockState(pos).isAir()) {
                return ScenarioResult.fail(id(),
                    "block-break=deny : le bloc a ete casse malgre le flag", ms);
            }
            return ScenarioResult.pass(id(), ms);
        }
    };

    // ── block-break allow (sanity check) ──────────────────────────────────────

    public static final Scenario BLOCK_BREAK_ALLOW = new Scenario() {
        @Override public String id() { return "block-break-allow"; }
        @Override public String category() { return "blocks"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            ctx.setupZone(BuiltinFlags.BLOCK_BREAK.id(), true, 8);

            BlockPos pos = ctx.testPos();
            var snapshot = ctx.snapshotBlock(pos);
            ctx.setBlock(pos, Blocks.STONE);
            boolean destroyed = ctx.level().destroyBlock(pos, false, ctx.player());

            long ms = (System.nanoTime() - start) / 1_000_000;
            ctx.restoreBlock(pos, snapshot);

            if (!destroyed) {
                return ScenarioResult.fail(id(),
                    "block-break=allow : le bloc n'a pas ete casse alors qu'autorise", ms);
            }
            return ScenarioResult.pass(id(), ms);
        }
    };

    // ── block-place deny ──────────────────────────────────────────────────────

    public static final Scenario BLOCK_PLACE_DENY = new Scenario() {
        @Override public String id() { return "block-place-deny"; }
        @Override public String category() { return "blocks"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            ctx.setupZone(BuiltinFlags.BLOCK_PLACE.id(), false, 8);

            // Place via un hook event : on simule en verifiant que le handler AG
            // voit la zone et refuse. Pour un vrai test de placement il faudrait
            // UseOnContext avec ItemStack. Ici on verifie juste que la zone + flag
            // sont correctement configures (smoke test du pipeline).
            long ms = (System.nanoTime() - start) / 1_000_000;
            var zoneOpt = com.arcadia.arcadiaguard.ArcadiaGuard.zoneManager()
                .get(ctx.level(), ctx.zoneName());
            if (zoneOpt.isEmpty()) {
                return ScenarioResult.fail(id(), "zone introuvable apres setup", ms);
            }
            Object val = ((com.arcadia.arcadiaguard.zone.ProtectedZone) zoneOpt.get())
                .flagValues().get(BuiltinFlags.BLOCK_PLACE.id());
            if (!Boolean.FALSE.equals(val)) {
                return ScenarioResult.fail(id(),
                    "flag block-place devrait etre false, got " + val, ms);
            }
            return ScenarioResult.pass(id(), "flag correctement configure (placement reel non teste)", ms);
        }
    };
}

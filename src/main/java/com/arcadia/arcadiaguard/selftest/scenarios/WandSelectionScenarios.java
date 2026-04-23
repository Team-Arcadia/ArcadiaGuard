package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.item.WandItem;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import net.minecraft.core.BlockPos;

/**
 * Scenarios in-game pour FR09 : selection pos1/pos2 via le WandItem.
 * Teste le stockage des positions dans les ConcurrentHashMap statiques de WandItem.
 */
public final class WandSelectionScenarios {

    private WandSelectionScenarios() {}

    public static final Scenario WAND_POS1_SET = new Scenario() {
        @Override public String id() { return "wand-pos1-set"; }
        @Override public String category() { return "wand"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            var uuid = ctx.player().getUUID();
            BlockPos expected = ctx.player().blockPosition().offset(10, 0, 10);

            WandItem.clearSelection(uuid);
            WandItem.setPos1(uuid, expected);
            BlockPos actual = WandItem.getPos1(uuid);
            WandItem.clearSelection(uuid);

            long ms = (System.nanoTime() - s) / 1_000_000;
            if (!expected.equals(actual)) {
                return ScenarioResult.fail(id(), "pos1 attendu=" + expected + " obtenu=" + actual, ms);
            }
            return ScenarioResult.pass(id(), ms);
        }
    };

    public static final Scenario WAND_POS2_SET = new Scenario() {
        @Override public String id() { return "wand-pos2-set"; }
        @Override public String category() { return "wand"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            var uuid = ctx.player().getUUID();
            BlockPos expected = ctx.player().blockPosition().offset(-10, 5, -10);

            WandItem.clearSelection(uuid);
            WandItem.setPos2(uuid, expected);
            BlockPos actual = WandItem.getPos2(uuid);
            WandItem.clearSelection(uuid);

            long ms = (System.nanoTime() - s) / 1_000_000;
            if (!expected.equals(actual)) {
                return ScenarioResult.fail(id(), "pos2 attendu=" + expected + " obtenu=" + actual, ms);
            }
            return ScenarioResult.pass(id(), ms);
        }
    };

    public static final Scenario WAND_BOTH_INDEPENDENT = new Scenario() {
        @Override public String id() { return "wand-pos1-pos2-independent"; }
        @Override public String category() { return "wand"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            var uuid = ctx.player().getUUID();
            BlockPos p1 = ctx.player().blockPosition().offset(-5, 0, 0);
            BlockPos p2 = ctx.player().blockPosition().offset(5, 0, 0);

            WandItem.clearSelection(uuid);
            WandItem.setPos1(uuid, p1);
            WandItem.setPos2(uuid, p2);

            boolean ok = p1.equals(WandItem.getPos1(uuid)) && p2.equals(WandItem.getPos2(uuid));
            WandItem.clearSelection(uuid);

            long ms = (System.nanoTime() - s) / 1_000_000;
            if (!ok) return ScenarioResult.fail(id(), "pos1/pos2 se sont interferes", ms);
            return ScenarioResult.pass(id(), ms);
        }
    };

    public static final Scenario WAND_CLEAR_SELECTION = new Scenario() {
        @Override public String id() { return "wand-clear-selection"; }
        @Override public String category() { return "wand"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            var uuid = ctx.player().getUUID();

            WandItem.setPos1(uuid, ctx.player().blockPosition());
            WandItem.setPos2(uuid, ctx.player().blockPosition().above(10));
            WandItem.clearSelection(uuid);

            long ms = (System.nanoTime() - s) / 1_000_000;
            if (WandItem.getPos1(uuid) != null || WandItem.getPos2(uuid) != null) {
                return ScenarioResult.fail(id(), "clearSelection n'a pas efface les positions", ms);
            }
            return ScenarioResult.pass(id(), ms);
        }
    };
}

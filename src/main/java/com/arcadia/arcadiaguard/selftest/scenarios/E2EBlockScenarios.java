package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * Vrais E2E pour les flags blocks (RightClickBlock-based).
 * Pattern : place le bloc cible, simule un right-click, observe si l'event est cancel.
 */
public final class E2EBlockScenarios {

    private E2EBlockScenarios() {}
    private static long ms(long s) { return (System.nanoTime() - s) / 1_000_000; }

    private static ScenarioResult interactDeny(TestContext ctx, String id, String flagId,
                                               net.minecraft.world.level.block.Block block) {
        long s = System.nanoTime();
        if (com.arcadia.arcadiaguard.ArcadiaGuard.guardService().shouldBypass(ctx.player())) {
            return ScenarioResult.skip(id, "player bypass actif (active Debug)");
        }
        ctx.setupZone(flagId, false, 8);
        BlockPos pos = ctx.testPos();
        BlockState snap = ctx.snapshotBlock(pos);
        ctx.setBlock(pos, block);

        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var result = CommonHooks.onRightClickBlock(ctx.player(), InteractionHand.MAIN_HAND, pos, hit);
        ctx.restoreBlock(pos, snap);
        long elapsed = ms(s);
        if (result.isCanceled()) return ScenarioResult.pass(id, elapsed);
        return ScenarioResult.fail(id, "RightClickBlock non cancel pour " + flagId, elapsed);
    }

    public static final Scenario DOOR_DENY = new Scenario() {
        @Override public String id() { return "door-deny-e2e"; }
        @Override public String category() { return "blocks"; }
        @Override public ScenarioResult run(TestContext ctx) {
            return interactDeny(ctx, id(), BuiltinFlags.DOOR.id(), Blocks.OAK_DOOR);
        }
    };
    public static final Scenario TRAPDOOR_DENY = new Scenario() {
        @Override public String id() { return "trapdoor-deny-e2e"; }
        @Override public String category() { return "blocks"; }
        @Override public ScenarioResult run(TestContext ctx) {
            return interactDeny(ctx, id(), BuiltinFlags.TRAPDOOR.id(), Blocks.OAK_TRAPDOOR);
        }
    };
    public static final Scenario BUTTON_DENY = new Scenario() {
        @Override public String id() { return "button-deny-e2e"; }
        @Override public String category() { return "blocks"; }
        @Override public ScenarioResult run(TestContext ctx) {
            return interactDeny(ctx, id(), BuiltinFlags.BUTTON.id(), Blocks.STONE_BUTTON);
        }
    };
    public static final Scenario LEVER_DENY = new Scenario() {
        @Override public String id() { return "lever-deny-e2e"; }
        @Override public String category() { return "blocks"; }
        @Override public ScenarioResult run(TestContext ctx) {
            return interactDeny(ctx, id(), BuiltinFlags.LEVER.id(), Blocks.LEVER);
        }
    };
    public static final Scenario GATE_DENY = new Scenario() {
        @Override public String id() { return "gate-deny-e2e"; }
        @Override public String category() { return "blocks"; }
        @Override public ScenarioResult run(TestContext ctx) {
            return interactDeny(ctx, id(), BuiltinFlags.GATE.id(), Blocks.OAK_FENCE_GATE);
        }
    };
    public static final Scenario CONTAINER_ACCESS_DENY = new Scenario() {
        @Override public String id() { return "container-access-deny-e2e"; }
        @Override public String category() { return "blocks"; }
        @Override public ScenarioResult run(TestContext ctx) {
            return interactDeny(ctx, id(), BuiltinFlags.CONTAINER_ACCESS.id(), Blocks.CHEST);
        }
    };
}

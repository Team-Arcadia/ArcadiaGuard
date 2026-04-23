package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * Scenarios in-game pour FR05/FR24 : zones enfant et sous-zones.
 * Verifie que les flags d'une zone enfant surchargent ceux de la zone parent.
 */
public final class ExceptionZoneScenarios {

    private ExceptionZoneScenarios() {}

    public static final Scenario CHILD_OVERRIDES_PARENT_FLAG = new Scenario() {
        @Override public String id() { return "child-overrides-parent"; }
        @Override public String category() { return "exception-zones"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            if (ArcadiaGuard.guardService().shouldBypass(ctx.player())) {
                return ScenarioResult.skip(id(), "player bypass actif (active Debug)");
            }

            String parentName = "selftest-parent-" + UUID.randomUUID().toString().substring(0, 6);
            String childName  = "selftest-child-"  + UUID.randomUUID().toString().substring(0, 6);
            String dim = ctx.level().dimension().location().toString();
            BlockPos center = ctx.player().blockPosition();

            // Zone parent : rayon 10, door=deny
            ProtectedZone parent = new ProtectedZone(parentName, dim,
                center.offset(-10, -3, -10), center.offset(10, 10, 10));
            parent.setFlag(BuiltinFlags.DOOR.id(), false);
            ArcadiaGuard.zoneManager().add(ctx.level(), parent);

            // Zone enfant : rayon 3 (a l'interieur du parent), door=allow
            ProtectedZone child = new ProtectedZone(childName, dim,
                center.offset(-3, -2, -3), center.offset(3, 5, 3));
            child.setFlag(BuiltinFlags.DOOR.id(), true);
            child.setParent(parentName);
            ArcadiaGuard.zoneManager().add(ctx.level(), child);

            BlockPos pos = ctx.testPos();
            BlockState snap = ctx.level().getBlockState(pos);
            ctx.level().setBlockAndUpdate(pos, Blocks.OAK_DOOR.defaultBlockState());
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            var result = CommonHooks.onRightClickBlock(ctx.player(), InteractionHand.MAIN_HAND, pos, hit);
            ctx.level().setBlockAndUpdate(pos, snap);

            ArcadiaGuard.zoneManager().remove(ctx.level(), childName);
            ArcadiaGuard.zoneManager().remove(ctx.level(), parentName);

            long ms = (System.nanoTime() - s) / 1_000_000;
            if (result.isCanceled()) {
                return ScenarioResult.fail(id(),
                    "la zone enfant (door=allow) aurait du surcharger la zone parent (door=deny)", ms);
            }
            return ScenarioResult.pass(id(), "child door=allow surcharge parent door=deny", ms);
        }
    };

    public static final Scenario PARENT_FLAG_INHERITED_WHEN_CHILD_UNSET = new Scenario() {
        @Override public String id() { return "parent-flag-inherited"; }
        @Override public String category() { return "exception-zones"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            if (ArcadiaGuard.guardService().shouldBypass(ctx.player())) {
                return ScenarioResult.skip(id(), "player bypass actif (active Debug)");
            }

            String parentName = "selftest-inh-p-" + UUID.randomUUID().toString().substring(0, 6);
            String childName  = "selftest-inh-c-" + UUID.randomUUID().toString().substring(0, 6);
            String dim = ctx.level().dimension().location().toString();
            BlockPos center = ctx.player().blockPosition();

            // Zone parent : rayon 10, door=deny
            ProtectedZone parent = new ProtectedZone(parentName, dim,
                center.offset(-10, -3, -10), center.offset(10, 10, 10));
            parent.setFlag(BuiltinFlags.DOOR.id(), false);
            ArcadiaGuard.zoneManager().add(ctx.level(), parent);

            // Zone enfant : rayon 3, AUCUN flag (herite du parent)
            ProtectedZone child = new ProtectedZone(childName, dim,
                center.offset(-3, -2, -3), center.offset(3, 5, 3));
            child.setParent(parentName);
            ArcadiaGuard.zoneManager().add(ctx.level(), child);

            BlockPos pos = ctx.testPos();
            BlockState snap = ctx.level().getBlockState(pos);
            ctx.level().setBlockAndUpdate(pos, Blocks.OAK_DOOR.defaultBlockState());
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            var result = CommonHooks.onRightClickBlock(ctx.player(), InteractionHand.MAIN_HAND, pos, hit);
            ctx.level().setBlockAndUpdate(pos, snap);

            ArcadiaGuard.zoneManager().remove(ctx.level(), childName);
            ArcadiaGuard.zoneManager().remove(ctx.level(), parentName);

            long ms = (System.nanoTime() - s) / 1_000_000;
            if (!result.isCanceled()) {
                return ScenarioResult.fail(id(),
                    "le flag door=deny du parent aurait du etre herite par la zone enfant", ms);
            }
            return ScenarioResult.pass(id(), "flag parent herite correctement par zone enfant sans override", ms);
        }
    };
}

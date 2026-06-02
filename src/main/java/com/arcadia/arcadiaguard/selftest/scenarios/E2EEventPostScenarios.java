package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Vrais E2E qui postent directement l'event sur EVENT_BUS et observent les flags
 * (canceled / cancelled / removed entities). Pattern : creer event manuellement,
 * post, verifier le state.
 */
public final class E2EEventPostScenarios {

    private E2EEventPostScenarios() {}
    private static long ms(long s) { return (System.nanoTime() - s) / 1_000_000; }

    public static final Scenario VILLAGER_SPAWN_DENY = new Scenario() {
        @Override public String id() { return "villager-spawn-deny-e2e"; }
        @Override public String category() { return "mobs"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            ctx.setupZone(BuiltinFlags.VILLAGER_SPAWN.id(), false, 8);
            Villager v = EntityType.VILLAGER.create(ctx.level(), EntitySpawnReason.TRIGGERED);
            if (v == null) return ScenarioResult.fail(id(), "create() null", ms(s));
            BlockPos pos = ctx.player().blockPosition().offset(3, 0, 0);
            v.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            var event = new FinalizeSpawnEvent(v, ctx.level(),
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                ctx.level().getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null, null);
            NeoForge.EVENT_BUS.post(event);
            return (event.isSpawnCancelled() || event.isCanceled())
                ? ScenarioResult.pass(id(), ms(s))
                : ScenarioResult.fail(id(), "ni isSpawnCancelled ni isCanceled", ms(s));
        }
    };

    private static ScenarioResult explosionDeny(TestContext ctx, String id, String flagId,
                                                 EntityType<?> sourceType) {
        long s = System.nanoTime();
        ctx.setupZone(flagId, false, 8);
        BlockPos center = ctx.player().blockPosition().offset(3, 0, 0);
        Entity source = sourceType != null ? sourceType.create(ctx.level(), EntitySpawnReason.TRIGGERED) : null;
        if (source != null) {
            source.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0, 0);
        }
        ServerExplosion explosion = new ServerExplosion(ctx.level(), source, null, null,
            Vec3.atCenterOf(center), 4f, false, Explosion.BlockInteraction.DESTROY);
        var event = new ExplosionEvent.Start((Level) ctx.level(), explosion);
        NeoForge.EVENT_BUS.post(event);
        long elapsed = ms(s);
        if (source != null) source.discard();
        if (event.isCanceled()) return ScenarioResult.pass(id, elapsed);
        return ScenarioResult.fail(id,
            flagId + "=deny mais ExplosionEvent.Start non cancel", elapsed);
    }

    public static final Scenario CREEPER_EXPLOSION_DENY = new Scenario() {
        @Override public String id() { return "creeper-explosion-deny-e2e"; }
        @Override public String category() { return "explosions"; }
        @Override public ScenarioResult run(TestContext ctx) {
            return explosionDeny(ctx, id(), BuiltinFlags.CREEPER_EXPLOSION.id(), EntityType.CREEPER);
        }
    };

    public static final Scenario TNT_EXPLOSION_DENY = new Scenario() {
        @Override public String id() { return "tnt-explosion-deny-e2e"; }
        @Override public String category() { return "explosions"; }
        @Override public ScenarioResult run(TestContext ctx) {
            return explosionDeny(ctx, id(), BuiltinFlags.TNT_EXPLOSION.id(), EntityType.TNT);
        }
    };

    public static final Scenario BLOCK_EXPLOSION_DENY = new Scenario() {
        @Override public String id() { return "block-explosion-deny-e2e"; }
        @Override public String category() { return "explosions"; }
        @Override public ScenarioResult run(TestContext ctx) {
            return explosionDeny(ctx, id(), BuiltinFlags.BLOCK_EXPLOSION.id(), null);
        }
    };

    public static final Scenario FARMLAND_TRAMPLE_DENY = new Scenario() {
        @Override public String id() { return "farmland-trample-deny-e2e"; }
        @Override public String category() { return "env"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            ctx.setupZone(BuiltinFlags.FARMLAND_TRAMPLE.id(), false, 8);
            BlockPos pos = ctx.testPos();
            var snap = ctx.snapshotBlock(pos);
            ctx.setBlock(pos, Blocks.FARMLAND);
            BlockState state = ctx.level().getBlockState(pos);
            var event = new BlockEvent.FarmlandTrampleEvent(ctx.level(), pos, state, 1.0f, ctx.player());
            NeoForge.EVENT_BUS.post(event);
            ctx.restoreBlock(pos, snap);
            return event.isCanceled()
                ? ScenarioResult.pass(id(), ms(s))
                : ScenarioResult.fail(id(), "FarmlandTrampleEvent non cancel", ms(s));
        }
    };
}

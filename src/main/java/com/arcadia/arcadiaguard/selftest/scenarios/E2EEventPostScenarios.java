package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
            Villager v = EntityType.VILLAGER.create(ctx.level());
            if (v == null) return ScenarioResult.fail(id(), "create() null", ms(s));
            BlockPos pos = ctx.player().blockPosition().offset(3, 0, 0);
            v.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            var event = new FinalizeSpawnEvent(v, ctx.level(),
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                ctx.level().getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
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
        // Setup quelques blocs autour pour qu'ils soient affected.
        var savedStates = new java.util.HashMap<BlockPos, BlockState>();
        var positions = new java.util.ArrayList<BlockPos>();
        for (var off : new BlockPos[]{ center.east(), center.west(), center.north(), center.south() }) {
            savedStates.put(off, ctx.snapshotBlock(off));
            ctx.setBlock(off, Blocks.WHITE_WOOL);
            positions.add(off);
        }
        Entity source = sourceType != null ? sourceType.create(ctx.level()) : null;
        if (source != null) {
            source.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0, 0);
        }
        Explosion explosion = new Explosion(ctx.level(), source,
            center.getX() + 0.5, center.getY(), center.getZ() + 0.5,
            4f, false, Explosion.BlockInteraction.DESTROY);
        var affected = new ArrayList<>(positions);
        var entities = new ArrayList<Entity>();
        var event = new ExplosionEvent.Detonate((Level) ctx.level(), explosion, entities);
        NeoForge.EVENT_BUS.post(event);
        long elapsed = ms(s);
        // Cleanup
        for (var e : savedStates.entrySet()) ctx.restoreBlock(e.getKey(), e.getValue());
        // Si AG a clear les affected blocks, l'explosion ne touchera rien.
        if (event.getAffectedBlocks().isEmpty()) return ScenarioResult.pass(id, elapsed);
        return ScenarioResult.fail(id,
            flagId + "=deny mais affected blocks non vides : " + event.getAffectedBlocks().size(), elapsed);
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

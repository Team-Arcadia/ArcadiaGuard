package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

/** Vrais E2E pour combat (mob-damage, attack-monsters, ender-pearl, etc.) */
public final class E2ECombatScenarios {

    private E2ECombatScenarios() {}
    private static long ms(long s) { return (System.nanoTime() - s) / 1_000_000; }

    private static ScenarioResult attackMobDeny(TestContext ctx, String id, String flagId,
                                                 EntityType<? extends LivingEntity> type) {
        long s = System.nanoTime();
        if (com.arcadia.arcadiaguard.ArcadiaGuard.guardService().shouldBypass(ctx.player())) {
            return ScenarioResult.skip(id, "player bypass actif (active Debug)");
        }
        ctx.setupZone(flagId, false, 8);
        var mob = type.create(ctx.level());
        if (mob == null) return ScenarioResult.fail(id, "create() null", ms(s));
        Vec3 pos = ctx.player().position();
        mob.moveTo(pos.x + 2, pos.y, pos.z, 0, 0);
        ctx.level().addFreshEntity(mob);
        float hpBefore = mob.getHealth();
        ctx.player().attack(mob);
        float hpAfter = mob.getHealth();
        mob.discard();
        long elapsed = ms(s);
        if (hpAfter < hpBefore - 0.001f) {
            return ScenarioResult.fail(id,
                flagId + "=deny mais dommage inflige : " + hpBefore + " -> " + hpAfter, elapsed);
        }
        return ScenarioResult.pass(id, elapsed);
    }

    public static final Scenario ATTACK_MONSTERS_DENY = new Scenario() {
        @Override public String id() { return "attack-monsters-deny-e2e"; }
        @Override public String category() { return "combat"; }
        @Override public ScenarioResult run(TestContext ctx) {
            return attackMobDeny(ctx, id(), BuiltinFlags.ATTACK_MONSTERS.id(), EntityType.ZOMBIE);
        }
    };

    public static final Scenario MOB_DAMAGE_DENY = new Scenario() {
        @Override public String id() { return "mob-damage-deny-e2e"; }
        @Override public String category() { return "combat"; }
        @Override public ScenarioResult run(TestContext ctx) {
            // mob-damage = damage subi PAR le joueur depuis un mob. On simule : zombie attack player.
            long s = System.nanoTime();
            if (com.arcadia.arcadiaguard.ArcadiaGuard.guardService().shouldBypass(ctx.player())) {
                return ScenarioResult.skip(id(), "player bypass actif");
            }
            ctx.setupZone(BuiltinFlags.MOB_DAMAGE.id(), false, 8);
            Zombie zombie = EntityType.ZOMBIE.create(ctx.level());
            if (zombie == null) return ScenarioResult.fail(id(), "create() null", ms(s));
            var pos = ctx.player().position();
            zombie.moveTo(pos.x + 1, pos.y, pos.z, 0, 0);
            ctx.level().addFreshEntity(zombie);
            float hpBefore = ctx.player().getHealth();
            ctx.player().hurt(ctx.level().damageSources().mobAttack(zombie), 1.0f);
            float hpAfter = ctx.player().getHealth();
            ctx.player().setHealth(hpBefore);
            zombie.discard();
            long elapsed = ms(s);
            if (hpAfter < hpBefore - 0.001f) {
                return ScenarioResult.fail(id(),
                    "mob-damage=deny mais damage applique : " + hpBefore + " -> " + hpAfter, elapsed);
            }
            return ScenarioResult.pass(id(), elapsed);
        }
    };

    public static final Scenario PLAYER_DAMAGE_DENY = new Scenario() {
        @Override public String id() { return "player-damage-deny-e2e"; }
        @Override public String category() { return "combat"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            if (com.arcadia.arcadiaguard.ArcadiaGuard.guardService().shouldBypass(ctx.player())) {
                return ScenarioResult.skip(id(), "player bypass actif");
            }
            ctx.setupZone(BuiltinFlags.PLAYER_DAMAGE.id(), false, 8);
            float hpBefore = ctx.player().getHealth();
            ctx.player().hurt(ctx.level().damageSources().generic(), 1.0f);
            float hpAfter = ctx.player().getHealth();
            ctx.player().setHealth(hpBefore);
            long elapsed = ms(s);
            if (hpAfter < hpBefore - 0.001f) {
                return ScenarioResult.fail(id(),
                    "player-damage=deny mais damage applique", elapsed);
            }
            return ScenarioResult.pass(id(), elapsed);
        }
    };
}

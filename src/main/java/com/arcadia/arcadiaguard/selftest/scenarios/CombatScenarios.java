package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.EntityType;

/** Scenarios combat : PvP, attack animals, invincible, player damage. */
public final class CombatScenarios {

    private CombatScenarios() {}

    public static final Scenario INVINCIBLE_FLAG = new Scenario() {
        @Override public String id() { return "invincible-allow"; }
        @Override public String category() { return "combat"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            ctx.setupZone(BuiltinFlags.INVINCIBLE.id(), true, 8);

            float hpBefore = ctx.player().getHealth();
            // Applique 1 HP de damage generic. Si invincible=allow fonctionne, le handler
            // AG doit cancel ou setAmount(0).
            ctx.player().hurt(
                ctx.level().damageSources().generic(), 1.0f);
            float hpAfter = ctx.player().getHealth();

            // Restaure en cas de dommage residuel.
            ctx.player().setHealth(hpBefore);
            long ms = (System.nanoTime() - start) / 1_000_000;

            if (hpAfter < hpBefore - 0.001f) {
                return ScenarioResult.fail(id(),
                    "invincible=allow mais damage applique : " + hpBefore + " -> " + hpAfter, ms);
            }
            return ScenarioResult.pass(id(), ms);
        }
    };

    public static final Scenario ATTACK_ANIMALS_DENY = new Scenario() {
        @Override public String id() { return "attack-animals-deny"; }
        @Override public String category() { return "combat"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            if (com.arcadia.arcadiaguard.ArcadiaGuard.guardService().shouldBypass(ctx.player())) {
                return ScenarioResult.skip(id(),
                    "player bypass actif. Active Debug dans GUI pour tester.");
            }
            ctx.setupZone(BuiltinFlags.ATTACK_ANIMALS.id(), false, 8);

            Pig pig = EntityType.PIG.create(ctx.level());
            if (pig == null) return ScenarioResult.fail(id(), "pig.create null", 0);
            var pos = ctx.player().position();
            pig.moveTo(pos.x + 2, pos.y, pos.z, 0, 0);
            ctx.level().addFreshEntity(pig);

            float pigHpBefore = pig.getHealth();
            ctx.player().attack(pig);
            float pigHpAfter = pig.getHealth();
            pig.discard();
            long ms = (System.nanoTime() - start) / 1_000_000;

            if (pigHpAfter < pigHpBefore - 0.001f) {
                return ScenarioResult.fail(id(),
                    "attack-animals=deny mais dommage inflige : " + pigHpBefore + " -> " + pigHpAfter, ms);
            }
            return ScenarioResult.pass(id(), ms);
        }
    };

    public static final Scenario FALL_DAMAGE_ALLOW = new Scenario() {
        @Override public String id() { return "fall-damage-allow"; }
        @Override public String category() { return "combat"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            // Skip si player en creatif/spectator — invulnerable nativement aux dmg.
            if (ctx.player().isCreative() || ctx.player().isSpectator()) {
                return ScenarioResult.skip(id(),
                    "player en creatif/spectator (invulnerable nativement). Passe en survie.");
            }
            ctx.setupZone(BuiltinFlags.FALL_DAMAGE.id(), true, 8);

            float hpBefore = ctx.player().getHealth();
            DamageSource fall = ctx.level().damageSources().fall();
            ctx.player().hurt(fall, 2.0f);
            float hpAfter = ctx.player().getHealth();
            ctx.player().setHealth(hpBefore);

            long ms = (System.nanoTime() - start) / 1_000_000;
            if (hpAfter >= hpBefore - 0.001f) {
                return ScenarioResult.fail(id(),
                    "fall-damage=allow mais pas de dommage applique", ms);
            }
            return ScenarioResult.pass(id(), ms);
        }
    };
}

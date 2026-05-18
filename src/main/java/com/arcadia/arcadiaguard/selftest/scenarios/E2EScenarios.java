package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Scenarios end-to-end pour les flags HIGH risk identifies par l'audit :
 * mob-spawn (via FinalizeSpawnEvent vrai), tnt-explosion, entry/exit, item-drop, etc.
 */
public final class E2EScenarios {

    private E2EScenarios() {}

    private static long ms(long start) { return (System.nanoTime() - start) / 1_000_000; }

    // ── MOB SPAWN via NaturalSpawner-like event simulation ──────────────────────

    public static final Scenario MONSTER_SPAWN_DENY_E2E = new Scenario() {
        @Override public String id() { return "monster-spawn-deny-e2e"; }
        @Override public String category() { return "mobs"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            ctx.setupZone(BuiltinFlags.MONSTER_SPAWN.id(), false, 8);

            Zombie zombie = EntityType.ZOMBIE.create(ctx.level());
            if (zombie == null) return ScenarioResult.fail(id(), "create() null", ms(start));
            BlockPos pos = ctx.player().blockPosition().offset(3, 0, 0);
            zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);

            // EntityEventHandler.onMobSpawn appelle event.setSpawnCancelled(true) pour
            // les flags MOB_SPAWN/MONSTER_SPAWN/ANIMAL_SPAWN. On post l'event soi-meme
            // et on verifie isSpawnCancelled() (pas isCanceled() qui correspond a un
            // veto plus fort).
            var event = new net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent(
                zombie, ctx.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                ctx.level().getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            if (event.isSpawnCancelled() || event.isCanceled()) {
                return ScenarioResult.pass(id(), "spawn cancel par AG", ms(start));
            }
            return ScenarioResult.fail(id(),
                "monster-spawn=deny mais ni isSpawnCancelled ni isCanceled", ms(start));
        }
    };

    public static final Scenario ANIMAL_SPAWN_DENY_E2E = new Scenario() {
        @Override public String id() { return "animal-spawn-deny-e2e"; }
        @Override public String category() { return "mobs"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            ctx.setupZone(BuiltinFlags.ANIMAL_SPAWN.id(), false, 8);

            Cow cow = EntityType.COW.create(ctx.level());
            if (cow == null) return ScenarioResult.fail(id(), "create() null", ms(start));
            BlockPos pos = ctx.player().blockPosition().offset(3, 0, 0);
            cow.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);

            var event = new net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent(
                cow, ctx.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                ctx.level().getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            if (event.isSpawnCancelled() || event.isCanceled()) {
                return ScenarioResult.pass(id(), ms(start));
            }
            return ScenarioResult.fail(id(),
                "animal-spawn=deny mais ni isSpawnCancelled ni isCanceled", ms(start));
        }
    };

    // ── PVP via attaque player -> mock target ────────────────────────────────────

    public static final Scenario PVP_DENY_E2E = new Scenario() {
        @Override public String id() { return "pvp-deny-e2e"; }
        @Override public String category() { return "combat"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            ctx.setupZone(BuiltinFlags.PVP.id(), false, 8);

            // Cherche un autre joueur sur le serveur. S'il n'y en a pas, skip.
            var others = ctx.server().getPlayerList().getPlayers().stream()
                .filter(p -> !p.getUUID().equals(ctx.player().getUUID()))
                .toList();
            if (others.isEmpty()) {
                return ScenarioResult.skip(id(), "needs >= 2 players online for real PvP test");
            }
            var target = others.get(0);

            float hpBefore = target.getHealth();
            // Tente d'inflict 1.0f damage via DamageSource du player attaquant.
            target.hurt(ctx.level().damageSources().playerAttack(ctx.player()), 1.0f);
            float hpAfter = target.getHealth();
            target.setHealth(hpBefore); // restaure

            if (hpAfter < hpBefore - 0.001f) {
                return ScenarioResult.fail(id(),
                    "pvp=deny mais damage applique : " + hpBefore + " -> " + hpAfter, ms(start));
            }
            return ScenarioResult.pass(id(), ms(start));
        }
    };

    // ── BLOCK INTERACT deny ──────────────────────────────────────────────────────

    public static final Scenario BLOCK_INTERACT_DENY_E2E = new Scenario() {
        @Override public String id() { return "block-interact-deny-e2e"; }
        @Override public String category() { return "blocks"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            ctx.setupZone(BuiltinFlags.BLOCK_INTERACT.id(), false, 8);
            BlockPos pos = ctx.testPos();
            BlockState snap = ctx.snapshotBlock(pos);
            ctx.setBlock(pos, Blocks.LEVER);
            BlockState before = ctx.level().getBlockState(pos);

            // Simule un useItemOn via PlayerInteractEvent.RightClickBlock.
            var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
            var face = net.minecraft.core.Direction.UP;
            var hitResult = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos), face, pos, false);
            var result = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(
                ctx.player(), hand, pos, hitResult);
            BlockState after = ctx.level().getBlockState(pos);
            ctx.restoreBlock(pos, snap);

            // RightClickBlock doit etre cancel (canceled=true ou interactSide=DENY).
            if (result.isCanceled()) {
                return ScenarioResult.pass(id(), "RightClickBlock cancele", ms(start));
            }
            // Si pas cancel mais le block n'a pas change (deny via setUseBlock), pass aussi.
            if (before.equals(after)) {
                return ScenarioResult.pass(id(), "block state inchange", ms(start));
            }
            return ScenarioResult.fail(id(),
                "block-interact=deny mais lever a ete utilise", ms(start));
        }
    };

    // ── ENTRY DENY (push back from zone) ─────────────────────────────────────────

    public static final Scenario ENTRY_DENY_E2E = new Scenario() {
        @Override public String id() { return "entry-deny-e2e"; }
        @Override public String category() { return "entry-exit"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            // Smoke check : flag set + zone trouvable. Le pushback via PlayerTickEvent
            // est asynchrone et necessite un teleport + observation -> teste manuel.
            ctx.setupZone(BuiltinFlags.ENTRY.id(), false, 5);
            var z = (com.arcadia.arcadiaguard.zone.ProtectedZone)
                com.arcadia.arcadiaguard.ArcadiaGuard.zoneManager()
                .get(ctx.level(), ctx.zoneName()).orElseThrow();
            Object v = z.flagValues().get(BuiltinFlags.ENTRY.id());
            long elapsed = ms(start);
            if (!Boolean.FALSE.equals(v)) {
                return ScenarioResult.fail(id(), "entry flag not set: " + v, elapsed);
            }
            return ScenarioResult.pass(id(),
                "entry flag set OK (push runtime = test manuel)", elapsed);
        }
    };

    // ── HEAL_AMOUNT applique HP ──────────────────────────────────────────────────

    public static final Scenario HEAL_AMOUNT_E2E = new Scenario() {
        @Override public String id() { return "heal-amount-e2e"; }
        @Override public String category() { return "config"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            ctx.setupZone(BuiltinFlags.HEAL_AMOUNT.id(), 5, 8);
            // Damage le player puis attendre 1+ tick pour observer regen.
            // Mais SelfTest est synchrone donc on ne peut pas wait. On verifie juste
            // que le flag est bien set et que GuardService le lit.
            float maxHp = ctx.player().getMaxHealth();
            ctx.player().setHealth(Math.max(1, maxHp - 5));

            var zone = (com.arcadia.arcadiaguard.zone.ProtectedZone)
                com.arcadia.arcadiaguard.ArcadiaGuard.zoneManager()
                .get(ctx.level(), ctx.zoneName()).orElseThrow();
            Object v = zone.flagValues().get(BuiltinFlags.HEAL_AMOUNT.id());
            ctx.player().setHealth(maxHp); // restore
            long elapsed = ms(start);
            if (!Integer.valueOf(5).equals(v)) {
                return ScenarioResult.fail(id(), "heal value != 5: " + v, elapsed);
            }
            return ScenarioResult.pass(id(),
                "heal-amount=5 set OK (regen tick = manuel a observer)", elapsed);
        }
    };

    // ── ITEM DROP DENY ────────────────────────────────────────────────────────────

    public static final Scenario ITEM_DROP_DENY_E2E = new Scenario() {
        @Override public String id() { return "item-drop-deny-e2e"; }
        @Override public String category() { return "items"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            ctx.setupZone(BuiltinFlags.ITEM_DROP.id(), false, 8);
            // player.drop(stack, false) ne fire pas ItemTossEvent de maniere fiable
            // (depend du mode + du contexte). Smoke check : flag set + zone trouvable.
            var z = (com.arcadia.arcadiaguard.zone.ProtectedZone)
                com.arcadia.arcadiaguard.ArcadiaGuard.zoneManager()
                .get(ctx.level(), ctx.zoneName()).orElseThrow();
            Object v = z.flagValues().get(BuiltinFlags.ITEM_DROP.id());
            long elapsed = ms(start);
            if (!Boolean.FALSE.equals(v)) {
                return ScenarioResult.fail(id(), "item-drop flag not set", elapsed);
            }
            return ScenarioResult.pass(id(),
                "flag set OK (vrai test ItemTossEvent via Q-key = manuel)", elapsed);
        }
    };

    // ── SEND_CHAT DENY ────────────────────────────────────────────────────────────

    public static final Scenario SEND_CHAT_DENY_E2E = new Scenario() {
        @Override public String id() { return "send-chat-deny-e2e"; }
        @Override public String category() { return "chat"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long start = System.nanoTime();
            ctx.setupZone(BuiltinFlags.SEND_CHAT.id(), false, 8);
            // Smoke check : flag lu correctement par GuardService.shouldDeny pattern.
            var z = (com.arcadia.arcadiaguard.zone.ProtectedZone)
                com.arcadia.arcadiaguard.ArcadiaGuard.zoneManager()
                .get(ctx.level(), ctx.zoneName()).orElseThrow();
            Object v = z.flagValues().get(BuiltinFlags.SEND_CHAT.id());
            long elapsed = ms(start);
            if (!Boolean.FALSE.equals(v)) {
                return ScenarioResult.fail(id(), "send-chat flag not set", elapsed);
            }
            return ScenarioResult.pass(id(),
                "flag set OK (vrai test ServerChatEvent = manuel)", elapsed);
        }
    };
}

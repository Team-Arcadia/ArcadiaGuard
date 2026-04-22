package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import java.util.UUID;

/**
 * Scenarios qui invoquent les sous-commandes /ag via le dispatcher du serveur,
 * en utilisant le player executant comme CommandSource. Verifie qu'aucune ne crashe
 * et que les CRUD basiques se reflettent dans l'etat.
 */
public final class CommandScenarios {

    private CommandScenarios() {}

    private static long ms(long start) { return (System.nanoTime() - start) / 1_000_000; }

    private static int exec(TestContext ctx, String cmd) {
        try {
            return ctx.server().getCommands().getDispatcher()
                .execute(cmd, ctx.player().createCommandSourceStack().withPermission(4));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return -1;
        }
    }

    public static final Scenario CMD_ZONE_LIST = new Scenario() {
        @Override public String id() { return "cmd-zone-list"; }
        @Override public String category() { return "commands"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            int rc = exec(ctx, "ag zone list");
            return rc >= 0 ? ScenarioResult.pass(id(), ms(s))
                           : ScenarioResult.fail(id(), "exit code " + rc, ms(s));
        }
    };

    public static final Scenario CMD_ZONE_INFO = new Scenario() {
        @Override public String id() { return "cmd-zone-info"; }
        @Override public String category() { return "commands"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            // Setup une zone pour pouvoir info dessus.
            ctx.setupEmptyZone(3);
            int rc = exec(ctx, "ag zone info " + ctx.zoneName());
            return rc >= 0 ? ScenarioResult.pass(id(), ms(s))
                           : ScenarioResult.fail(id(), "exit code " + rc, ms(s));
        }
    };

    public static final Scenario CMD_ZONE_CREATE_REMOVE = new Scenario() {
        @Override public String id() { return "cmd-zone-create-remove"; }
        @Override public String category() { return "commands"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            String name = "cmdtest-" + UUID.randomUUID().toString().substring(0, 6);
            // /ag zone add <nom> x1 y1 z1 x2 y2 z2 (commande qui prend des coords explicites)
            var pos = ctx.player().blockPosition();
            String cmd = String.format("ag zone add %s %d %d %d %d %d %d",
                name, pos.getX() - 3, pos.getY() - 3, pos.getZ() - 3,
                pos.getX() + 3, pos.getY() + 3, pos.getZ() + 3);
            int rcAdd = exec(ctx, cmd);
            boolean exists = ArcadiaGuard.zoneManager().get(ctx.level(), name).isPresent();
            int rcRemove = exec(ctx, "ag zone remove " + name);
            boolean removed = ArcadiaGuard.zoneManager().get(ctx.level(), name).isEmpty();
            long elapsed = ms(s);
            if (rcAdd < 0) return ScenarioResult.fail(id(), "add failed", elapsed);
            if (!exists) return ScenarioResult.fail(id(), "zone not in registry after add", elapsed);
            if (rcRemove < 0) return ScenarioResult.fail(id(), "remove failed", elapsed);
            if (!removed) return ScenarioResult.fail(id(), "zone still in registry", elapsed);
            return ScenarioResult.pass(id(), elapsed);
        }
    };

    public static final Scenario CMD_ZONE_FLAG = new Scenario() {
        @Override public String id() { return "cmd-zone-flag"; }
        @Override public String category() { return "commands"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            ctx.setupEmptyZone(3);
            int rc = exec(ctx, "ag zone flag " + ctx.zoneName() + " block-break deny");
            var z = (com.arcadia.arcadiaguard.zone.ProtectedZone)
                ArcadiaGuard.zoneManager().get(ctx.level(), ctx.zoneName()).orElseThrow();
            Object v = z.flagValues().get("block-break");
            long elapsed = ms(s);
            if (rc < 0) return ScenarioResult.fail(id(), "flag command exit " + rc, elapsed);
            if (!Boolean.FALSE.equals(v)) {
                return ScenarioResult.fail(id(), "flag value not false: " + v, elapsed);
            }
            return ScenarioResult.pass(id(), elapsed);
        }
    };

    public static final Scenario CMD_DIMFLAG = new Scenario() {
        @Override public String id() { return "cmd-dimflag"; }
        @Override public String category() { return "commands"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            int rc = exec(ctx, "ag dimflag list");
            return rc >= 0 ? ScenarioResult.pass(id(), ms(s))
                           : ScenarioResult.fail(id(), "exit code " + rc, ms(s));
        }
    };

    public static final Scenario CMD_LOG = new Scenario() {
        @Override public String id() { return "cmd-log"; }
        @Override public String category() { return "commands"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            int rc = exec(ctx, "ag log");
            return rc >= 0 ? ScenarioResult.pass(id(), ms(s))
                           : ScenarioResult.fail(id(), "exit code " + rc, ms(s));
        }
    };

    public static final Scenario CMD_ITEM_LIST = new Scenario() {
        @Override public String id() { return "cmd-item-list"; }
        @Override public String category() { return "commands"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            int rc = exec(ctx, "ag item list");
            return rc >= 0 ? ScenarioResult.pass(id(), ms(s))
                           : ScenarioResult.fail(id(), "exit code " + rc, ms(s));
        }
    };

    public static final Scenario CMD_HELP = new Scenario() {
        @Override public String id() { return "cmd-help"; }
        @Override public String category() { return "commands"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            int rc = exec(ctx, "ag help");
            return rc >= 0 ? ScenarioResult.pass(id(), ms(s))
                           : ScenarioResult.fail(id(), "exit code " + rc, ms(s));
        }
    };

    public static final Scenario CMD_RELOAD = new Scenario() {
        @Override public String id() { return "cmd-reload"; }
        @Override public String category() { return "commands"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            int rc = exec(ctx, "ag reload");
            return rc >= 0 ? ScenarioResult.pass(id(), ms(s))
                           : ScenarioResult.fail(id(), "exit code " + rc, ms(s));
        }
    };

    public static final Scenario CMD_WAND_GIVE = new Scenario() {
        @Override public String id() { return "cmd-wand-give"; }
        @Override public String category() { return "commands"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            int rc = exec(ctx, "ag wand give");
            // Cleanup : retire le wand donne.
            ctx.player().getInventory().clearOrCountMatchingItems(
                stack -> stack.is(com.arcadia.arcadiaguard.item.ModItems.ZONE_EDITOR.get()),
                64, ctx.player().inventoryMenu.getCraftSlots());
            return rc >= 0 ? ScenarioResult.pass(id(), ms(s))
                           : ScenarioResult.fail(id(), "exit code " + rc, ms(s));
        }
    };
}

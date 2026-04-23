package com.arcadia.arcadiaguard.selftest.scenarios;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.selftest.Scenario;
import com.arcadia.arcadiaguard.selftest.ScenarioResult;
import com.arcadia.arcadiaguard.selftest.TestContext;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.arcadia.arcadiaguard.zone.ZoneRole;
import java.util.UUID;

/**
 * Scenarios in-game pour FR18 : roles par zone (MEMBER / OWNER).
 * Verifie l'assignation, la lecture et la suppression de role via l'API ProtectedZone.
 */
public final class RoleScenarios {

    private RoleScenarios() {}

    public static final Scenario ROLE_MEMBER_ASSIGN = new Scenario() {
        @Override public String id() { return "role-member-assign"; }
        @Override public String category() { return "roles"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            ctx.setupEmptyZone(3);
            UUID uid = ctx.player().getUUID();
            ProtectedZone zone = (ProtectedZone)
                ArcadiaGuard.zoneManager().get(ctx.level(), ctx.zoneName()).orElseThrow();

            zone.setRole(uid, ZoneRole.MEMBER);
            boolean hasMember = zone.hasRole(uid, ZoneRole.MEMBER);
            boolean isWhitelisted = zone.whitelistedPlayers().contains(uid);

            long ms = (System.nanoTime() - s) / 1_000_000;
            if (!hasMember) return ScenarioResult.fail(id(), "hasAtLeast(MEMBER) false apres setRole", ms);
            if (!isWhitelisted) return ScenarioResult.fail(id(), "setRole(MEMBER) n'a pas ajoute a la whitelist", ms);
            return ScenarioResult.pass(id(), ms);
        }
    };

    public static final Scenario ROLE_OWNER_ASSIGN = new Scenario() {
        @Override public String id() { return "role-owner-assign"; }
        @Override public String category() { return "roles"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            ctx.setupEmptyZone(3);
            UUID uid = ctx.player().getUUID();
            ProtectedZone zone = (ProtectedZone)
                ArcadiaGuard.zoneManager().get(ctx.level(), ctx.zoneName()).orElseThrow();

            zone.setRole(uid, ZoneRole.OWNER);
            long ms = (System.nanoTime() - s) / 1_000_000;
            if (zone.roleOf(uid).orElse(null) != ZoneRole.OWNER) {
                return ScenarioResult.fail(id(), "roleOf != OWNER apres setRole(OWNER)", ms);
            }
            if (!zone.hasRole(uid, ZoneRole.MEMBER)) {
                return ScenarioResult.fail(id(), "OWNER doit satisfaire hasAtLeast(MEMBER)", ms);
            }
            return ScenarioResult.pass(id(), ms);
        }
    };

    public static final Scenario ROLE_REMOVE = new Scenario() {
        @Override public String id() { return "role-remove"; }
        @Override public String category() { return "roles"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            ctx.setupEmptyZone(3);
            UUID uid = UUID.randomUUID(); // joueur fictif pour ne pas perturber l'executant
            ProtectedZone zone = (ProtectedZone)
                ArcadiaGuard.zoneManager().get(ctx.level(), ctx.zoneName()).orElseThrow();

            zone.setRole(uid, ZoneRole.MEMBER);
            zone.removeRole(uid);
            long ms = (System.nanoTime() - s) / 1_000_000;
            if (zone.roleOf(uid).isPresent()) {
                return ScenarioResult.fail(id(), "role encore present apres removeRole", ms);
            }
            return ScenarioResult.pass(id(), ms);
        }
    };

    public static final Scenario WHITELIST_BYPASS_API = new Scenario() {
        @Override public String id() { return "whitelist-bypass-api"; }
        @Override public String category() { return "roles"; }
        @Override public ScenarioResult run(TestContext ctx) {
            long s = System.nanoTime();
            ctx.setupEmptyZone(3);
            UUID uid = ctx.player().getUUID();
            ProtectedZone zone = (ProtectedZone)
                ArcadiaGuard.zoneManager().get(ctx.level(), ctx.zoneName()).orElseThrow();

            // Joueur pas encore dans la whitelist
            boolean beforeAdd = zone.whitelistedPlayers().contains(uid);
            zone.whitelistAdd(uid);
            boolean afterAdd = zone.whitelistedPlayers().contains(uid);
            zone.whitelistRemove(uid);
            boolean afterRemove = zone.whitelistedPlayers().contains(uid);

            long ms = (System.nanoTime() - s) / 1_000_000;
            if (beforeAdd) return ScenarioResult.fail(id(), "joueur deja dans la whitelist avant add", ms);
            if (!afterAdd) return ScenarioResult.fail(id(), "whitelistAdd n'a pas ajoute le joueur", ms);
            if (afterRemove) return ScenarioResult.fail(id(), "whitelistRemove n'a pas retire le joueur", ms);
            return ScenarioResult.pass(id(), ms);
        }
    };
}

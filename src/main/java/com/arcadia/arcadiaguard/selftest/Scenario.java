package com.arcadia.arcadiaguard.selftest;

/**
 * Un scenario de test executable in-game. Chaque scenario decrit une action atomique
 * (ex: casser un bloc en zone deny) et observe le resultat.
 *
 * <p>Contrat : le scenario doit etre deterministe et idempotent. Le {@link TestContext}
 * fournit des utilitaires pour creer/cleanup des zones temporaires autour du test area.
 */
public interface Scenario {

    /** Identifiant unique (ex: "block-break-deny"). */
    String id();

    /** Categorie (ex: "blocks", "combat", "mods.ars_nouveau") pour le filtrage. */
    String category();

    /** Mod requis pour executer ce scenario (vide si vanilla/AG-only). */
    default String requiredMod() { return ""; }

    /** Execute le scenario et retourne le resultat. Doit nettoyer apres lui-meme. */
    ScenarioResult run(TestContext ctx);
}

package com.arcadia.arcadiaguard.selftest;

/**
 * Resultat d'un scenario. {@code status} = PASS / FAIL / SKIP.
 * {@code notes} = raison du fail ou info additionnelle (affiche au chat).
 * {@code durationMs} = temps d'execution pour diagnostic.
 */
public record ScenarioResult(String id, Status status, String notes, long durationMs) {

    public enum Status { PASS, FAIL, SKIP }

    public static ScenarioResult pass(String id, long durationMs) {
        return new ScenarioResult(id, Status.PASS, "", durationMs);
    }

    public static ScenarioResult pass(String id, String notes, long durationMs) {
        return new ScenarioResult(id, Status.PASS, notes, durationMs);
    }

    public static ScenarioResult fail(String id, String reason, long durationMs) {
        return new ScenarioResult(id, Status.FAIL, reason, durationMs);
    }

    public static ScenarioResult skip(String id, String reason) {
        return new ScenarioResult(id, Status.SKIP, reason, 0L);
    }
}

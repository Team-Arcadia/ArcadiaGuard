package com.arcadia.arcadiaguard.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arcadia.arcadiaguard.api.flag.FlagFrequency;
import org.junit.jupiter.api.Test;

/** Tests {@link FlagFrequency} ordering + coverage. */
class FlagFrequencyTest {

    @Test
    void ordinals_strictlyIncreasingSeverity() {
        // Le tri doit suivre : NEGLIGIBLE < LOW < NORMAL < HIGH < VERY_HIGH
        // Utilise par la config admin pour disabled_frequencies >= threshold.
        assertTrue(FlagFrequency.NEGLIGIBLE.ordinal() < FlagFrequency.LOW.ordinal());
        assertTrue(FlagFrequency.LOW.ordinal() < FlagFrequency.NORMAL.ordinal());
        assertTrue(FlagFrequency.NORMAL.ordinal() < FlagFrequency.HIGH.ordinal());
        assertTrue(FlagFrequency.HIGH.ordinal() < FlagFrequency.VERY_HIGH.ordinal());
    }

    @Test
    void values_fiveLevels() {
        assertEquals(5, FlagFrequency.values().length);
    }

    @Test
    void valueOf_roundTrip() {
        for (FlagFrequency f : FlagFrequency.values()) {
            assertEquals(f, FlagFrequency.valueOf(f.name()));
        }
    }
}

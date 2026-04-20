package com.arcadia.arcadiaguard.test;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * Registers ArcadiaGuard GameTest methods with the NeoForge GameTest framework.
 * Call {@link #register(IEventBus)} from the mod constructor with the mod event bus.
 */
public final class ArcadiaGuardTestRegistry {

    private ArcadiaGuardTestRegistry() {}

    /** Registers all GameTest classes on the given mod event bus. */
    public static void register(IEventBus modBus) {
        modBus.addListener(ArcadiaGuardTestRegistry::onRegisterGameTests);
    }

    private static void onRegisterGameTests(RegisterGameTestsEvent event) {
        event.register(ArcadiaGuardGameTests.class);
    }
}

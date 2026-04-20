package com.arcadia.arcadiaguard.api.event;

import com.arcadia.arcadiaguard.api.zone.IZone;
import net.neoforged.bus.api.Event;

/**
 * Fired on the NeoForge EventBus when a zone is successfully created and persisted.
 *
 * <p>Listeners must be registered on {@code NeoForge.EVENT_BUS}.
 * This event is fired on the server thread.
 */
public class ZoneCreatedEvent extends Event {

    private final IZone zone;

    /**
     * @param zone the zone that was created; never null
     */
    public ZoneCreatedEvent(IZone zone) {
        this.zone = zone;
    }

    /**
     * Returns the zone that was created.
     *
     * @return the newly created zone
     */
    public IZone zone() { return this.zone; }
}

package com.arcadia.arcadiaguard.api.event;

import com.arcadia.arcadiaguard.api.zone.IZone;
import net.neoforged.bus.api.Event;

/**
 * Fired on the NeoForge EventBus when a zone is successfully removed and its file deleted.
 *
 * <p>Listeners must be registered on {@code NeoForge.EVENT_BUS}.
 * This event is fired on the server thread.
 */
public class ZoneRemovedEvent extends Event {

    private final IZone zone;

    /**
     * @param zone the zone that was removed; never null
     */
    public ZoneRemovedEvent(IZone zone) {
        this.zone = zone;
    }

    /**
     * Returns the zone that was removed.
     *
     * @return the removed zone
     */
    public IZone zone() { return this.zone; }
}

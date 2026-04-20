package com.arcadia.arcadiaguard.api.event;

import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.zone.IZone;
import net.neoforged.bus.api.Event;

/**
 * Fired on the NeoForge EventBus when a flag value is explicitly set or reset on a zone.
 *
 * <p>Listeners must be registered on {@code NeoForge.EVENT_BUS}.
 * This event is fired on the server thread after the change is persisted to disk.
 */
public class FlagChangedEvent extends Event {

    private final IZone zone;
    private final Flag<?> flag;
    private final Object oldValue;
    private final Object newValue;

    /**
     * @param zone     the zone whose flag was changed; never null
     * @param flag     the flag that was changed; never null
     * @param oldValue the previous explicit value, or {@code null} if the flag was not set before
     * @param newValue the new explicit value, or {@code null} if the flag was reset to its default
     */
    public FlagChangedEvent(IZone zone, Flag<?> flag, Object oldValue, Object newValue) {
        this.zone = zone;
        this.flag = flag;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    /**
     * Returns the zone on which the flag was changed.
     *
     * @return the affected zone
     */
    public IZone zone() { return this.zone; }

    /**
     * Returns the flag that was changed.
     *
     * @return the changed flag
     */
    public Flag<?> flag() { return this.flag; }

    /**
     * Returns the previous explicit value set on the zone, or {@code null} if the flag was
     * inheriting its default value before this change.
     *
     * @return the old value, or {@code null}
     */
    public Object oldValue() { return this.oldValue; }

    /**
     * Returns the new explicit value, or {@code null} if the flag was reset and will now
     * inherit its default value.
     *
     * @return the new value, or {@code null}
     */
    public Object newValue() { return this.newValue; }
}

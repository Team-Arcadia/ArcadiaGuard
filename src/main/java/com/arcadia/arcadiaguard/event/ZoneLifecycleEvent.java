package com.arcadia.arcadiaguard.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/**
 * Events fired on NeoForge.EVENT_BUS when zone lifecycle operations are requested.
 * Events are synchronous: the result field is set by the ZoneManager subscriber
 * before control returns to the poster.
 */
public abstract class ZoneLifecycleEvent extends Event {

    private final ServerPlayer player;
    private final Level level;
    private boolean success;

    protected ZoneLifecycleEvent(ServerPlayer player, Level level) {
        this.player  = player;
        this.level   = level;
    }

    public ServerPlayer player() { return player; }
    public Level level()         { return level; }
    public boolean isSuccess()   { return success; }
    public void setSuccess(boolean v) { this.success = v; }

    // ── Concrete event types ──────────────────────────────────────────────────────

    public static final class CreateZone extends ZoneLifecycleEvent {
        private final String name;
        private final String dimension;
        private final BlockPos pos1;
        private final BlockPos pos2;

        public CreateZone(ServerPlayer player, Level level,
                String name, String dimension, BlockPos pos1, BlockPos pos2) {
            super(player, level);
            this.name      = name;
            this.dimension = dimension;
            this.pos1      = pos1;
            this.pos2      = pos2;
        }

        public String name()       { return name; }
        public String dimension()  { return dimension; }
        public BlockPos pos1()     { return pos1; }
        public BlockPos pos2()     { return pos2; }
    }

    public static final class DeleteZone extends ZoneLifecycleEvent {
        private final String zoneName;

        public DeleteZone(ServerPlayer player, Level level, String zoneName) {
            super(player, level);
            this.zoneName = zoneName;
        }

        public String zoneName() { return zoneName; }
    }

    public static final class SetFlag extends ZoneLifecycleEvent {
        private final String zoneName;
        private final String flagId;
        private final Object value;
        private final boolean reset;

        public SetFlag(ServerPlayer player, Level level,
                String zoneName, String flagId, Object value, boolean reset) {
            super(player, level);
            this.zoneName = zoneName;
            this.flagId   = flagId;
            this.value    = value;
            this.reset    = reset;
        }

        public String zoneName() { return zoneName; }
        public String flagId()   { return flagId; }
        public Object value()    { return value; }
        public boolean isReset() { return reset; }
    }

    public static final class ModifyZone extends ZoneLifecycleEvent {
        public enum Kind { WHITELIST_ADD, WHITELIST_REMOVE, SET_PARENT,
                           TOGGLE_ENABLED, TOGGLE_INHERIT, SET_BOUNDS,
                           ITEM_BLOCK_CHANGED }

        private final String zoneName;
        private final Kind kind;
        private final Object arg1;
        private final Object arg2;

        public ModifyZone(ServerPlayer player, Level level,
                String zoneName, Kind kind, Object arg1, Object arg2) {
            super(player, level);
            this.zoneName = zoneName;
            this.kind     = kind;
            this.arg1     = arg1;
            this.arg2     = arg2;
        }

        public String zoneName() { return zoneName; }
        public Kind   kind()     { return kind; }
        public Object arg1()     { return arg1; }
        public Object arg2()     { return arg2; }
    }
}

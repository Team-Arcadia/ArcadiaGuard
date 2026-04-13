package com.arcadia.arcadiaguard.zone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class ZoneManager {

    private final YawpZoneProvider yawp = new YawpZoneProvider();
    private final InternalZoneProvider internal = new InternalZoneProvider();
    private final ExceptionZoneManager exceptions = new ExceptionZoneManager();

    public void reload(MinecraftServer server) {
        this.yawp.reload(server);
        this.internal.reload(server);
        this.exceptions.reload(server);
    }

    public ZoneCheckResult check(ServerPlayer player, BlockPos pos) {
        if (this.yawp.isAvailable()) {
            ZoneCheckResult yawpResult = this.yawp.check(player, pos);
            if (yawpResult.blocked()) return yawpResult;
        }
        return this.internal.check(player, pos);
    }

    public Collection<ProtectedZone> zones(Level level) {
        List<ProtectedZone> zones = new ArrayList<>();
        zones.addAll(this.yawp.zones(level));
        zones.addAll(this.internal.zones(level));
        return zones;
    }

    public Optional<ProtectedZone> get(Level level, String name) {
        Optional<ProtectedZone> yawpZone = this.yawp.get(level, name);
        return yawpZone.isPresent() ? yawpZone : this.internal.get(level, name);
    }

    public boolean add(Level level, ProtectedZone zone) { return this.internal.add(level, zone); }

    public boolean remove(Level level, String name) { return this.internal.remove(level, name); }

    public boolean whitelistAdd(Level level, String name, UUID playerId, String playerName) {
        return this.yawp.isAvailable() && this.yawp.whitelistAdd(level, name, playerId, playerName)
            || this.internal.whitelistAdd(level, name, playerId, playerName);
    }

    public boolean whitelistRemove(Level level, String name, UUID playerId, String playerName) {
        return this.yawp.isAvailable() && this.yawp.whitelistRemove(level, name, playerId, playerName)
            || this.internal.whitelistRemove(level, name, playerId, playerName);
    }

    public boolean isExceptionAllowed(ServerPlayer player, BlockPos pos, String featureKey) {
        return this.exceptions.isAllowed(player, pos, featureKey);
    }

    public Collection<ExceptionZone> exceptionZones(Level level) {
        return this.exceptions.zones(level);
    }

    public Optional<ExceptionZone> getException(Level level, String name) {
        return this.exceptions.get(level, name);
    }

    public boolean addException(Level level, ExceptionZone zone) {
        return this.exceptions.add(level, zone);
    }

    public boolean removeException(Level level, String name) {
        return this.exceptions.remove(level, name);
    }

    public boolean allowExceptionFeature(Level level, String name, String featureKey) {
        return this.exceptions.allow(level, name, featureKey);
    }

    public boolean denyExceptionFeature(Level level, String name, String featureKey) {
        return this.exceptions.deny(level, name, featureKey);
    }
}

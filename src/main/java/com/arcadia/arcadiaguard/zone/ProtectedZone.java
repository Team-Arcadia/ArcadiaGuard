package com.arcadia.arcadiaguard.zone;

import com.google.gson.annotations.SerializedName;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class ProtectedZone {

    private final String name;
    private final String dimension;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    @SerializedName("whitelist")
    private final Set<UUID> whitelistedPlayers;

    public ProtectedZone(String name, String dimension, BlockPos a, BlockPos b) {
        this(name, dimension,
            Math.min(ReflectionHelper.intMethod(a, "getX"), ReflectionHelper.intMethod(b, "getX")),
            Math.min(ReflectionHelper.intMethod(a, "getY"), ReflectionHelper.intMethod(b, "getY")),
            Math.min(ReflectionHelper.intMethod(a, "getZ"), ReflectionHelper.intMethod(b, "getZ")),
            Math.max(ReflectionHelper.intMethod(a, "getX"), ReflectionHelper.intMethod(b, "getX")),
            Math.max(ReflectionHelper.intMethod(a, "getY"), ReflectionHelper.intMethod(b, "getY")),
            Math.max(ReflectionHelper.intMethod(a, "getZ"), ReflectionHelper.intMethod(b, "getZ")),
            new HashSet<>());
    }

    public ProtectedZone(String name, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Set<UUID> whitelistedPlayers) {
        this.name = name;
        this.dimension = dimension;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.whitelistedPlayers = whitelistedPlayers == null ? new HashSet<>() : new HashSet<>(whitelistedPlayers);
    }

    public boolean contains(String dimension, BlockPos pos) {
        return this.dimension.equals(dimension)
            && ReflectionHelper.intMethod(pos, "getX") >= this.minX && ReflectionHelper.intMethod(pos, "getX") <= this.maxX
            && ReflectionHelper.intMethod(pos, "getY") >= this.minY && ReflectionHelper.intMethod(pos, "getY") <= this.maxY
            && ReflectionHelper.intMethod(pos, "getZ") >= this.minZ && ReflectionHelper.intMethod(pos, "getZ") <= this.maxZ;
    }

    public String name() { return this.name; }
    public String dimension() { return this.dimension; }
    public int minX() { return this.minX; }
    public int minY() { return this.minY; }
    public int minZ() { return this.minZ; }
    public int maxX() { return this.maxX; }
    public int maxY() { return this.maxY; }
    public int maxZ() { return this.maxZ; }
    public Set<UUID> whitelistedPlayers() { return Collections.unmodifiableSet(this.whitelistedPlayers); }

    public boolean whitelistAdd(UUID playerId) { return this.whitelistedPlayers.add(playerId); }

    public boolean whitelistRemove(UUID playerId) { return this.whitelistedPlayers.remove(playerId); }
}

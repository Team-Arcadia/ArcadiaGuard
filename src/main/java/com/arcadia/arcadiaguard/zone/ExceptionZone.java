package com.arcadia.arcadiaguard.zone;

import com.arcadia.arcadiaguard.util.ReflectionHelper;
import com.google.gson.annotations.SerializedName;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

public final class ExceptionZone {

    private final String name;
    private final String dimension;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    @SerializedName("allowed_features")
    private final Set<String> allowedFeatures;

    public ExceptionZone(String name, String dimension, BlockPos a, BlockPos b) {
        this(name, dimension,
            Math.min(ReflectionHelper.intMethod(a, "getX"), ReflectionHelper.intMethod(b, "getX")),
            Math.min(ReflectionHelper.intMethod(a, "getY"), ReflectionHelper.intMethod(b, "getY")),
            Math.min(ReflectionHelper.intMethod(a, "getZ"), ReflectionHelper.intMethod(b, "getZ")),
            Math.max(ReflectionHelper.intMethod(a, "getX"), ReflectionHelper.intMethod(b, "getX")),
            Math.max(ReflectionHelper.intMethod(a, "getY"), ReflectionHelper.intMethod(b, "getY")),
            Math.max(ReflectionHelper.intMethod(a, "getZ"), ReflectionHelper.intMethod(b, "getZ")),
            new LinkedHashSet<>());
    }

    public ExceptionZone(String name, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Set<String> allowedFeatures) {
        this.name = name;
        this.dimension = dimension;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.allowedFeatures = allowedFeatures == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedFeatures);
    }

    public boolean contains(String dimension, BlockPos pos) {
        return this.dimension.equals(dimension)
            && ReflectionHelper.intMethod(pos, "getX") >= this.minX && ReflectionHelper.intMethod(pos, "getX") <= this.maxX
            && ReflectionHelper.intMethod(pos, "getY") >= this.minY && ReflectionHelper.intMethod(pos, "getY") <= this.maxY
            && ReflectionHelper.intMethod(pos, "getZ") >= this.minZ && ReflectionHelper.intMethod(pos, "getZ") <= this.maxZ;
    }

    public boolean allows(String featureKey) {
        return this.allowedFeatures.contains(featureKey.toLowerCase());
    }

    public String name() { return this.name; }
    public String dimension() { return this.dimension; }
    public int minX() { return this.minX; }
    public int minY() { return this.minY; }
    public int minZ() { return this.minZ; }
    public int maxX() { return this.maxX; }
    public int maxY() { return this.maxY; }
    public int maxZ() { return this.maxZ; }
    public Set<String> allowedFeatures() { return this.allowedFeatures; }
}

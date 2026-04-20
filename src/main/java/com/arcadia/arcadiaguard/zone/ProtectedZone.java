package com.arcadia.arcadiaguard.zone;

import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.zone.IZone;
import com.arcadia.arcadiaguard.flag.FlagResolver;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class ProtectedZone implements IZone {

    private final String name;
    private final String dimension;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    @Nullable
    private String parent;
    private final int priority;
    private final Set<UUID> whitelistedPlayers;
    private final Map<UUID, ZoneRole> memberRoles;
    private final Map<String, Object> flagValues;
    private final Map<String, Object> flagValuesUnmodifiable;
    private final Set<UUID> whitelistedPlayersUnmodifiable;
    private final Map<UUID, ZoneRole> memberRolesUnmodifiable;
    private boolean enabled = true;
    private boolean inheritDimFlags = true;

    /** Convenience constructor — no parent, default priority 0. */
    public ProtectedZone(String name, String dimension, BlockPos a, BlockPos b) {
        this(name, dimension,
            Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()),
            Math.max(a.getZ(), b.getZ()),
            new HashSet<>(), null, 0, null, null);
    }

    /** Convenience constructor — no parent, default priority 0, explicit whitelist. */
    public ProtectedZone(String name, String dimension, int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ, Set<UUID> whitelistedPlayers) {
        this(name, dimension, minX, minY, minZ, maxX, maxY, maxZ, whitelistedPlayers, null, 0, null, null);
    }

    /** Constructor with parent + priority (no explicit flags). */
    public ProtectedZone(String name, String dimension, int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ, Set<UUID> whitelistedPlayers,
                         @Nullable String parent, int priority) {
        this(name, dimension, minX, minY, minZ, maxX, maxY, maxZ, whitelistedPlayers, parent, priority, null, null);
    }

    /** Full constructor used by ZoneSerializer (includes persisted flag values). */
    public ProtectedZone(String name, String dimension, int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ, Set<UUID> whitelistedPlayers,
                         @Nullable String parent, int priority, @Nullable Map<String, Object> flagValues) {
        this(name, dimension, minX, minY, minZ, maxX, maxY, maxZ, whitelistedPlayers, parent, priority, flagValues, null);
    }

    /** Full constructor with member roles. */
    public ProtectedZone(String name, String dimension, int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ, Set<UUID> whitelistedPlayers,
                         @Nullable String parent, int priority,
                         @Nullable Map<String, Object> flagValues,
                         @Nullable Map<UUID, ZoneRole> memberRoles) {
        this.name = name;
        this.dimension = dimension;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.whitelistedPlayers = whitelistedPlayers == null ? new HashSet<>() : new HashSet<>(whitelistedPlayers);
        this.parent = parent;
        this.priority = priority;
        this.flagValues = flagValues != null ? new LinkedHashMap<>(flagValues) : new LinkedHashMap<>();
        this.memberRoles = memberRoles != null ? new LinkedHashMap<>(memberRoles) : new LinkedHashMap<>();
        // Ensure all members with roles are also whitelisted
        this.whitelistedPlayers.addAll(this.memberRoles.keySet());
        this.flagValuesUnmodifiable = Collections.unmodifiableMap(this.flagValues);
        this.whitelistedPlayersUnmodifiable = Collections.unmodifiableSet(this.whitelistedPlayers);
        this.memberRolesUnmodifiable = Collections.unmodifiableMap(this.memberRoles);
    }

    public boolean contains(String dimension, BlockPos pos) {
        return this.dimension.equals(dimension)
            && pos.getX() >= this.minX && pos.getX() <= this.maxX
            && pos.getY() >= this.minY && pos.getY() <= this.maxY
            && pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ;
    }

    // --- Flag management ---

    public void setFlag(String flagId, Object value) {
        this.flagValues.put(flagId, value);
    }

    public void resetFlag(String flagId) {
        this.flagValues.remove(flagId);
    }

    public Map<String, Object> flagValues() {
        return this.flagValuesUnmodifiable;
    }

    @Override
    public <T> T flag(Flag<T> flag) {
        return FlagResolver.resolve(this, flag, ignored -> Optional.empty());
    }

    // --- Role management ---

    /** Returns the role of the given player in this zone, or empty if they have no role. */
    public Optional<ZoneRole> roleOf(UUID playerId) {
        return Optional.ofNullable(this.memberRoles.get(playerId));
    }

    /** Returns true if the player has at least the given role in this zone. */
    public boolean hasRole(UUID playerId, ZoneRole required) {
        ZoneRole role = this.memberRoles.get(playerId);
        return role != null && role.atLeast(required);
    }

    /** Assigns a role to a player. They are also added to the whitelist. */
    public void setRole(UUID playerId, ZoneRole role) {
        this.memberRoles.put(playerId, role);
        this.whitelistedPlayers.add(playerId);
    }

    /** Removes a player's role. Does NOT remove them from the general whitelist. */
    public void removeRole(UUID playerId) {
        this.memberRoles.remove(playerId);
    }

    public Map<UUID, ZoneRole> memberRoles() {
        return this.memberRolesUnmodifiable;
    }

    // --- Accessors ---

    public String name() { return this.name; }
    public String dimension() { return this.dimension; }
    public int minX() { return this.minX; }
    public int minY() { return this.minY; }
    public int minZ() { return this.minZ; }
    public int maxX() { return this.maxX; }
    public int maxY() { return this.maxY; }
    public int maxZ() { return this.maxZ; }
    @Nullable public String parent() { return this.parent; }
    public int priority() { return this.priority; }
    public Set<UUID> whitelistedPlayers() { return this.whitelistedPlayersUnmodifiable; }

    public void setParent(@Nullable String parent) { this.parent = parent; }
    public boolean enabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean inheritDimFlags() { return this.inheritDimFlags; }
    public void setInheritDimFlags(boolean inherit) { this.inheritDimFlags = inherit; }

    /** Remplace les bornes min/max (normalise a/b). Appelant : reindexer après. */
    public void setBounds(BlockPos a, BlockPos b) {
        this.minX = Math.min(a.getX(), b.getX());
        this.minY = Math.min(a.getY(), b.getY());
        this.minZ = Math.min(a.getZ(), b.getZ());
        this.maxX = Math.max(a.getX(), b.getX());
        this.maxY = Math.max(a.getY(), b.getY());
        this.maxZ = Math.max(a.getZ(), b.getZ());
    }

    public boolean whitelistAdd(UUID playerId) { return this.whitelistedPlayers.add(playerId); }
    public boolean whitelistRemove(UUID playerId) {
        this.memberRoles.remove(playerId);
        return this.whitelistedPlayers.remove(playerId);
    }
}

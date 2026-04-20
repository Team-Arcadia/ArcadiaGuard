package com.arcadia.arcadiaguard.zone;

import com.arcadia.arcadiaguard.api.zone.ZoneCheckResult;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public interface ZoneProvider {

    String name();

    void reload(MinecraftServer server);

    ZoneCheckResult check(ServerPlayer player, BlockPos pos);

    Collection<ProtectedZone> zones(Level level);

    Optional<ProtectedZone> get(Level level, String name);

    boolean add(Level level, ProtectedZone zone);

    boolean remove(Level level, String name);

    boolean whitelistAdd(Level level, String name, UUID playerId, @Nullable String playerName);

    boolean whitelistRemove(Level level, String name, UUID playerId, @Nullable String playerName);
}

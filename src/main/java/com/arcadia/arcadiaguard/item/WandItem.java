package com.arcadia.arcadiaguard.item;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * ArcadiaGuard wand item — two variants registered in {@link ModItems}:
 * ZONE_EDITOR (left-click = pos1, right-click = pos2) and ZONE_VIEWER (passive).
 * Pos selection is stored server-side, keyed by player UUID.
 */
public final class WandItem extends Item {

    private static final Map<UUID, BlockPos> POS1 = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> POS2 = new ConcurrentHashMap<>();

    public WandItem(Properties properties) {
        super(properties);
    }

    public static void setPos1(UUID playerId, BlockPos pos) { POS1.put(playerId, pos); }
    public static void setPos2(UUID playerId, BlockPos pos) { POS2.put(playerId, pos); }

    @Nullable public static BlockPos getPos1(UUID playerId) { return POS1.get(playerId); }
    @Nullable public static BlockPos getPos2(UUID playerId) { return POS2.get(playerId); }

    public static void clearSelection(UUID playerId) {
        POS1.remove(playerId);
        POS2.remove(playerId);
    }
}

package com.arcadia.arcadiaguard.selftest;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Utilitaire fourni aux scenarios : acces au player executant, au level, et helpers
 * pour creer/cleanup des zones temporaires autour du test area.
 */
public final class TestContext {

    private final ServerPlayer player;
    private final ServerLevel level;
    private String zoneName;
    private BlockPos zoneMin;
    private BlockPos zoneMax;

    public TestContext(ServerPlayer player) {
        this.player = player;
        this.level = player.serverLevel();
    }

    public ServerPlayer player() { return this.player; }
    public ServerLevel level() { return this.level; }
    public MinecraftServer server() { return this.player.getServer(); }

    /**
     * Cree une zone temporaire de rayon {@code radius} autour du joueur avec un flag
     * defini. Appelle {@link #cleanup()} a la fin du scenario.
     */
    public void setupZone(String flagId, Object value, int radius) {
        this.zoneName = "selftest-" + UUID.randomUUID().toString().substring(0, 8);
        BlockPos center = player.blockPosition();
        this.zoneMin = new BlockPos(center.getX() - radius, Math.max(level.getMinBuildHeight(), center.getY() - radius), center.getZ() - radius);
        this.zoneMax = new BlockPos(center.getX() + radius, Math.min(level.getMaxBuildHeight(), center.getY() + radius), center.getZ() + radius);
        ProtectedZone zone = new ProtectedZone(zoneName, level.dimension().location().toString(),
            zoneMin, zoneMax);
        zone.setFlag(flagId, value);
        ArcadiaGuard.zoneManager().add(level, zone);
    }

    /** Variante sans flag, pour scenarios qui configurent plusieurs flags via setFlag(). */
    public void setupEmptyZone(int radius) {
        this.zoneName = "selftest-" + UUID.randomUUID().toString().substring(0, 8);
        BlockPos center = player.blockPosition();
        this.zoneMin = new BlockPos(center.getX() - radius, Math.max(level.getMinBuildHeight(), center.getY() - radius), center.getZ() - radius);
        this.zoneMax = new BlockPos(center.getX() + radius, Math.min(level.getMaxBuildHeight(), center.getY() + radius), center.getZ() + radius);
        ProtectedZone zone = new ProtectedZone(zoneName, level.dimension().location().toString(),
            zoneMin, zoneMax);
        ArcadiaGuard.zoneManager().add(level, zone);
    }

    public void setFlag(String flagId, Object value) {
        ArcadiaGuard.zoneManager().setFlag(level, zoneName, flagId, value);
    }

    public String zoneName() { return this.zoneName; }
    public BlockPos zoneMin() { return this.zoneMin; }
    public BlockPos zoneMax() { return this.zoneMax; }

    /** Position de test libre a 2 blocs au-dessus du joueur, dans la zone. */
    public BlockPos testPos() {
        return player.blockPosition().above(2);
    }

    /** Sauvegarde le block au testPos pour restauration ulterieure. */
    public BlockState snapshotBlock(BlockPos pos) {
        return level.getBlockState(pos);
    }

    public void setBlock(BlockPos pos, Block block) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());
    }

    public void restoreBlock(BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
    }

    /** Cleanup : supprime la zone temporaire. Safe meme si pas encore setup. */
    public void cleanup() {
        if (zoneName != null) {
            try { ArcadiaGuard.zoneManager().remove(level, zoneName); }
            catch (Exception ignored) {}
            zoneName = null;
        }
    }
}

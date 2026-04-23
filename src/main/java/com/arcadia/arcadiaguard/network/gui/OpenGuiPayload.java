package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C : ouvre ZoneListScreen avec une page de zones protégées
 * et les positions courantes de la baguette.
 */
public record OpenGuiPayload(List<ZoneEntry> zones, long wandPos1, long wandPos2, boolean debugMode,
    int page, int pageSize, int totalPages, boolean viewOnly) implements CustomPacketPayload {

    /** Valeur sentinelle = position non définie. */
    public static final long NO_POS = Long.MIN_VALUE;

    public record ZoneEntry(
        String name, String dim,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        int memberCount, boolean hasParent, int flagCount, boolean enabled
    ) {
        private static final StreamCodec<ByteBuf, String> NAME_C = ByteBufCodecs.stringUtf8(64);
        private static final StreamCodec<ByteBuf, String> DIM_C  = ByteBufCodecs.stringUtf8(256);

        static final StreamCodec<ByteBuf, ZoneEntry> CODEC = StreamCodec.of(
            (buf, e) -> {
                NAME_C.encode(buf, e.name);
                DIM_C.encode(buf, e.dim);
                buf.writeInt(e.minX); buf.writeInt(e.minY); buf.writeInt(e.minZ);
                buf.writeInt(e.maxX); buf.writeInt(e.maxY); buf.writeInt(e.maxZ);
                buf.writeInt(e.memberCount);
                buf.writeBoolean(e.hasParent);
                buf.writeInt(e.flagCount);
                buf.writeBoolean(e.enabled);
            },
            buf -> new ZoneEntry(
                NAME_C.decode(buf),
                DIM_C.decode(buf),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readBoolean(), buf.readInt(), buf.readBoolean()
            )
        );
    }

    public static final Type<OpenGuiPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "open_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGuiPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeInt(p.zones().size());
            for (ZoneEntry e : p.zones()) ZoneEntry.CODEC.encode(buf, e);
            buf.writeLong(p.wandPos1());
            buf.writeLong(p.wandPos2());
            buf.writeBoolean(p.debugMode());
            buf.writeInt(p.page());
            buf.writeInt(p.pageSize());
            buf.writeInt(p.totalPages());
            buf.writeBoolean(p.viewOnly());
        },
        buf -> {
            int size = buf.readInt();
            if (size < 0 || size > 200) throw new io.netty.handler.codec.DecoderException("Invalid size " + size + " in OpenGuiPayload");
            List<ZoneEntry> zones = new ArrayList<>(size);
            for (int i = 0; i < size; i++) zones.add(ZoneEntry.CODEC.decode(buf));
            long wandPos1 = buf.readLong();
            long wandPos2 = buf.readLong();
            boolean debugMode = buf.readBoolean();
            int page = buf.readInt();
            int pageSize = buf.readInt();
            if (pageSize < 1 || pageSize > 200) throw new io.netty.handler.codec.DecoderException("Invalid pageSize " + pageSize + " in OpenGuiPayload");
            int totalPages = buf.readInt();
            if (totalPages < 1 || totalPages > 100_000) throw new io.netty.handler.codec.DecoderException("Invalid totalPages " + totalPages + " in OpenGuiPayload");
            boolean viewOnly = buf.readBoolean();
            return new OpenGuiPayload(zones, wandPos1, wandPos2, debugMode, page, pageSize, totalPages, viewOnly);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public BlockPos pos1() { return wandPos1 == NO_POS ? null : BlockPos.of(wandPos1); }
    public BlockPos pos2() { return wandPos2 == NO_POS ? null : BlockPos.of(wandPos2); }
}

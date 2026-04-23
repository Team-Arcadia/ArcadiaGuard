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
 * S→C : ouvre ZoneListScreen avec la liste complete des zones protegees
 * et les positions courantes de la baguette.
 * <p>La pagination a ete retiree au profit d'un scrollbar cote client.
 * La taille maximale est cappee pour eviter une saturation reseau en cas
 * de corruption de packet ; elle reste largement suffisante en pratique.
 */
public record OpenGuiPayload(List<ZoneEntry> zones, long wandPos1, long wandPos2, boolean debugMode,
    boolean viewOnly) implements CustomPacketPayload {

    /** Valeur sentinelle = position non définie. */
    public static final long NO_POS = Long.MIN_VALUE;

    /** Cap de securite cote decodage. Un serveur avec plus de zones verrait
     *  le GUI refuser le packet — a ajuster si un jour ce plafond gene. */
    public static final int MAX_ZONES = 10_000;

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
            buf.writeBoolean(p.viewOnly());
        },
        buf -> {
            int size = buf.readInt();
            if (size < 0 || size > MAX_ZONES) throw new io.netty.handler.codec.DecoderException("Invalid size " + size + " in OpenGuiPayload");
            List<ZoneEntry> zones = new ArrayList<>(size);
            for (int i = 0; i < size; i++) zones.add(ZoneEntry.CODEC.decode(buf));
            long wandPos1 = buf.readLong();
            long wandPos2 = buf.readLong();
            boolean debugMode = buf.readBoolean();
            boolean viewOnly = buf.readBoolean();
            return new OpenGuiPayload(zones, wandPos1, wandPos2, debugMode, viewOnly);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public BlockPos pos1() { return wandPos1 == NO_POS ? null : BlockPos.of(wandPos1); }
    public BlockPos pos2() { return wandPos2 == NO_POS ? null : BlockPos.of(wandPos2); }
}

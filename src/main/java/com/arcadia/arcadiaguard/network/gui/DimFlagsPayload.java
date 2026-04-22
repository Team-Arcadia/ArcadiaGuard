package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S→C : flags configurés sur une dimension spécifique. */
public record DimFlagsPayload(String dimKey, List<FlagInfo> flags) implements CustomPacketPayload {

    /** type: 0=BOOL, 1=INT, 2=LIST. stringValue contient la valeur brute. */
    public record FlagInfo(String id, String label, boolean value, boolean configured,
                           String description, byte type, String stringValue) {
        public static final byte TYPE_BOOL = 0, TYPE_INT = 1, TYPE_LIST = 2;

        private static final StreamCodec<ByteBuf, String> ID_C    = ByteBufCodecs.stringUtf8(64);
        private static final StreamCodec<ByteBuf, String> LABEL_C = ByteBufCodecs.stringUtf8(128);
        private static final StreamCodec<ByteBuf, String> DESC_C  = ByteBufCodecs.stringUtf8(512);
        private static final StreamCodec<ByteBuf, String> VALUE_C = ByteBufCodecs.stringUtf8(32768);

        static final StreamCodec<ByteBuf, FlagInfo> CODEC = StreamCodec.of(
            (buf, f) -> {
                ID_C.encode(buf, f.id);
                LABEL_C.encode(buf, f.label);
                buf.writeBoolean(f.value);
                buf.writeBoolean(f.configured);
                DESC_C.encode(buf, f.description);
                buf.writeByte(f.type);
                VALUE_C.encode(buf, f.stringValue);
            },
            buf -> new FlagInfo(
                ID_C.decode(buf),
                LABEL_C.decode(buf),
                buf.readBoolean(), buf.readBoolean(),
                DESC_C.decode(buf),
                buf.readByte(),
                VALUE_C.decode(buf)
            )
        );
    }

    public static final Type<DimFlagsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "dim_flags"));

    private static final StreamCodec<ByteBuf, String> DIM_C = ByteBufCodecs.stringUtf8(256);

    public static final StreamCodec<RegistryFriendlyByteBuf, DimFlagsPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            DIM_C.encode(buf, p.dimKey());
            buf.writeInt(p.flags().size());
            for (FlagInfo f : p.flags()) FlagInfo.CODEC.encode(buf, f);
        },
        buf -> {
            String dimKey = DIM_C.decode(buf);
            int size = buf.readInt();
            if (size < 0 || size > 256) throw new io.netty.handler.codec.DecoderException("Invalid size " + size + " in DimFlagsPayload");
            List<FlagInfo> flags = new ArrayList<>(size);
            for (int i = 0; i < size; i++) flags.add(FlagInfo.CODEC.decode(buf));
            return new DimFlagsPayload(dimKey, flags);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

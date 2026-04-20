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

        static final StreamCodec<ByteBuf, FlagInfo> CODEC = StreamCodec.of(
            (buf, f) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, f.id);
                ByteBufCodecs.STRING_UTF8.encode(buf, f.label);
                buf.writeBoolean(f.value);
                buf.writeBoolean(f.configured);
                ByteBufCodecs.STRING_UTF8.encode(buf, f.description);
                buf.writeByte(f.type);
                ByteBufCodecs.STRING_UTF8.encode(buf, f.stringValue);
            },
            buf -> new FlagInfo(
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readBoolean(), buf.readBoolean(),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readByte(),
                ByteBufCodecs.STRING_UTF8.decode(buf)
            )
        );
    }

    public static final Type<DimFlagsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "dim_flags"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DimFlagsPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, p.dimKey());
            buf.writeInt(p.flags().size());
            for (FlagInfo f : p.flags()) FlagInfo.CODEC.encode(buf, f);
        },
        buf -> {
            String dimKey = ByteBufCodecs.STRING_UTF8.decode(buf);
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

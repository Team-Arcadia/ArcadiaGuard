package com.arcadia.arcadiaguard.network;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ZoneDataPayload(List<ClientZoneInfo> zones) implements CustomPacketPayload {

    public record ClientZoneInfo(
        String name,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        boolean dimensional,
        boolean hasParent
    ) {
        private static final StreamCodec<ByteBuf, String> NAME_C = ByteBufCodecs.stringUtf8(64);

        static final StreamCodec<ByteBuf, ClientZoneInfo> STREAM_CODEC = StreamCodec.of(
            (buf, info) -> {
                NAME_C.encode(buf, info.name);
                buf.writeInt(info.minX); buf.writeInt(info.minY); buf.writeInt(info.minZ);
                buf.writeInt(info.maxX); buf.writeInt(info.maxY); buf.writeInt(info.maxZ);
                buf.writeByte((info.dimensional ? 1 : 0) | (info.hasParent ? 2 : 0));
            },
            buf -> {
                String name = NAME_C.decode(buf);
                int minX = buf.readInt(), minY = buf.readInt(), minZ = buf.readInt();
                int maxX = buf.readInt(), maxY = buf.readInt(), maxZ = buf.readInt();
                byte flags = buf.readByte();
                return new ClientZoneInfo(name, minX, minY, minZ, maxX, maxY, maxZ,
                    (flags & 1) != 0, (flags & 2) != 0);
            }
        );
    }

    public static final Type<ZoneDataPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "zone_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ZoneDataPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.zones().size());
                for (ClientZoneInfo info : payload.zones()) {
                    ClientZoneInfo.STREAM_CODEC.encode(buf, info);
                }
            },
            buf -> {
                int size = buf.readInt();
                if (size < 0 || size > 4096)
                    throw new io.netty.handler.codec.DecoderException("ZoneDataPayload: zone count out of range: " + size);
                List<ClientZoneInfo> zones = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    zones.add(ClientZoneInfo.STREAM_CODEC.decode(buf));
                }
                return new ZoneDataPayload(zones);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

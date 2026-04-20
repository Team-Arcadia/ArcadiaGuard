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

/** S→C : logs d'audit pour une zone, filtrés. */
public record ZoneLogsPayload(String zoneName, List<LogLine> entries) implements CustomPacketPayload {

    public record LogLine(String timestamp, String player, String action, String pos) {
        static final StreamCodec<ByteBuf, LogLine> CODEC = StreamCodec.of(
            (buf, l) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, l.timestamp);
                ByteBufCodecs.STRING_UTF8.encode(buf, l.player);
                ByteBufCodecs.STRING_UTF8.encode(buf, l.action);
                ByteBufCodecs.STRING_UTF8.encode(buf, l.pos);
            },
            buf -> new LogLine(
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf)
            )
        );
    }

    public static final Type<ZoneLogsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "zone_logs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ZoneLogsPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, p.zoneName());
            buf.writeInt(p.entries().size());
            for (LogLine l : p.entries()) LogLine.CODEC.encode(buf, l);
        },
        buf -> {
            String zone = ByteBufCodecs.STRING_UTF8.decode(buf);
            int n = buf.readInt();
            if (n < 0 || n > 200) throw new io.netty.handler.codec.DecoderException("Invalid size " + n + " in ZoneLogsPayload");
            List<LogLine> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) entries.add(LogLine.CODEC.decode(buf));
            return new ZoneLogsPayload(zone, entries);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

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

/** S→C : détail complet d'une zone (flags + membres). */
public record ZoneDetailPayload(Detail detail) implements CustomPacketPayload {

    /** type: 0=BOOL, 1=INT, 2=LIST. stringValue contient la valeur brute (ex: "42" ou "a,b,c"). */
    public record FlagEntry(String id, String label, boolean value, boolean inherited,
                            String description, byte type, String stringValue) {
        public static final byte TYPE_BOOL = 0, TYPE_INT = 1, TYPE_LIST = 2;

        static final StreamCodec<ByteBuf, FlagEntry> CODEC = StreamCodec.of(
            (buf, f) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, f.id);
                ByteBufCodecs.STRING_UTF8.encode(buf, f.label);
                buf.writeBoolean(f.value);
                buf.writeBoolean(f.inherited);
                ByteBufCodecs.STRING_UTF8.encode(buf, f.description);
                buf.writeByte(f.type);
                ByteBufCodecs.STRING_UTF8.encode(buf, f.stringValue);
            },
            buf -> new FlagEntry(
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readBoolean(), buf.readBoolean(),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readByte(),
                ByteBufCodecs.STRING_UTF8.decode(buf)
            )
        );
    }

    public record MemberEntry(String uuid, String name) {
        static final StreamCodec<ByteBuf, MemberEntry> CODEC = StreamCodec.of(
            (buf, m) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, m.uuid);
                ByteBufCodecs.STRING_UTF8.encode(buf, m.name);
            },
            buf -> new MemberEntry(
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf)
            )
        );
    }

    public record Detail(
        String name, String dim,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        String parentName,
        List<FlagEntry> flags,
        List<MemberEntry> members,
        boolean enabled,
        boolean inheritDimFlags
    ) {
        static final StreamCodec<ByteBuf, Detail> CODEC = StreamCodec.of(
            (buf, d) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, d.name);
                ByteBufCodecs.STRING_UTF8.encode(buf, d.dim);
                buf.writeInt(d.minX); buf.writeInt(d.minY); buf.writeInt(d.minZ);
                buf.writeInt(d.maxX); buf.writeInt(d.maxY); buf.writeInt(d.maxZ);
                ByteBufCodecs.STRING_UTF8.encode(buf, d.parentName == null ? "" : d.parentName);
                buf.writeInt(d.flags.size());
                for (FlagEntry f : d.flags) FlagEntry.CODEC.encode(buf, f);
                buf.writeInt(d.members.size());
                for (MemberEntry m : d.members) MemberEntry.CODEC.encode(buf, m);
                buf.writeBoolean(d.enabled);
                buf.writeBoolean(d.inheritDimFlags);
            },
            buf -> {
                String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                String dim  = ByteBufCodecs.STRING_UTF8.decode(buf);
                int minX = buf.readInt(), minY = buf.readInt(), minZ = buf.readInt();
                int maxX = buf.readInt(), maxY = buf.readInt(), maxZ = buf.readInt();
                String parent = ByteBufCodecs.STRING_UTF8.decode(buf);
                int fc = buf.readInt();
                if (fc < 0 || fc > 256) throw new io.netty.handler.codec.DecoderException("Invalid flag count " + fc + " in ZoneDetailPayload");
                List<FlagEntry> flags = new ArrayList<>(fc);
                for (int i = 0; i < fc; i++) flags.add(FlagEntry.CODEC.decode(buf));
                int mc = buf.readInt();
                if (mc < 0 || mc > 256) throw new io.netty.handler.codec.DecoderException("Invalid member count " + mc + " in ZoneDetailPayload");
                List<MemberEntry> members = new ArrayList<>(mc);
                for (int i = 0; i < mc; i++) members.add(MemberEntry.CODEC.decode(buf));
                boolean enabled = buf.readBoolean();
                boolean inheritDimFlags = buf.readBoolean();
                return new Detail(name, dim, minX, minY, minZ, maxX, maxY, maxZ,
                    parent.isEmpty() ? null : parent, flags, members, enabled, inheritDimFlags);
            }
        );
    }

    public static final Type<ZoneDetailPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "zone_detail"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ZoneDetailPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> Detail.CODEC.encode(buf, p.detail()),
        buf -> new ZoneDetailPayload(Detail.CODEC.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

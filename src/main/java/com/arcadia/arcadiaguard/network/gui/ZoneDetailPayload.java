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
public record ZoneDetailPayload(Detail detail, boolean viewOnly) implements CustomPacketPayload {

    /**
     * type: 0=BOOL, 1=INT, 2=LIST. stringValue contient la valeur brute (ex: "42" ou "a,b,c").
     * source : origine de la valeur resolue. NONE=flag non configure nulle part (defaut),
     * ZONE_OWN=override local de la zone, PARENT=herite d'une zone parente,
     * DIM=herite des flags de dimension (uniquement si inheritDimFlags=true sur la zone).
     */
    public record FlagEntry(String id, String label, boolean value, boolean inherited,
                            String description, byte type, String stringValue, byte source) {
        public static final byte TYPE_BOOL = 0, TYPE_INT = 1, TYPE_LIST = 2;
        public static final byte SOURCE_NONE = 0, SOURCE_ZONE_OWN = 1, SOURCE_PARENT = 2, SOURCE_DIM = 3;

        // DoS-safe string codecs (size-capped)
        private static final StreamCodec<ByteBuf, String> ID_C     = ByteBufCodecs.stringUtf8(64);
        private static final StreamCodec<ByteBuf, String> LABEL_C  = ByteBufCodecs.stringUtf8(128);
        private static final StreamCodec<ByteBuf, String> DESC_C   = ByteBufCodecs.stringUtf8(512);
        private static final StreamCodec<ByteBuf, String> VALUE_C  = ByteBufCodecs.stringUtf8(32768);

        static final StreamCodec<ByteBuf, FlagEntry> CODEC = StreamCodec.of(
            (buf, f) -> {
                ID_C.encode(buf, f.id);
                LABEL_C.encode(buf, f.label);
                buf.writeBoolean(f.value);
                buf.writeBoolean(f.inherited);
                DESC_C.encode(buf, f.description);
                buf.writeByte(f.type);
                VALUE_C.encode(buf, f.stringValue);
                buf.writeByte(f.source);
            },
            buf -> new FlagEntry(
                ID_C.decode(buf),
                LABEL_C.decode(buf),
                buf.readBoolean(), buf.readBoolean(),
                DESC_C.decode(buf),
                buf.readByte(),
                VALUE_C.decode(buf),
                buf.readByte()
            )
        );
    }

    public record MemberEntry(String uuid, String name) {
        private static final StreamCodec<ByteBuf, String> UUID_C = ByteBufCodecs.stringUtf8(36);
        private static final StreamCodec<ByteBuf, String> NAME_C = ByteBufCodecs.stringUtf8(32);
        static final StreamCodec<ByteBuf, MemberEntry> CODEC = StreamCodec.of(
            (buf, m) -> {
                UUID_C.encode(buf, m.uuid);
                NAME_C.encode(buf, m.name);
            },
            buf -> new MemberEntry(
                UUID_C.decode(buf),
                NAME_C.decode(buf)
            )
        );
    }

    private static final StreamCodec<ByteBuf, String> ZONE_NAME_C = ByteBufCodecs.stringUtf8(64);
    private static final StreamCodec<ByteBuf, String> DIM_NAME_C  = ByteBufCodecs.stringUtf8(256);
    private static final StreamCodec<ByteBuf, String> ITEM_ID_C   = ByteBufCodecs.stringUtf8(128);

    public record Detail(
        String name, String dim,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        String parentName,
        List<FlagEntry> flags,
        List<MemberEntry> members,
        boolean enabled,
        boolean inheritDimFlags,
        List<String> blockedItems
    ) {
        static final StreamCodec<ByteBuf, Detail> CODEC = StreamCodec.of(
            (buf, d) -> {
                ZONE_NAME_C.encode(buf, d.name);
                DIM_NAME_C.encode(buf, d.dim);
                buf.writeInt(d.minX); buf.writeInt(d.minY); buf.writeInt(d.minZ);
                buf.writeInt(d.maxX); buf.writeInt(d.maxY); buf.writeInt(d.maxZ);
                ZONE_NAME_C.encode(buf, d.parentName == null ? "" : d.parentName);
                buf.writeInt(d.flags.size());
                for (FlagEntry f : d.flags) FlagEntry.CODEC.encode(buf, f);
                buf.writeInt(d.members.size());
                for (MemberEntry m : d.members) MemberEntry.CODEC.encode(buf, m);
                buf.writeBoolean(d.enabled);
                buf.writeBoolean(d.inheritDimFlags);
                buf.writeInt(d.blockedItems.size());
                for (String id : d.blockedItems) ITEM_ID_C.encode(buf, id);
            },
            buf -> {
                String name = ZONE_NAME_C.decode(buf);
                String dim  = DIM_NAME_C.decode(buf);
                int minX = buf.readInt(), minY = buf.readInt(), minZ = buf.readInt();
                int maxX = buf.readInt(), maxY = buf.readInt(), maxZ = buf.readInt();
                String parent = ZONE_NAME_C.decode(buf);
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
                int bic = buf.readInt();
                if (bic < 0 || bic > 4096) throw new io.netty.handler.codec.DecoderException("Invalid blocked items count " + bic + " in ZoneDetailPayload");
                List<String> blockedItems = new ArrayList<>(bic);
                for (int i = 0; i < bic; i++) blockedItems.add(ITEM_ID_C.decode(buf));
                return new Detail(name, dim, minX, minY, minZ, maxX, maxY, maxZ,
                    parent.isEmpty() ? null : parent, flags, members, enabled, inheritDimFlags, blockedItems);
            }
        );
    }

    public static final Type<ZoneDetailPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "zone_detail"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ZoneDetailPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { Detail.CODEC.encode(buf, p.detail()); buf.writeBoolean(p.viewOnly()); },
        buf -> new ZoneDetailPayload(Detail.CODEC.decode(buf), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

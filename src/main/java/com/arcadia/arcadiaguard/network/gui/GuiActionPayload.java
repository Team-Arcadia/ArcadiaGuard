package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S : action générique depuis l'interface graphique.
 * arg1/arg2 portent des données contextuelles selon l'action.
 */
public record GuiActionPayload(
    Action action,
    String zoneName,
    String arg1,
    String arg2,
    boolean boolVal,
    int x1, int y1, int z1,
    int x2, int y2, int z2
) implements CustomPacketPayload {

    public enum Action {
        REQUEST_DETAIL,    // zoneName
        CREATE_ZONE,       // zoneName=name, x1/y1/z1=pos1, x2/y2/z2=pos2
        DELETE_ZONE,       // zoneName
        SET_FLAG,          // zoneName, arg1=flagId, boolVal=newValue
        WHITELIST_ADD,     // zoneName, arg1=playerName
        WHITELIST_REMOVE,  // zoneName, arg1=playerName
        TELEPORT,          // zoneName
        TOGGLE_DEBUG,      // aucun arg — bascule le debug mode pour le joueur
        SET_PARENT,        // zoneName, arg1=parentName ("" = effacer le parent)
        TOGGLE_ZONE_ENABLED,       // zoneName, boolVal=newEnabled
        TOGGLE_INHERIT_DIM_FLAGS,  // zoneName, boolVal=newInherit
        SET_DIM_FLAG,              // zoneName=dimKey, arg1=flagId, boolVal=value
        RESET_DIM_FLAG,            // zoneName=dimKey, arg1=flagId — retire l'override
        REQUEST_DIM_DETAIL,        // zoneName=dimKey
        RESET_FLAG,                // zoneName, arg1=flagId — retire l'override côté zone
        SET_FLAG_STR,              // zoneName, arg1=flagId, arg2=stringValue (int/list)
        SET_DIM_FLAG_STR,          // zoneName=dimKey, arg1=flagId, arg2=stringValue
        SET_ZONE_BOUNDS,           // zoneName, x1/y1/z1=nouveau pos1, x2/y2/z2=nouveau pos2
        REQUEST_ZONE_LOGS,         // zoneName, arg1=playerFilter, arg2=actionFilter
        REQUEST_PAGE               // x1=pageNumber
    }

    public static final Type<GuiActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "gui_action"));

    private static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, String> ZONE_NAME_CODEC =
        ByteBufCodecs.stringUtf8(64);
    private static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, String> ARG1_CODEC =
        ByteBufCodecs.stringUtf8(64);
    private static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, String> ARG2_CODEC =
        ByteBufCodecs.stringUtf8(256);

    public static final StreamCodec<RegistryFriendlyByteBuf, GuiActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeByte(p.action().ordinal());
            ZONE_NAME_CODEC.encode(buf, p.zoneName());
            ARG1_CODEC.encode(buf, p.arg1());
            ARG2_CODEC.encode(buf, p.arg2());
            buf.writeBoolean(p.boolVal());
            buf.writeInt(p.x1()); buf.writeInt(p.y1()); buf.writeInt(p.z1());
            buf.writeInt(p.x2()); buf.writeInt(p.y2()); buf.writeInt(p.z2());
        },
        buf -> {
            int idx = buf.readByte() & 0xFF;
            Action[] vals = Action.values();
            if (idx >= vals.length) throw new io.netty.handler.codec.DecoderException("Invalid Action index: " + idx);
            Action action = vals[idx];
            String zoneName = ZONE_NAME_CODEC.decode(buf);
            String arg1 = ARG1_CODEC.decode(buf);
            String arg2 = ARG2_CODEC.decode(buf);
            boolean boolVal = buf.readBoolean();
            int x1 = buf.readInt(), y1 = buf.readInt(), z1 = buf.readInt();
            int x2 = buf.readInt(), y2 = buf.readInt(), z2 = buf.readInt();
            return new GuiActionPayload(action, zoneName, arg1, arg2, boolVal, x1, y1, z1, x2, y2, z2);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // Factories statiques pour chaque action
    public static GuiActionPayload requestDetail(String zone) {
        return new GuiActionPayload(Action.REQUEST_DETAIL, zone, "", "", false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload createZone(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new GuiActionPayload(Action.CREATE_ZONE, name, "", "", false, x1,y1,z1,x2,y2,z2);
    }
    public static GuiActionPayload deleteZone(String zone) {
        return new GuiActionPayload(Action.DELETE_ZONE, zone, "", "", false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload setFlag(String zone, String flagId, boolean value) {
        return new GuiActionPayload(Action.SET_FLAG, zone, flagId, "", value, 0,0,0,0,0,0);
    }
    public static GuiActionPayload whitelistAdd(String zone, String player) {
        return new GuiActionPayload(Action.WHITELIST_ADD, zone, player, "", false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload whitelistRemove(String zone, String player) {
        return new GuiActionPayload(Action.WHITELIST_REMOVE, zone, player, "", false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload teleport(String zone) {
        return new GuiActionPayload(Action.TELEPORT, zone, "", "", false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload toggleDebug() {
        return new GuiActionPayload(Action.TOGGLE_DEBUG, "", "", "", false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload setParent(String zone, String parentName) {
        return new GuiActionPayload(Action.SET_PARENT, zone, parentName, "", false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload toggleZoneEnabled(String zone, boolean enabled) {
        return new GuiActionPayload(Action.TOGGLE_ZONE_ENABLED, zone, "", "", enabled, 0,0,0,0,0,0);
    }
    public static GuiActionPayload toggleInheritDimFlags(String zone, boolean inherit) {
        return new GuiActionPayload(Action.TOGGLE_INHERIT_DIM_FLAGS, zone, "", "", inherit, 0,0,0,0,0,0);
    }
    public static GuiActionPayload setDimFlag(String dimKey, String flagId, boolean value) {
        return new GuiActionPayload(Action.SET_DIM_FLAG, dimKey, flagId, "", value, 0,0,0,0,0,0);
    }
    public static GuiActionPayload resetDimFlag(String dimKey, String flagId) {
        return new GuiActionPayload(Action.RESET_DIM_FLAG, dimKey, flagId, "", false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload requestZoneLogs(String zone, String playerFilter, String actionFilter) {
        return new GuiActionPayload(Action.REQUEST_ZONE_LOGS, zone,
            playerFilter == null ? "" : playerFilter,
            actionFilter == null ? "" : actionFilter, false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload setZoneBounds(String zone, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new GuiActionPayload(Action.SET_ZONE_BOUNDS, zone, "", "", false, x1,y1,z1,x2,y2,z2);
    }
    public static GuiActionPayload resetFlag(String zone, String flagId) {
        return new GuiActionPayload(Action.RESET_FLAG, zone, flagId, "", false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload setFlagStr(String zone, String flagId, String value) {
        return new GuiActionPayload(Action.SET_FLAG_STR, zone, flagId, value, false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload setDimFlagStr(String dimKey, String flagId, String value) {
        return new GuiActionPayload(Action.SET_DIM_FLAG_STR, dimKey, flagId, value, false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload requestDimDetail(String dimKey) {
        return new GuiActionPayload(Action.REQUEST_DIM_DETAIL, dimKey, "", "", false, 0,0,0,0,0,0);
    }
    public static GuiActionPayload requestPage(int page) {
        return new GuiActionPayload(Action.REQUEST_PAGE, "", "", "", false, page,0,0,0,0,0);
    }
}

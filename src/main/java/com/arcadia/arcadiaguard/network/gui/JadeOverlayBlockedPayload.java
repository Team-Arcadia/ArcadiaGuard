package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S->C : indique au client si l'overlay Jade doit etre masque pour le joueur local.
 */
public record JadeOverlayBlockedPayload(boolean blocked) implements CustomPacketPayload {

    public static final Type<JadeOverlayBlockedPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "jade_overlay_blocked"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JadeOverlayBlockedPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> buf.writeBoolean(p.blocked()),
        buf -> new JadeOverlayBlockedPayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

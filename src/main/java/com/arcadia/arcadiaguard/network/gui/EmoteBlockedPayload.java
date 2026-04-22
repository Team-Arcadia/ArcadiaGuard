package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S->C : indique au client si les emotes du joueur local sont bloquees
 * (zone avec emote_use=deny). Le server-side verifier natif d'Emotecraft
 * depend d'une config externe (validateEmote) qui peut etre desactivee,
 * donc on double avec un verifier CLIENT-side authoritative.
 */
public record EmoteBlockedPayload(boolean blocked) implements CustomPacketPayload {

    public static final Type<EmoteBlockedPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "emote_blocked"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EmoteBlockedPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> buf.writeBoolean(p.blocked()),
        buf -> new EmoteBlockedPayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

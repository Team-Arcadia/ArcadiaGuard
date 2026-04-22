package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S->C : notifie le client qu'une zone vient d'etre supprimee cote serveur,
 * pour retirer l'entree correspondante du ClientZoneCache (rendu).
 * Sans ce packet, une zone deja activee via "Voir : ON" restait dessinee
 * meme apres sa suppression.
 */
public record ZoneRemovedPayload(String zoneName) implements CustomPacketPayload {

    public static final Type<ZoneRemovedPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "zone_removed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ZoneRemovedPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> ByteBufCodecs.stringUtf8(64).encode(buf, p.zoneName()),
        buf -> new ZoneRemovedPayload(ByteBufCodecs.stringUtf8(64).decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

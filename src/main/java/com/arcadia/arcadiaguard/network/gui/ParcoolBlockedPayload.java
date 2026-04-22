package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S-H21 (v1.5) : S->C payload qui indique au client si le joueur
 * local a ses actions ParCool bloquees (zone avec parcool_actions=deny).
 *
 * <p>Le client maintient un flag static via ClientParcoolState, consulte
 * par {@code ActionProcessorMixin} pour canceler le tick ParCool local.
 */
public record ParcoolBlockedPayload(boolean blocked) implements CustomPacketPayload {

    public static final Type<ParcoolBlockedPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaGuard.MOD_ID, "parcool_blocked"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ParcoolBlockedPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> buf.writeBoolean(p.blocked()),
        buf -> new ParcoolBlockedPayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

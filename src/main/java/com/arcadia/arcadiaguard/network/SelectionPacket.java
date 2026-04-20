package com.arcadia.arcadiaguard.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent from client to server when the wand selects pos1 or pos2.
 */
public record SelectionPacket(boolean isPos1, BlockPos pos) {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("arcadiaguard", "selection");

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(isPos1);
        buf.writeBlockPos(pos);
    }

    public static SelectionPacket decode(FriendlyByteBuf buf) {
        return new SelectionPacket(buf.readBoolean(), buf.readBlockPos());
    }
}

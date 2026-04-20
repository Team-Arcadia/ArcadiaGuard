package com.arcadia.arcadiaguard.network;

import com.arcadia.arcadiaguard.client.ClientZoneCache;
import com.arcadia.arcadiaguard.gui.ZoneDetailScreen;
import com.arcadia.arcadiaguard.gui.ZoneListScreen;
import com.arcadia.arcadiaguard.network.gui.OpenGuiPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void handleZoneData(ZoneDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientZoneCache.update(payload.zones()));
    }

    public static void handleOpenGui(OpenGuiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Screen current = mc.screen;
            if (current instanceof ZoneListScreen zls) {
                zls.refresh(payload);
            } else {
                mc.setScreen(new ZoneListScreen(
                    payload.zones(),
                    payload.pos1(), payload.pos2(), payload.debugMode(),
                    payload.page(), payload.pageSize(), payload.totalPages()));
            }
        });
    }

    public static void handleZoneDetail(ZoneDetailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ZoneDetailScreen zds) {
                zds.updateDetail(payload.detail());
            } else {
                Screen parent = mc.screen;
                mc.setScreen(new ZoneDetailScreen(parent, payload.detail()));
            }
        });
    }
}

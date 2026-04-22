package com.arcadia.arcadiaguard.client;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.gui.DimDetailScreen;
import com.arcadia.arcadiaguard.gui.ZoneDetailScreen;
import com.arcadia.arcadiaguard.gui.ZoneListScreen;
import com.arcadia.arcadiaguard.gui.ZoneLogsScreen;
import com.arcadia.arcadiaguard.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;

@OnlyIn(Dist.CLIENT)
public final class ArcadiaGuardClient {

    private ArcadiaGuardClient() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ZoneRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(WandHudRenderer::onRenderGui);

        // S-H21 / S-H22 : enregistrer le verifier client-side d'Emotecraft
        // (necessaire car le verifier server-side natif depend d'une config externe
        // validateEmote qui peut etre desactivee).
        ClientEmotecraftHook.register();

        // Injection des handlers S→C (lambdas dans cette classe @OnlyIn(CLIENT))
        PacketHandler.setClientHandlers(
            (payload, ctx) -> ctx.enqueueWork(() -> ClientZoneCache.update(payload.zones())),
            (payload, ctx) -> ctx.enqueueWork(() -> {
                try {
                    ClientZoneCache.updateWandPositions(payload.pos1(), payload.pos2());
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
                } catch (Exception e) {
                    ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Failed to open zone list GUI", e);
                    Minecraft.getInstance().setScreen(null);
                }
            }),
            (payload, ctx) -> ctx.enqueueWork(() -> {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.screen instanceof ZoneDetailScreen zds) {
                        zds.updateDetail(payload.detail());
                    } else {
                        Screen parent = mc.screen;
                        mc.setScreen(new ZoneDetailScreen(parent, payload.detail()));
                    }
                } catch (Exception e) {
                    ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Failed to open zone detail GUI", e);
                    Minecraft.getInstance().setScreen(null);
                }
            }),
            (payload, ctx) -> ctx.enqueueWork(() -> {
                ClientDimFlagCache.update(payload);
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof DimDetailScreen dds) {
                    dds.refresh(payload);
                } else {
                    mc.setScreen(new DimDetailScreen(mc.screen, payload));
                }
            }),
            (payload, ctx) -> ctx.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof ZoneLogsScreen zls) {
                    zls.refresh(payload);
                } else {
                    mc.setScreen(new ZoneLogsScreen(mc.screen, payload));
                }
            }),
            // ZoneRemovedPayload : purge du cache de rendu quand une zone est supprimee serveur
            (payload, ctx) -> ctx.enqueueWork(() -> ClientZoneCache.remove(payload.zoneName()))
        );
    }
}

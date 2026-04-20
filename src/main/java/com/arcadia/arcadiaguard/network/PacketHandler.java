package com.arcadia.arcadiaguard.network;

import com.arcadia.arcadiaguard.handler.GuiActionHandler;
import com.arcadia.arcadiaguard.network.gui.DimFlagsPayload;
import com.arcadia.arcadiaguard.network.gui.GuiActionPayload;
import com.arcadia.arcadiaguard.network.gui.OpenGuiPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneLogsPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public final class PacketHandler {

    private PacketHandler() {}

    private static final Logger LOGGER = LogManager.getLogger(PacketHandler.class);

    private static final AtomicBoolean warnedDefaultHandler = new AtomicBoolean(false);

    private static <T> BiConsumer<T, IPayloadContext> defaultHandler(String name) {
        return (p, c) -> {
            if (warnedDefaultHandler.compareAndSet(false, true)) {
                LOGGER.warn("Default handler called for {} — wiring not initialized", name);
            }
        };
    }

    // Handlers S→C injectés par ArcadiaGuardClient.init() (CLIENT dist uniquement)
    private static BiConsumer<ZoneDataPayload,   IPayloadContext> clientZoneData   = defaultHandler("ZoneDataPayload");
    private static BiConsumer<OpenGuiPayload,    IPayloadContext> clientOpenGui    = defaultHandler("OpenGuiPayload");
    private static BiConsumer<ZoneDetailPayload, IPayloadContext> clientZoneDetail = defaultHandler("ZoneDetailPayload");
    private static BiConsumer<DimFlagsPayload,   IPayloadContext> clientDimFlags   = defaultHandler("DimFlagsPayload");
    private static BiConsumer<ZoneLogsPayload,   IPayloadContext> clientZoneLogs   = defaultHandler("ZoneLogsPayload");

    public static void setClientHandlers(
            BiConsumer<ZoneDataPayload,   IPayloadContext> zoneData,
            BiConsumer<OpenGuiPayload,    IPayloadContext> openGui,
            BiConsumer<ZoneDetailPayload, IPayloadContext> zoneDetail,
            BiConsumer<DimFlagsPayload,   IPayloadContext> dimFlags,
            BiConsumer<ZoneLogsPayload,   IPayloadContext> zoneLogs) {
        clientZoneData   = zoneData;
        clientOpenGui    = openGui;
        clientZoneDetail = zoneDetail;
        clientDimFlags   = dimFlags;
        clientZoneLogs   = zoneLogs;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(PacketHandler::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");

        reg.playToClient(ZoneDataPayload.TYPE,   ZoneDataPayload.STREAM_CODEC,
            (p, c) -> clientZoneData.accept(p, c));

        reg.playToClient(OpenGuiPayload.TYPE,    OpenGuiPayload.STREAM_CODEC,
            (p, c) -> clientOpenGui.accept(p, c));

        reg.playToClient(ZoneDetailPayload.TYPE, ZoneDetailPayload.STREAM_CODEC,
            (p, c) -> clientZoneDetail.accept(p, c));

        reg.playToClient(DimFlagsPayload.TYPE, DimFlagsPayload.STREAM_CODEC,
            (p, c) -> clientDimFlags.accept(p, c));

        reg.playToClient(ZoneLogsPayload.TYPE, ZoneLogsPayload.STREAM_CODEC,
            (p, c) -> clientZoneLogs.accept(p, c));

        reg.playToServer(GuiActionPayload.TYPE,  GuiActionPayload.STREAM_CODEC,
            GuiActionHandler::handle);
    }
}

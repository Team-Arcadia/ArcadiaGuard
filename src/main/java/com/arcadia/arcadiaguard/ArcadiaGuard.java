package com.arcadia.arcadiaguard;

import com.arcadia.arcadiaguard.command.ArcadiaGuardCommands;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry;
import com.arcadia.arcadiaguard.logging.ArcadiaGuardAuditLogger;
import com.arcadia.arcadiaguard.zone.ZoneManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(ArcadiaGuard.MOD_ID)
public final class ArcadiaGuard {

    public static final String MOD_ID = "arcadiaguard";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static ZoneManager zoneManager;
    private static GuardService guardService;
    private static ArcadiaGuardAuditLogger auditLogger;
    private static HandlerRegistry handlerRegistry;

    public ArcadiaGuard(IEventBus modBus, ModContainer modContainer) {
        ArcadiaGuardPaths.migrateLegacyFiles();
        modContainer.registerConfig(ModConfig.Type.COMMON, ArcadiaGuardConfig.SPEC, ArcadiaGuardPaths.commonConfigSpecPath());

        auditLogger = new ArcadiaGuardAuditLogger();
        zoneManager = new ZoneManager();
        guardService = new GuardService(zoneManager, auditLogger);
        handlerRegistry = new HandlerRegistry(guardService);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        handlerRegistry.register(NeoForge.EVENT_BUS);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ArcadiaGuardCommands.register(event.getDispatcher());
    }

    private void onServerStarting(ServerStartingEvent event) {
        zoneManager.reload(event.getServer());
        auditLogger.onServerStarted(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        auditLogger.onServerStopped();
    }

    public static ZoneManager zoneManager() {
        return zoneManager;
    }

    public static GuardService guardService() {
        return guardService;
    }
}

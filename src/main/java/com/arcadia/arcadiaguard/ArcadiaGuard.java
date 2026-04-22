package com.arcadia.arcadiaguard;

import com.arcadia.arcadiaguard.api.ArcadiaGuardAPI;
import com.arcadia.arcadiaguard.command.ArcadiaGuardCommands;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.FlagRegistryImpl;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry;
import com.arcadia.arcadiaguard.item.DynamicItemBlockList;
import com.arcadia.arcadiaguard.item.ModItems;
import com.arcadia.arcadiaguard.network.PacketHandler;
import com.arcadia.arcadiaguard.logging.ArcadiaGuardAuditLogger;
import com.arcadia.arcadiaguard.persist.AsyncZoneWriter;
import com.arcadia.arcadiaguard.persist.DimFlagSerializer;
import com.arcadia.arcadiaguard.zone.DimensionFlagStore;
import com.arcadia.arcadiaguard.zone.ZoneManager;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(ArcadiaGuard.MOD_ID)
public final class ArcadiaGuard {

    public static final String MOD_ID = "arcadiaguard";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static ServiceRegistry services;

    public ArcadiaGuard(IEventBus modBus, ModContainer modContainer) {
        ArcadiaGuardPaths.migrateLegacyFiles();
        modContainer.registerConfig(ModConfig.Type.COMMON, ArcadiaGuardConfig.SPEC, ArcadiaGuardPaths.commonConfigSpecPath());

        var flagRegistry = new FlagRegistryImpl();
        flagRegistry.registerBuiltins();

        var asyncZoneWriter = new AsyncZoneWriter();
        var auditLogger = new ArcadiaGuardAuditLogger();
        var dimFlagStore = new DimensionFlagStore();
        var zoneManager = new ZoneManager(flagRegistry, asyncZoneWriter);
        var guardService = new GuardService(zoneManager, auditLogger, dimFlagStore);
        ArcadiaGuardAPI.setup(flagRegistry, guardService, zoneManager);
        var dynamicItemBlockList = new DynamicItemBlockList(ArcadiaGuardPaths.blockedItemsFile());
        var handlerRegistry = new HandlerRegistry(guardService, dynamicItemBlockList);
        services = new ServiceRegistry(flagRegistry, zoneManager, guardService, auditLogger,
            handlerRegistry, dynamicItemBlockList, asyncZoneWriter, dimFlagStore);

        ModItems.register(modBus);
        PacketHandler.register(modBus);
        com.arcadia.arcadiaguard.test.ArcadiaGuardTestRegistry.register(modBus);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Indirect call via FQN string + reflection ensures the client class
            // (which transitively loads Minecraft client classes) is never linked
            // in dedicated-server JVMs that never reach this branch.
            try {
                Class.forName("com.arcadia.arcadiaguard.client.ArcadiaGuardClient")
                    .getDeclaredMethod("init").invoke(null);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("[ArcadiaGuard] Failed to initialize client hooks", e);
            }
        }

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.register(zoneManager);
        handlerRegistry.register(NeoForge.EVENT_BUS);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ArcadiaGuardCommands.register(event.getDispatcher());
    }

    private void onServerStarting(ServerStartingEvent event) {
        services.asyncZoneWriter().start();
        services.zoneManager().reload(event.getServer());
        services.dynamicItemBlockList().load();
        try { DimFlagSerializer.read(services.dimFlagStore(), ArcadiaGuardPaths.dimFlagsFile()); }
        catch (java.io.IOException e) { LOGGER.error("[ArcadiaGuard] Failed to load dimension flags", e); }
        services.auditLogger().onServerStarted(event.getServer());
        com.arcadia.arcadiaguard.compat.luckperms.LuckPermsCompat.init();
        // Re-apply chunkload for zones with CHUNKLOAD flag active
        com.arcadia.arcadiaguard.zone.ZoneChunkLoader.refreshAll(event.getServer(), services.guardService());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        services.auditLogger().onServerStopped();
        services.asyncZoneWriter().stop();
    }

    public static ServiceRegistry services() { return services; }

    public static FlagRegistryImpl flagRegistry()             { return services.flagRegistry(); }
    public static ZoneManager zoneManager()                   { return services.zoneManager(); }
    public static GuardService guardService()                 { return services.guardService(); }
    public static DynamicItemBlockList dynamicItemBlockList() { return services.dynamicItemBlockList(); }
    public static AsyncZoneWriter asyncZoneWriter()           { return services.asyncZoneWriter(); }
    public static DimensionFlagStore dimFlagStore()           { return services.dimFlagStore(); }
    public static ArcadiaGuardAuditLogger auditLogger()       { return services.auditLogger(); }
}

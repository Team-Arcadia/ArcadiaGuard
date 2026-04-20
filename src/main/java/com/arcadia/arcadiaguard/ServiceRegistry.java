package com.arcadia.arcadiaguard;

import com.arcadia.arcadiaguard.flag.FlagRegistryImpl;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry;
import com.arcadia.arcadiaguard.item.DynamicItemBlockList;
import com.arcadia.arcadiaguard.logging.ArcadiaGuardAuditLogger;
import com.arcadia.arcadiaguard.persist.AsyncZoneWriter;
import com.arcadia.arcadiaguard.zone.DimensionFlagStore;
import com.arcadia.arcadiaguard.zone.ZoneManager;

/**
 * Immutable container for all ArcadiaGuard runtime services.
 *
 * <p>Obtain the singleton via {@link ArcadiaGuard#services()} after server start.
 * All components are non-null after construction — construction only happens once
 * at the end of {@link ArcadiaGuard#ArcadiaGuard} when all services are ready.
 *
 * <p>Using a record guarantees that all fields are final and that no service can be
 * replaced after initialization, eliminating the NPE window that existed when 8
 * individual static fields were initialized one by one.
 */
public record ServiceRegistry(
    FlagRegistryImpl flagRegistry,
    ZoneManager zoneManager,
    GuardService guardService,
    ArcadiaGuardAuditLogger auditLogger,
    HandlerRegistry handlerRegistry,
    DynamicItemBlockList dynamicItemBlockList,
    AsyncZoneWriter asyncZoneWriter,
    DimensionFlagStore dimFlagStore
) {}

package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.handlers.ArsNouveauHandler;
import com.arcadia.arcadiaguard.handler.handlers.ApotheosisHandler;
import com.arcadia.arcadiaguard.handler.handlers.BetterArcheologyHandler;
import com.arcadia.arcadiaguard.handler.handlers.SpawnBookHandler;
import com.arcadia.arcadiaguard.handler.handlers.ZoneItemHandler;
import com.arcadia.arcadiaguard.handler.handlers.IronsSpellbooksHandler;
import com.arcadia.arcadiaguard.handler.handlers.OccultismHandler;
import com.arcadia.arcadiaguard.handler.handlers.SimplySwordsHandler;
import com.arcadia.arcadiaguard.handler.handlers.SupplementariesHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.util.List;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class HandlerRegistry {

    private final List<Object> handlers;

    public HandlerRegistry(GuardService guardService) {
        this.handlers = List.of(
            new IronsSpellbooksHandler(guardService),
            new ArsNouveauHandler(guardService),
            new SimplySwordsHandler(guardService),
            new OccultismHandler(guardService),
            new SupplementariesHandler(guardService),
            new ApotheosisHandler(guardService),
            new BetterArcheologyHandler(guardService),
            new SpawnBookHandler(guardService),
            new ZoneItemHandler(guardService)
        );
    }

    public void register(IEventBus ignored) {
        for (Object handler : this.handlers) {
            if (handler instanceof DynamicEventHandler dynamic) {
                registerDynamic(dynamic);
            }
        }
        NeoForge.EVENT_BUS.addListener(this::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, BlockEvent.BreakEvent.class, this::onBlockBreak);
    }

    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        for (Object handler : this.handlers) {
            if (handler instanceof RightClickItemHandler specific) specific.handle(event);
        }
    }

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        for (Object handler : this.handlers) {
            if (handler instanceof RightClickBlockHandler specific) specific.handle(event);
        }
    }

    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        for (Object handler : this.handlers) {
            if (handler instanceof EntityInteractHandler specific) specific.handle(event);
        }
    }

    public void onBlockBreak(BlockEvent.BreakEvent event) {
        for (Object handler : this.handlers) {
            if (handler instanceof BlockBreakHandler specific) specific.handle(event);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void registerDynamic(DynamicEventHandler handler) {
        Object eventClass = ReflectionHelper.invokeStatic("java.lang.Class", "forName", new Class<?>[] { String.class }, handler.eventClassName()).orElse(null);
        if (!(eventClass instanceof Class<?> clazz)) return;
        NeoForge.EVENT_BUS.addListener((Class) clazz, event -> handler.handle((Event) event));
    }

    public interface DynamicEventHandler {
        String eventClassName();
        void handle(Event event);
    }

    public interface RightClickItemHandler { void handle(PlayerInteractEvent.RightClickItem event); }
    public interface RightClickBlockHandler { void handle(PlayerInteractEvent.RightClickBlock event); }
    public interface EntityInteractHandler { void handle(PlayerInteractEvent.EntityInteract event); }
    public interface BlockBreakHandler { void handle(BlockEvent.BreakEvent event); }
}

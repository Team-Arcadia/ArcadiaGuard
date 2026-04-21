package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.item.DynamicItemBlockList;
import com.arcadia.arcadiaguard.handler.handlers.ArsNouveauHandler;
import com.arcadia.arcadiaguard.handler.handlers.ApotheosisCharmHandler;
import com.arcadia.arcadiaguard.handler.handlers.ApotheosisHandler;
import com.arcadia.arcadiaguard.handler.handlers.BetterArcheologyHandler;
import com.arcadia.arcadiaguard.handler.handlers.EmotecraftHandler;
import com.arcadia.arcadiaguard.handler.handlers.MutantMonstersHandler;
import com.arcadia.arcadiaguard.handler.handlers.ParcoolHandler;
import com.arcadia.arcadiaguard.handler.handlers.SpawnBookHandler;
import com.arcadia.arcadiaguard.handler.handlers.TwilightForestHandler;
import com.arcadia.arcadiaguard.handler.handlers.ZoneItemHandler;
import com.arcadia.arcadiaguard.handler.handlers.IronsSpellbooksHandler;
import com.arcadia.arcadiaguard.handler.handlers.OccultismHandler;
import com.arcadia.arcadiaguard.handler.handlers.SimplySwordsHandler;
import com.arcadia.arcadiaguard.handler.handlers.SophisticatedStorageHandler;
import com.arcadia.arcadiaguard.handler.handlers.SupplementariesHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.util.List;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class HandlerRegistry {

    private final GuardService guardService;
    private final BlockEventHandler blockEventHandler;
    private final EntityEventHandler entityEventHandler;
    private final PlayerEventHandler playerEventHandler;
    private final FlagEventHandler flagEventHandler;
    private final MutantMonstersHandler mutantMonstersHandler;
    private final TwilightForestHandler twilightForestHandler;
    private final SophisticatedStorageHandler sophisticatedStorageHandler;
    private final SimplySwordsHandler simplySwordsHandlerRef;
    private final List<Object> handlers;

    // H-P8: pre-typed arrays for O(1) dispatch without instanceof on every event
    private RightClickItemHandler[] rightClickItemHandlers;
    private RightClickBlockHandler[] rightClickBlockHandlers;
    private EntityInteractHandler[] entityInteractHandlers;
    private BlockBreakHandler[] blockBreakHandlers;

    public HandlerRegistry(GuardService guardService, DynamicItemBlockList dynamicItemBlockList) {
        this.guardService = guardService;
        this.blockEventHandler = new BlockEventHandler(guardService);
        this.entityEventHandler = new EntityEventHandler(guardService);
        this.flagEventHandler = new FlagEventHandler(guardService);
        this.mutantMonstersHandler = new MutantMonstersHandler(guardService);
        this.twilightForestHandler = new TwilightForestHandler(guardService);
        this.sophisticatedStorageHandler = new SophisticatedStorageHandler(guardService);
        SimplySwordsHandler simplySwordsHandler = new SimplySwordsHandler(guardService);
        this.simplySwordsHandlerRef = simplySwordsHandler;
        ApotheosisCharmHandler charmHandler = new ApotheosisCharmHandler(guardService);
        this.playerEventHandler = new PlayerEventHandler(guardService, charmHandler);
        this.handlers = List.of(
            this.playerEventHandler,
            new IronsSpellbooksHandler(guardService),
            new ArsNouveauHandler(guardService),
            simplySwordsHandler,
            new OccultismHandler(guardService),
            new SupplementariesHandler(guardService),
            new ApotheosisHandler(guardService),
            new BetterArcheologyHandler(guardService),
            new SpawnBookHandler(guardService),
            new ZoneItemHandler(guardService, dynamicItemBlockList),
            new ParcoolHandler(guardService),
            charmHandler
        );
        // H-P8: build typed arrays so dispatch loops cast once at boot, not on every event
        this.rightClickItemHandlers = this.handlers.stream()
            .filter(h -> h instanceof RightClickItemHandler)
            .toArray(RightClickItemHandler[]::new);
        this.rightClickBlockHandlers = this.handlers.stream()
            .filter(h -> h instanceof RightClickBlockHandler)
            .toArray(RightClickBlockHandler[]::new);
        this.entityInteractHandlers = this.handlers.stream()
            .filter(h -> h instanceof EntityInteractHandler)
            .toArray(EntityInteractHandler[]::new);
        this.blockBreakHandlers = this.handlers.stream()
            .filter(h -> h instanceof BlockBreakHandler)
            .toArray(BlockBreakHandler[]::new);
    }

    public void register(IEventBus ignored) {
        // H-P7: register static event listeners for cache invalidation
        IronsSpellbooksHandler.registerEventListeners();
        // Mod integrations that register their own listeners
        EmotecraftHandler.register(guardService);

        for (Object handler : this.handlers) {
            if (handler instanceof DynamicEventHandler dynamic) {
                registerDynamic(dynamic);
            }
        }
        NeoForge.EVENT_BUS.addListener(this::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, BlockEvent.BreakEvent.class, this::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, BlockEvent.EntityPlaceEvent.class, blockEventHandler::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, PlayerInteractEvent.LeftClickBlock.class, playerEventHandler::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(playerEventHandler::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, playerEventHandler::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, LivingIncomingDamageEvent.class, entityEventHandler::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, LivingFallEvent.class, entityEventHandler::onLivingFall);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, FinalizeSpawnEvent.class, entityEventHandler::onMobSpawn);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ExplosionEvent.Detonate.class, entityEventHandler::onExplosion);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerTickEvent.Post.class, playerEventHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTickEvent.Post.class, entityEventHandler::onEntityTick);

        // FlagEventHandler : interactions / combats / items / téléportations / croissance
        // S-H16 T1 : Sophisticated Storage en HIGHEST — doit s'executer avant FlagEventHandler
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.RightClickBlock.class, sophisticatedStorageHandler::onRightClickBlock);
        // S-H16 AC1 : minecarts-coffres et chest-boats (Entity, pas BlockEntity)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.EntityInteract.class, sophisticatedStorageHandler::onEntityInteract);
        // S-H16 T7 : SimplySwords abilities declenchees au chargement de l'item
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
            net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start.class,
            simplySwordsHandlerRef::onUseItemStart);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerInteractEvent.RightClickBlock.class, flagEventHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerInteractEvent.RightClickItem.class, flagEventHandler::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, AttackEntityEvent.class, flagEventHandler::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ItemTossEvent.class, flagEventHandler::onItemToss);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ItemEntityPickupEvent.Pre.class, flagEventHandler::onItemPickup);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingExperienceDropEvent.class, flagEventHandler::onExpDrop);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTeleportEvent.EnderPearl.class, flagEventHandler::onEnderPearlTeleport);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTeleportEvent.ChorusFruit.class, flagEventHandler::onChorusFruitTeleport);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, CropGrowEvent.Pre.class, flagEventHandler::onCropGrow);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BonemealEvent.class, flagEventHandler::onBonemeal);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockGrowFeatureEvent.class, flagEventHandler::onTreeGrow);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.FarmlandTrampleEvent.class, flagEventHandler::onFarmlandTrample);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.FluidPlaceBlockEvent.class, flagEventHandler::onFluidPlaceBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.EntityPlaceEvent.class, flagEventHandler::onBlockEntityPlace);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, entityEventHandler::onAnimalJoinLevel);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, EntityJoinLevelEvent.class, flagEventHandler::onVehicleJoin);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, mutantMonstersHandler::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ProjectileImpactEvent.class, twilightForestHandler::onProjectileImpact);
    }

    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        // H-P8: iterate pre-typed array — no instanceof check on every element
        for (RightClickItemHandler h : rightClickItemHandlers) {
            if (event.isCanceled()) break;
            h.handle(event);
        }
    }

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        for (RightClickBlockHandler h : rightClickBlockHandlers) {
            if (event.isCanceled()) break;
            h.handle(event);
        }
    }

    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        for (EntityInteractHandler h : entityInteractHandlers) {
            if (event.isCanceled()) break;
            h.handle(event);
        }
    }

    public void onBlockBreak(BlockEvent.BreakEvent event) {
        blockEventHandler.onBlockBreak(event);
        if (event.isCanceled()) return;
        for (BlockBreakHandler h : blockBreakHandlers) {
            h.handle(event);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void registerDynamic(DynamicEventHandler handler) {
        Object eventClass = ReflectionHelper.invokeStatic("java.lang.Class", "forName",
            new Class<?>[] { String.class }, handler.eventClassName()).orElse(null);
        if (!(eventClass instanceof Class<?> clazz)) return;
        // H11: guard against non-Event classes being passed to addListener
        if (!net.neoforged.bus.api.Event.class.isAssignableFrom(clazz)) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Skipping handler {}: {} is not an Event subclass",
                handler.getClass().getSimpleName(), clazz.getName());
            return;
        }
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

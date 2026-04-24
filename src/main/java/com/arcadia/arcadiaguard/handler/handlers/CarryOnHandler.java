package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Gere le flag CARRYON : bloque le mod Carry On (ramasser/transporter blocs + entites).
 *
 * <p>Strategie multi-couches :
 * <ul>
 *   <li><b>RightClickBlock</b> HIGHEST priority : si player sneak + empty hand + deny zone
 *       -> cancel l'event avant que Carry On ne le traite.</li>
 *   <li><b>EntityPickupEvent</b> (event custom Carry On, cancellable) : cancel via reflexion
 *       dans un listener generic Event.</li>
 *   <li><b>PlayerTickEvent.Post</b> (20 ticks) : check si le joueur CARRY deja qqch en entrant
 *       en zone deny, force-drop via CarryOnDataManager.setCarryData(emptyData) reflexion.</li>
 * </ul>
 *
 * <p>Zero dependance compile-time : tout via ReflectionHelper + Class.forName.
 */
public final class CarryOnHandler {

    private static final String MOD_ID = "carryon";
    private static final String DATA_MANAGER_CLS = "tschipp.carryon.common.carry.CarryOnDataManager";
    private static final String DATA_CLS         = "tschipp.carryon.common.carry.CarryOnData";
    private static final String PICKUP_EVENT_CLS = "tschipp.carryon.events.EntityPickupEvent";

    private final GuardService guard;

    public CarryOnHandler(GuardService guard) {
        this.guard = guard;
    }

    /** Enregistre les listeners d'events NeoForge + l'event custom EntityPickupEvent. */
    public void register() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            ArcadiaGuard.LOGGER.info("[ArcadiaGuard] Carry On non detecte, flag CARRYON inactif.");
            return;
        }
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true,
            PlayerInteractEvent.RightClickBlock.class, this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
            PlayerTickEvent.Post.class, this::onPlayerTick);
        // Event custom : on passe la classe concrete EntityPickupEvent (NeoForge refuse Event.class abstract).
        try {
            Class<?> pickupCls = Class.forName(PICKUP_EVENT_CLS);
            registerPickupListener(pickupCls);
            ArcadiaGuard.LOGGER.info("[ArcadiaGuard] Carry On handler enregistre (EntityPickupEvent + RightClickBlock + PlayerTick).");
        } catch (ClassNotFoundException e) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Carry On EntityPickupEvent introuvable : pickup entite non bloque via event. RightClickBlock suffit pour la plupart des cas.");
        } catch (Throwable t) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Carry On EntityPickupEvent listener registration failed: {}", t.toString());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerPickupListener(Class<?> pickupCls) {
        Class raw = pickupCls;
        NeoForge.EVENT_BUS.addListener(raw, (java.util.function.Consumer) (Object event) -> handleCustomPickupEvent(event));
    }

    /** Cancel RightClickBlock si player sneak + empty hand en zone deny (pattern trigger Carry On). */
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown()) return;
        if (!event.getItemStack().isEmpty()) return;
        if (guard.shouldBypass(player)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        BlockPos pos = event.getPos();
        ProtectedZone zone = guard.zoneManager().findZoneContaining(level, pos)
            .map(z -> (ProtectedZone) z).orElse(null);
        if (zone != null && guard.isZoneMember(player, zone)) return;
        if (!guard.isZoneDenying(level, pos, BuiltinFlags.CARRYON)) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        sendDeny(player);
        String zoneName = zone != null ? zone.name() : "(dimension)";
        guard.auditDenied(player, zoneName, pos, BuiltinFlags.CARRYON, "carryon");
    }

    /** Handler du custom EntityPickupEvent de Carry On : cancel si deny zone. */
    private void handleCustomPickupEvent(Object event) {
        if (!(event instanceof ICancellableEvent cancellable)) return;
        ServerPlayer player = (ServerPlayer) ReflectionHelper.field(event, "player").orElse(null);
        if (player == null) return;
        if (guard.shouldBypass(player)) return;
        BlockPos pos = player.blockPosition();
        ProtectedZone zone = guard.zoneManager().findZoneContaining(player.level(), pos)
            .map(z -> (ProtectedZone) z).orElse(null);
        if (zone != null && guard.isZoneMember(player, zone)) return;
        if (!guard.isZoneDenying(player.level(), pos, BuiltinFlags.CARRYON)) return;
        cancellable.setCanceled(true);
        sendDeny(player);
        String zoneName = zone != null ? zone.name() : "(dimension)";
        guard.auditDenied(player, zoneName, pos, BuiltinFlags.CARRYON, "carryon_entity");
    }

    /** Force-drop si le joueur porte deja qqch en entrant dans une zone deny. Check toutes les 20 ticks. */
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;
        if (player.level().isClientSide()) return;
        if (guard.shouldBypass(player)) return;
        BlockPos pos = player.blockPosition();
        ProtectedZone zone = guard.zoneManager().findZoneContaining(player.level(), pos)
            .map(z -> (ProtectedZone) z).orElse(null);
        if (zone != null && guard.isZoneMember(player, zone)) return;
        if (!guard.isZoneDenying(player.level(), pos, BuiltinFlags.CARRYON)) return;
        if (isCarrying(player) && forceDrop(player)) {
            sendDeny(player);
            String zoneName = zone != null ? zone.name() : "(dimension)";
            guard.auditDenied(player, zoneName, pos, BuiltinFlags.CARRYON, "carryon_forced_drop");
        }
    }

    private static boolean isCarrying(ServerPlayer player) {
        try {
            Object data = invokeStaticDataManager("getCarryData", player);
            if (data == null) return false;
            Object result = ReflectionHelper.invoke(data, "isCarrying", new Class<?>[0]).orElse(Boolean.FALSE);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) { return false; }
    }

    private static boolean forceDrop(ServerPlayer player) {
        try {
            Class<?> dataCls = Class.forName(DATA_CLS);
            Object emptyData = dataCls.getConstructor(net.minecraft.nbt.CompoundTag.class)
                .newInstance(new net.minecraft.nbt.CompoundTag());
            Class<?> mgrCls = Class.forName(DATA_MANAGER_CLS);
            Method setter = mgrCls.getMethod("setCarryData", net.minecraft.world.entity.player.Player.class, dataCls);
            setter.invoke(null, player, emptyData);
            return true;
        } catch (Throwable t) {
            if (!FORCE_DROP_WARNED) {
                FORCE_DROP_WARNED = true;
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Carry On force-drop reflection failed (logged once): {}", t.toString());
            }
            return false;
        }
    }

    private static volatile boolean FORCE_DROP_WARNED = false;

    private static Object invokeStaticDataManager(String methodName, ServerPlayer player) throws Exception {
        Class<?> cls = Class.forName(DATA_MANAGER_CLS);
        Method m = cls.getMethod(methodName, net.minecraft.world.entity.player.Player.class);
        return m.invoke(null, player);
    }

    private static void sendDeny(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("arcadiaguard.message.carryon").withStyle(ChatFormatting.RED));
    }
}

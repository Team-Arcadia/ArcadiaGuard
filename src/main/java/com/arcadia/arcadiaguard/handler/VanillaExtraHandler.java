package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handlers pour les flags vanilla additionnels (parite YAWP) :
 * use-elytra, use-portal, till-farmland, strip-wood, shovel-path,
 * scoop-fluids, place-fluids, send-chat, exec-command.
 */
public final class VanillaExtraHandler {

    private final GuardService guard;

    public VanillaExtraHandler(GuardService guard) {
        this.guard = guard;
    }

    /** Bloque l'elytra dans les zones avec USE_ELYTRA=deny. Tick toutes les 10 ticks via PlayerEventHandler. */
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isFallFlying()) return;
        if (player.tickCount % 10 != 0) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        if (guard.shouldBypass(player)) return;
        BlockPos pos = player.blockPosition();
        ProtectedZone zone = zoneAt(level, pos);
        if (zone != null && guard.isZoneMember(player, zone)) return;
        if (!guard.isZoneDenying(level, pos, BuiltinFlags.USE_ELYTRA)) return;
        player.stopFallFlying();
        String zoneName = zone != null ? zone.name() : "(dimension)";
        sendDeny(player, "use_elytra", zoneName);
        guard.auditDenied(player, zoneName, pos, BuiltinFlags.USE_ELYTRA, "use_elytra");
    }

    /** Empeche le changement de dimension via portail si USE_PORTAL=deny. */
    public void onTravelDim(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        if (guard.shouldBypass(player)) return;
        BlockPos pos = player.blockPosition();
        ProtectedZone zone = zoneAt(level, pos);
        if (zone != null && guard.isZoneMember(player, zone)) return;
        if (!guard.isZoneDenying(level, pos, BuiltinFlags.USE_PORTAL)) return;
        event.setCanceled(true);
        String zoneName = zone != null ? zone.name() : "(dimension)";
        sendDeny(player, "use_portal", zoneName);
        guard.auditDenied(player, zoneName, pos, BuiltinFlags.USE_PORTAL, "use_portal");
    }

    /** Couvre till-farmland (houe), strip-wood (hache), shovel-path (pelle). */
    public void onBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        if (guard.shouldBypass(player)) return;
        BlockPos pos = event.getPos();
        ProtectedZone zone = zoneAt(level, pos);
        if (zone != null && guard.isZoneMember(player, zone)) return;

        BooleanFlag flag = switch (event.getItemAbility().name()) {
            case "till" -> BuiltinFlags.TILL_FARMLAND;
            case "strip" -> BuiltinFlags.STRIP_WOOD;
            case "shovel_flatten" -> BuiltinFlags.SHOVEL_PATH;
            default -> null;
        };
        if (flag == null) return;
        if (!guard.isZoneDenying(level, pos, flag)) return;
        event.setCanceled(true);
        String action = flag.id().replace('-', '_');
        sendDeny(player, action);
        String zoneName = zone != null ? zone.name() : "(dimension)";
        guard.auditDenied(player, zoneName, pos, flag, action);
    }

    /** Helper : resout la zone a une position (null si hors zone). */
    private ProtectedZone zoneAt(Level level, BlockPos pos) {
        return guard.zoneManager().findZoneContaining(level, pos)
            .map(z -> (ProtectedZone) z).orElse(null);
    }

    /** SCOOP_FLUIDS (seau vide -> rempli) + PLACE_FLUIDS (seau rempli -> pose).
     * Handle via RightClickBlock (raytrace fluide touche le bloc cible) ET RightClickItem
     * (clic dans le vide, seau fait son propre raytrace). Covers both paths cote NeoForge 1.21.1. */
    public void onBucketUse(PlayerInteractEvent.RightClickBlock event) {
        handleBucket(event.getEntity(), event.getItemStack(), event.getPos(), event);
    }

    public void onBucketUseItem(PlayerInteractEvent.RightClickItem event) {
        handleBucket(event.getEntity(), event.getItemStack(), event.getEntity().blockPosition(), event);
    }

    private void handleBucket(net.minecraft.world.entity.player.Player p, ItemStack stack, BlockPos pos,
                              net.neoforged.neoforge.event.entity.player.PlayerInteractEvent event) {
        if (!(p instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        if (!(stack.getItem() instanceof BucketItem)) return;
        if (guard.shouldBypass(player)) return;
        ProtectedZone zone = zoneAt(level, pos);
        if (zone != null && guard.isZoneMember(player, zone)) return;

        boolean empty = stack.is(Items.BUCKET);
        BooleanFlag flag = empty ? BuiltinFlags.SCOOP_FLUIDS : BuiltinFlags.PLACE_FLUIDS;
        if (!guard.isZoneDenying(level, pos, flag)) return;
        // Cancel + FAIL result pour empecher BucketItem.use() de s'executer dans tous les cas.
        if (event instanceof PlayerInteractEvent.RightClickBlock rcb) {
            rcb.setCanceled(true);
            rcb.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        } else if (event instanceof PlayerInteractEvent.RightClickItem rci) {
            rci.setCanceled(true);
            rci.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
        String action = empty ? "scoop_fluids" : "place_fluids";
        sendDeny(player, action);
        String zoneName = zone != null ? zone.name() : "(dimension)";
        guard.auditDenied(player, zoneName, pos, flag, action);
    }

    /** Bloque l'envoi de messages chat si SEND_CHAT=deny (zone ou dimension). */
    public void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) return;
        Level level = player.level();
        if (guard.shouldBypass(player)) return;
        BlockPos pos = player.blockPosition();
        ProtectedZone zone = zoneAt(level, pos);
        if (zone != null && guard.isZoneMember(player, zone)) return;
        if (!guard.isZoneDenying(level, pos, BuiltinFlags.SEND_CHAT)) return;
        event.setCanceled(true);
        String zoneName = zone != null ? zone.name() : "(dimension)";
        sendDeny(player, "send_chat", zoneName);
        guard.auditDenied(player, zoneName, pos, BuiltinFlags.SEND_CHAT, "send_chat");
    }

    /**
     * Bloque l'execution de commandes slash selon 2 modes :
     *  - EXEC_COMMAND=deny -> bloque TOUTES les commandes (sauf /ag)
     *  - Sinon, si EXEC_COMMAND_BLACKLIST non vide -> bloque les commandes listees
     * Les deux modes coexistent : deny bloque tout, la blacklist affine.
     * Notes : la blacklist par liste ne fonctionne que sur une zone (pas de semantique dim).
     * EXEC_COMMAND peut etre defini au niveau dimension -> applique partout hors zone.
     */
    public void onCommand(CommandEvent event) {
        Entity sender = event.getParseResults().getContext().getSource().getEntity();
        if (!(sender instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (guard.shouldBypass(player)) return;
        BlockPos pos = player.blockPosition();
        ProtectedZone zone = zoneAt(level, pos);
        if (zone != null && guard.isZoneMember(player, zone)) return;

        String rawCmd = event.getParseResults().getReader().getString();
        String firstToken = extractCommandToken(rawCmd);
        // Exception : toujours autoriser les commandes ArcadiaGuard (anti lock-out admin).
        if (firstToken.equals("ag") || firstToken.equals("arcadiaguard")) return;

        String zoneName = zone != null ? zone.name() : "(dimension)";
        boolean denyAll = guard.isZoneDenying(level, pos, BuiltinFlags.EXEC_COMMAND);

        if (denyAll) {
            event.setCanceled(true);
            sendDeny(player, "exec_command", zoneName);
            guard.auditDenied(player, zoneName, pos, BuiltinFlags.EXEC_COMMAND, "exec_command");
            return;
        }

        // Mode blacklist : uniquement si une zone concrete fournit la liste (pas de lookup dim pour la liste).
        if (zone == null) return;
        java.util.List<String> blacklist = readBlacklist(zone);
        if (blacklist.isEmpty()) return;
        for (String entry : blacklist) {
            String normalized = entry.startsWith("/") ? entry.substring(1) : entry;
            normalized = normalized.toLowerCase(java.util.Locale.ROOT);
            if (firstToken.equalsIgnoreCase(normalized)) {
                event.setCanceled(true);
                sendDeny(player, "exec_command_blacklist", firstToken);
                guard.auditDenied(player, zoneName, pos,
                    BuiltinFlags.EXEC_COMMAND, "exec_command:" + firstToken);
                return;
            }
        }
    }

    /** Extrait le 1er token d'une commande "/tp Steve 0 0 0" -> "tp". */
    private static String extractCommandToken(String raw) {
        int start = raw.startsWith("/") ? 1 : 0;
        int end = raw.length();
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '\t') { end = i; break; }
        }
        return raw.substring(start, end).toLowerCase(java.util.Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> readBlacklist(ProtectedZone zone) {
        Object raw = zone.flagValues().getOrDefault(BuiltinFlags.EXEC_COMMAND_BLACKLIST.id(), java.util.List.of());
        if (!(raw instanceof java.util.List<?> list)) return java.util.List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    /** Overload simple pour messages non-parametres. */
    private static void sendDeny(ServerPlayer player, String key) {
        sendDeny(player, key, null);
    }

    /**
     * Dispatch d'un message actionbar. Si {@code zoneName != null} le nom est passe comme
     * argument {@code %1$s} — les lang keys peuvent l'utiliser pour afficher "dans la zone X".
     */
    private static void sendDeny(ServerPlayer player, String key, String zoneName) {
        Component msg = zoneName != null
            ? Component.translatable("arcadiaguard.message." + key, zoneName)
            : Component.translatable("arcadiaguard.message." + key);
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
    }
}

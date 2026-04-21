package com.arcadia.arcadiaguard.zone;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.api.IZoneManager;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.flag.IntFlag;
import com.arcadia.arcadiaguard.api.flag.ListFlag;
import com.arcadia.arcadiaguard.api.zone.IZone;
import com.arcadia.arcadiaguard.api.zone.ZoneCheckResult;
import com.arcadia.arcadiaguard.event.ZoneLifecycleEvent;
import com.arcadia.arcadiaguard.flag.FlagRegistryImpl;
import com.arcadia.arcadiaguard.flag.FlagResolver;
import com.arcadia.arcadiaguard.util.FlagUtils;
import com.arcadia.arcadiaguard.item.WandItem;
import com.arcadia.arcadiaguard.network.gui.OpenGuiPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload.Detail;
import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload.FlagEntry;
import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload.MemberEntry;
import com.arcadia.arcadiaguard.persist.AsyncZoneWriter;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public final class ZoneManager implements IZoneManager {

    private static final int PAGE_SIZE = 50;

    private final InternalZoneProvider internal;
    private final FlagRegistryImpl flagRegistry;

    public ZoneManager(FlagRegistryImpl flagRegistry, AsyncZoneWriter asyncZoneWriter) {
        this.flagRegistry = flagRegistry;
        this.internal = new InternalZoneProvider(flagRegistry, asyncZoneWriter);
    }

    // ── Client response helpers ──────────────────────────────────────────────────

    public void sendDetailToPlayer(ServerPlayer player, String zoneName) {
        Level level = player.serverLevel();
        @SuppressWarnings("unchecked")
        Optional<ProtectedZone> opt = (Optional<ProtectedZone>)(Optional<?>) this.internal.get(level, zoneName);
        if (opt.isEmpty()) {
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.translatable(
                    "arcadiaguard.gui.action.zone_not_found", zoneName)
                .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        ProtectedZone zone = opt.get();
        MinecraftServer server = player.getServer();
        List<FlagEntry> flags = new ArrayList<>();
        for (Flag<?> flag : this.flagRegistry.all()) {
            String mod = flag.requiredMod();
            if (!mod.isEmpty() && !ModList.get().isLoaded(mod)) continue;
            Object raw = zone.flagValues().get(flag.id());
            boolean inherited = (raw == null);
            byte type;
            boolean boolVal = false;
            String strVal;
            if (flag instanceof BooleanFlag bf) {
                type = FlagEntry.TYPE_BOOL;
                if (raw instanceof Boolean b) { boolVal = b; }
                else {
                    @SuppressWarnings("unchecked")
                    java.util.function.Function<String, Optional<ProtectedZone>> lookup =
                        n -> (Optional<ProtectedZone>)(Optional<?>) this.internal.get(level, n);
                    boolVal = FlagResolver.resolve(zone, bf, lookup);
                }
                strVal = Boolean.toString(boolVal);
            } else if (flag instanceof IntFlag intF) {
                type = FlagEntry.TYPE_INT;
                int v = raw instanceof Integer i ? i : intF.defaultValue();
                strVal = Integer.toString(v);
            } else if (flag instanceof ListFlag) {
                type = FlagEntry.TYPE_LIST;
                @SuppressWarnings("unchecked")
                List<String> v = raw instanceof List<?> l ? (List<String>) l : List.of();
                String joined = String.join(",", v);
                strVal = joined.length() > 30_000 ? joined.substring(0, 29_990) + ",…" : joined;
            } else { continue; }
            String desc = flag.description();
            if (desc != null && desc.length() > 1024) desc = desc.substring(0, 1021) + "…";
            flags.add(new FlagEntry(flag.id(), FlagUtils.formatFlagLabel(flag.id()),
                boolVal, inherited, desc != null ? desc : "", type, strVal));
        }
        List<MemberEntry> members = new ArrayList<>();
        for (UUID uuid : zone.whitelistedPlayers()) {
            String name = server.getProfileCache().get(uuid)
                .map(GameProfile::getName)
                .orElse(uuid.toString().substring(0, 8) + "…");
            members.add(new MemberEntry(uuid.toString(), name));
        }
        List<String> blockedItems = new ArrayList<>();
        for (var id : zone.blockedItems()) blockedItems.add(id.toString());
        java.util.Collections.sort(blockedItems);
        Detail detail = new Detail(zone.name(), zone.dimension(),
            zone.minX(), zone.minY(), zone.minZ(),
            zone.maxX(), zone.maxY(), zone.maxZ(),
            zone.parent(), flags, members, zone.enabled(), zone.inheritDimFlags(),
            blockedItems);
        PacketDistributor.sendToPlayer(player, new ZoneDetailPayload(detail));
    }

    public void sendRefreshedList(ServerPlayer player) {
        sendRefreshedList(player, 1);
    }

    public void sendRefreshedList(ServerPlayer player, int page) {
        // S-H18 : collecter les zones de TOUTES les dimensions pour que la sidebar
        // affiche les counts corrects et les dimensions modées même si le joueur n'y est pas.
        @SuppressWarnings("unchecked")
        Collection<ProtectedZone> zones = (Collection<ProtectedZone>)(Collection<?>) this.internal.allZones(player.getServer());
        List<OpenGuiPayload.ZoneEntry> all = zones.stream()
            .map(z -> new OpenGuiPayload.ZoneEntry(
                z.name(), z.dimension(),
                z.minX(), z.minY(), z.minZ(),
                z.maxX(), z.maxY(), z.maxZ(),
                z.whitelistedPlayers().size(),
                z.parent() != null,
                (int) z.flagValues().values().stream().filter(v -> v instanceof Boolean).count(),
                z.enabled()))
            .sorted(java.util.Comparator.comparing(OpenGuiPayload.ZoneEntry::name))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        int total = all.size();
        int pages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        int p = Math.min(Math.max(page, 1), pages);
        List<OpenGuiPayload.ZoneEntry> pageEntries = all.subList((p - 1) * PAGE_SIZE, Math.min(p * PAGE_SIZE, total));

        BlockPos p1 = WandItem.getPos1(player.getUUID());
        BlockPos p2 = WandItem.getPos2(player.getUUID());
        long lp1 = p1 != null ? p1.asLong() : OpenGuiPayload.NO_POS;
        long lp2 = p2 != null ? p2.asLong() : OpenGuiPayload.NO_POS;
        boolean debugMode = ArcadiaGuard.guardService().isDebugMode(player.getUUID());

        PacketDistributor.sendToPlayer(player,
            new OpenGuiPayload(pageEntries, lp1, lp2, debugMode, p, PAGE_SIZE, pages));
    }

    public void reload(MinecraftServer server) {
        this.internal.reload(server);
    }

    public ZoneCheckResult check(ServerPlayer player, BlockPos pos) {
        return this.internal.check(player, pos);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<IZone> zones(Level level) {
        return (Collection<IZone>)(Collection<?>) this.internal.zones(level);
    }

    @Override
    public Optional<IZone> get(Level level, String name) {
        return this.internal.get(level, name).map(IZone.class::cast);
    }

    public boolean add(Level level, ProtectedZone zone) { return this.internal.add(level, zone); }

    public boolean remove(Level level, String name) { return this.internal.remove(level, name); }

    public boolean whitelistAdd(Level level, String name, UUID playerId, String playerName) {
        return this.internal.whitelistAdd(level, name, playerId, playerName);
    }

    public boolean whitelistRemove(Level level, String name, UUID playerId, String playerName) {
        return this.internal.whitelistRemove(level, name, playerId, playerName);
    }

    /** Assigns a role to a player in the named zone (also whitelists them). */
    public boolean setMemberRole(Level level, String zoneName, UUID playerId, ZoneRole role) {
        return this.internal.setMemberRole(level, zoneName, playerId, role);
    }

    @Override
    public Optional<IZone> findZoneContaining(Level level, BlockPos pos) {
        return this.internal.findContaining(level, pos).map(IZone.class::cast);
    }

    /** Single-pass equivalent of {@code check()} + {@code findZoneContaining()} for flag-aware checks. */
    public Optional<ProtectedZone> checkZone(ServerPlayer player, BlockPos pos) {
        return this.internal.checkZone(player, pos);
    }

    public boolean setEnabled(Level level, String zoneName, boolean enabled) {
        return this.internal.setEnabled(level, zoneName, enabled);
    }

    public boolean setInheritDimFlags(Level level, String zoneName, boolean inherit) {
        return this.internal.setInheritDimFlags(level, zoneName, inherit);
    }

    public boolean setFlag(Level level, String zoneName, String flagId, Object value) {
        return this.internal.setFlag(level, zoneName, flagId, value);
    }

    public boolean resetFlag(Level level, String zoneName, String flagId) {
        return this.internal.resetFlag(level, zoneName, flagId);
    }

    public boolean setParent(Level level, String zoneName, @Nullable String parentName) {
        return this.internal.setParent(level, zoneName, parentName);
    }

    public boolean setBounds(Level level, String zoneName, BlockPos a, BlockPos b) {
        return this.internal.setBounds(level, zoneName, a, b);
    }

    // ── ZoneLifecycleEvent subscribers ───────────────────────────────────────────

    @SubscribeEvent
    public void onCreateZone(ZoneLifecycleEvent.CreateZone event) {
        ProtectedZone zone = new ProtectedZone(
            event.name(), event.dimension(), event.pos1(), event.pos2());
        event.setSuccess(this.internal.add(event.level(), zone));
    }

    @SubscribeEvent
    public void onDeleteZone(ZoneLifecycleEvent.DeleteZone event) {
        event.setSuccess(this.internal.remove(event.level(), event.zoneName()));
    }

    @SubscribeEvent
    public void onSetFlag(ZoneLifecycleEvent.SetFlag event) {
        boolean ok = event.isReset()
            ? this.internal.resetFlag(event.level(), event.zoneName(), event.flagId())
            : this.internal.setFlag(event.level(), event.zoneName(), event.flagId(), event.value());
        event.setSuccess(ok);
    }

    @SubscribeEvent
    public void onModifyZone(ZoneLifecycleEvent.ModifyZone event) {
        boolean ok = switch (event.kind()) {
            case WHITELIST_ADD ->
                this.internal.whitelistAdd(event.level(), event.zoneName(),
                    (UUID) event.arg1(), (String) event.arg2());
            case WHITELIST_REMOVE ->
                this.internal.whitelistRemove(event.level(), event.zoneName(),
                    (UUID) event.arg1(), (String) event.arg2());
            case SET_PARENT ->
                this.internal.setParent(event.level(), event.zoneName(),
                    (String) event.arg1());
            case TOGGLE_ENABLED ->
                this.internal.setEnabled(event.level(), event.zoneName(),
                    (Boolean) event.arg1());
            case TOGGLE_INHERIT ->
                this.internal.setInheritDimFlags(event.level(), event.zoneName(),
                    (Boolean) event.arg1());
            case SET_BOUNDS ->
                this.internal.setBounds(event.level(), event.zoneName(),
                    (BlockPos) event.arg1(), (BlockPos) event.arg2());
            case ITEM_BLOCK_CHANGED ->
                this.internal.setItemBlocked(event.level(), event.zoneName(),
                    net.minecraft.resources.ResourceLocation.tryParse((String) event.arg2()),
                    "add".equals(event.arg1()));
        };
        event.setSuccess(ok);
    }
}

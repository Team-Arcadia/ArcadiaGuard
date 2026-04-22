package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickItemHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class SupplementariesHandler implements RightClickItemHandler {

    private static final TagKey<Item> THROWABLE_BRICKS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("supplementaries", "throwable_bricks"));
    private final GuardService guardService;

    public SupplementariesHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void handle(PlayerInteractEvent.RightClickItem event) {
        if (!ArcadiaGuardConfig.ENABLE_SUPPLEMENTARIES.get()) return;
        if (!ModList.get().isLoaded("supplementaries")) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        if (!(entity instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        // S-H17 T5 : couvre les briques (tag throwable_bricks) ET les bombes
        // (supplementaries:bomb, supplementaries:bomb_blue, etc. — pas dans le tag).
        // Match strict : path = "bomb" ou commence par "bomb_" pour eviter les faux
        // positifs (bombard_shell, bomber_jacket si jamais ajoutes).
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean isBomb = false;
        if (itemId != null && "supplementaries".equals(itemId.getNamespace())) {
            String path = itemId.getPath();
            isBomb = path.equals("bomb") || path.startsWith("bomb_");
        }
        if (!stack.is(THROWABLE_BRICKS) && !isBomb) return;
        Object pos = ReflectionHelper.invoke(player, "blockPosition", new Class<?>[0]).orElse(null);
        if (pos instanceof net.minecraft.core.BlockPos blockPos) {
            if (guardService.shouldBypass(player)) return;
            Optional<ProtectedZone> zoneOpt = guardService.zoneManager().checkZone(player, blockPos);
            if (zoneOpt.isPresent() && guardService.isZoneDenying(zoneOpt.get(), BuiltinFlags.SUPPLEMENTARIES_THROW, player.serverLevel())) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(ArcadiaGuardConfig.MESSAGE_SUPPLEMENTARIES.get())
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                guardService.auditDenied(player, zoneOpt.get().name(), blockPos, BuiltinFlags.SUPPLEMENTARIES_THROW, "supplementaries_throw");
                event.setCanceled(true);
            }
        }
    }
}

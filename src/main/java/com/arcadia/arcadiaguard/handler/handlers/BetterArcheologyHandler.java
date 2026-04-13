package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.BlockBreakHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class BetterArcheologyHandler implements BlockBreakHandler {

    private static final String ENCHANTMENT_HELPER_CLASS = "net.minecraft.world.item.enchantment.EnchantmentHelper";
    private static final ResourceKey<Enchantment> TUNNELING = ResourceKey.create(
        Registries.ENCHANTMENT,
        ResourceLocation.fromNamespaceAndPath("betterarcheology", "tunneling")
    );

    private final GuardService guardService;

    public BetterArcheologyHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void handle(BlockEvent.BreakEvent event) {
        if (!ArcadiaGuardConfig.ENABLE_BETTERARCHEOLOGY.get()) return;
        if (!ModList.get().isLoaded("betterarcheology")) return;

        Object playerObj = ReflectionHelper.invoke(event, "getPlayer", new Class<?>[0]).orElse(null);
        if (!(playerObj instanceof ServerPlayer player)) return;
        if (ReflectionHelper.boolMethod(playerObj, "isShiftKeyDown", new Class<?>[0])) return;

        Object posObj = ReflectionHelper.invoke(event, "getPos", new Class<?>[0]).orElse(null);
        if (!(posObj instanceof BlockPos pos)) return;

        Object toolObj = ReflectionHelper.invoke(playerObj, "getMainHandItem", new Class<?>[0]).orElse(ItemStack.EMPTY);
        if (!(toolObj instanceof ItemStack tool) || tool.isEmpty() || !tool.isEnchanted()) return;

        Object levelObj = ReflectionHelper.invoke(playerObj, "serverLevel", new Class<?>[0]).orElse(null);
        if (levelObj == null) return;
        Object registryAccess = ReflectionHelper.invoke(levelObj, "registryAccess", new Class<?>[0]).orElse(null);
        if (registryAccess == null) return;
        Object enchantmentLookup = ReflectionHelper.invoke(registryAccess, "lookupOrThrow", new Class<?>[] { ResourceKey.class }, Registries.ENCHANTMENT).orElse(null);
        if (enchantmentLookup == null) return;
        Object tunneling = ReflectionHelper.invoke(enchantmentLookup, "get", new Class<?>[] { ResourceKey.class }, TUNNELING).orElse(null);
        Object holder = tunneling == null ? null : ReflectionHelper.invoke(tunneling, "orElse", new Class<?>[] { Object.class }, (Object) null).orElse(null);
        if (holder == null) return;

        Object level = ReflectionHelper.invokeStatic(ENCHANTMENT_HELPER_CLASS, "getItemEnchantmentLevel", new Class<?>[] { holder.getClass(), tool.getClass() }, holder, tool).orElse(0);
        if (!(level instanceof Number number) || number.intValue() < 1) return;

        BlockPos below = new BlockPos(
            ReflectionHelper.intMethod(pos, "getX"),
            ReflectionHelper.intMethod(pos, "getY") - 1,
            ReflectionHelper.intMethod(pos, "getZ")
        );
        if (this.guardService.blockIfProtected(
            player,
            below,
            "betterarcheology:tunneling",
            "betterarcheology",
            ArcadiaGuardConfig.MESSAGE_BETTERARCHEOLOGY.get()
        ).blocked()) {
            event.setCanceled(true);
        }
    }
}

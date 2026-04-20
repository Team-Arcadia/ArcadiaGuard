package com.arcadia.arcadiaguard.item;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    private static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, ArcadiaGuard.MOD_ID);

    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArcadiaGuard.MOD_ID);

    public static final DeferredHolder<Item, WandItem> ZONE_EDITOR =
        ITEMS.register("zone_editor", () -> new WandItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
        TABS.register("arcadiaguard", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcadiaguard"))
            .icon(() -> ZONE_EDITOR.get().getDefaultInstance())
            .displayItems((params, output) -> output.accept(ZONE_EDITOR.get()))
            .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
        ModDataComponents.register(modBus);
    }

    private ModItems() {}
}

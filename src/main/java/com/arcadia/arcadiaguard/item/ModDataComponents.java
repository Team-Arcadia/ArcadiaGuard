package com.arcadia.arcadiaguard.item;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {

    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ArcadiaGuard.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandMode>> WAND_MODE =
        DATA_COMPONENTS.register("wand_mode", () -> DataComponentType.<WandMode>builder()
            .persistent(WandMode.CODEC)
            .networkSynchronized(WandMode.STREAM_CODEC)
            .build());

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
    }

    private ModDataComponents() {}
}

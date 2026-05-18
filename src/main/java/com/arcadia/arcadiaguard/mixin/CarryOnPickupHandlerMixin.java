package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.helper.CarryOnCompatHelper;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PickupHandler", remap = false)
public abstract class CarryOnPickupHandlerMixin {

    @Inject(
        method = "tryPickupEntity(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ljava/util/function/Function;)Z",
        at = @At(
            value = "INVOKE",
            target = "Ltschipp/carryon/common/carry/CarryOnDataManager;getCarryData(Lnet/minecraft/world/entity/player/Player;)Ltschipp/carryon/common/carry/CarryOnData;"
        ),
        cancellable = true,
        remap = false
    )
    private static void arcadiaguard$blockEntityPickup(ServerPlayer player, Entity entity,
            Function<Entity, Boolean> pickupCallback, CallbackInfoReturnable<Boolean> cir) {
        if (CarryOnCompatHelper.shouldBlockEntityPickup(player, entity)) {
            cir.setReturnValue(false);
        }
    }
}

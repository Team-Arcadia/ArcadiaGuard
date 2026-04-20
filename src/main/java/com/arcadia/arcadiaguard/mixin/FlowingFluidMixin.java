package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.helper.FlagMixinHelper;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.level.material.FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Inject(
        method = "spreadTo(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void arcadiaguard$blockFluidSpread(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluidState, CallbackInfo ci) {
        if (!FlagMixinHelper.hasAnyZoneInDim(level)) return;
        if (((Object) this) instanceof LavaFluid) {
            if (FlagMixinHelper.isDenied(level, pos, BuiltinFlags.LAVA_SPREAD)) ci.cancel();
        } else if (((Object) this) instanceof WaterFluid) {
            if (FlagMixinHelper.isDenied(level, pos, BuiltinFlags.WATER_SPREAD)) ci.cancel();
        }
    }
}

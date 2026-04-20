package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.helper.FlagMixinHelper;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.level.block.BasePressurePlateBlock.class)
public abstract class BasePressurePlateBlockMixin {

    @Inject(
        method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void arcadiaguard$blockPressurePlate(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (!FlagMixinHelper.hasAnyZoneInDim(level)) return;
        if (FlagMixinHelper.isDenied(level, pos, BuiltinFlags.PRESSURE_PLATE)) ci.cancel();
    }
}

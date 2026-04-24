package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.helper.FlagMixinHelper;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.level.block.SnowLayerBlock.class)
public abstract class SnowLayerBlockMixin {

    @Inject(
        method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void arcadiaguard$blockSnowMelt(BlockState state, ServerLevel level, BlockPos pos, RandomSource rand, CallbackInfo ci) {
        if (!FlagMixinHelper.hasAnyRuleInDim(level)) return;
        if (FlagMixinHelper.isDenied(level, pos, BuiltinFlags.SNOW_MELT)) ci.cancel();
    }
}

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

@Mixin(net.minecraft.world.level.block.LeavesBlock.class)
public abstract class LeavesBlockMixin {

    @Inject(
        method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void arcadiaguard$blockLeafDecay(BlockState state, ServerLevel level, BlockPos pos, RandomSource rand, CallbackInfo ci) {
        // H2: leaves that are persistent or not fully decayed won't decay anyway — skip zone check
        if (state.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
                || state.getValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE) < 7) return;
        if (!FlagMixinHelper.hasAnyZoneInDim(level)) return;
        if (FlagMixinHelper.isDenied(level, pos, BuiltinFlags.LEAF_DECAY)) ci.cancel();
    }
}

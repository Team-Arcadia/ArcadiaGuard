package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.helper.FlagMixinHelper;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.level.block.SculkSpreader.class)
public abstract class SculkSpreaderMixin {

    @Inject(
        method = "updateCursors(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;Z)V",
        at = @At("HEAD"), cancellable = true
    )
    private void arcadiaguard$blockSculkSpread(LevelAccessor level, BlockPos pos, RandomSource rand, boolean p_222259_, CallbackInfo ci) {
        if (!FlagMixinHelper.hasAnyRuleInDim(level)) return;
        if (FlagMixinHelper.isDenied(level, pos, BuiltinFlags.SCULK_SPREAD)) ci.cancel();
    }
}

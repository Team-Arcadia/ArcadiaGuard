package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Containers.class)
public abstract class ContainersMixin {

    @Inject(
        method = "dropContentsOnDestroy(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void arcadiaguard$blockProtectedContainerDrops(BlockState state, BlockState newState,
            Level level, BlockPos pos, CallbackInfo ci) {
        if (state.is(newState.getBlock())) return;
        if (!FlagMixinHelper.mayDeny(level, BuiltinFlags.BLOCK_BREAK)) return;
        if (!FlagMixinHelper.isDenied(level, pos, BuiltinFlags.BLOCK_BREAK)) return;
        if (FlagMixinHelper.consumeContainerDropAllowance(level, pos)) return;
        ci.cancel();
    }
}

package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S-H16 T2 : les pressure plates SecurityCraft ({@code ReinforcedPressurePlateBlock})
 * etendent {@code OwnableBlock}/{@code DisguisableBlock}, pas {@code BasePressurePlateBlock}
 * vanilla → le mixin existant {@code BasePressurePlateBlockMixin} ne les intercepte pas.
 *
 * Ce mixin pseudo cible leur methode {@code entityInside} pour annuler
 * l'activation quand le flag {@code PRESSURE_PLATE} est denied.
 */
@Pseudo
@Mixin(targets = "net.geforcemods.securitycraft.blocks.reinforced.ReinforcedPressurePlateBlock", remap = false)
public abstract class ReinforcedPressurePlateBlockMixin {

    @Inject(
        method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"), cancellable = true, remap = false
    )
    private void arcadiaguard$blockPressurePlate(BlockState state, Level level, BlockPos pos,
            Entity entity, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (!FlagMixinHelper.hasAnyZoneInDim(level)) return;
        if (FlagMixinHelper.isDenied(level, pos, BuiltinFlags.PRESSURE_PLATE)) {
            ci.cancel();
        }
    }
}

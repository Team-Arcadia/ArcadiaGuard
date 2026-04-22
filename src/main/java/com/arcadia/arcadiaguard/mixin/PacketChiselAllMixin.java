package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S-H17 T3 : packet "apply design" de l'UI Rechiseled. Ce packet opere sur
 * {@code player.containerMenu} sans BlockPos precis — on utilise la position
 * du joueur pour checker {@code RECHISELED_USE}.
 */
@Pseudo
@Mixin(targets = "com.supermartijn642.rechiseled.packet.PacketChiselAll", remap = false)
public abstract class PacketChiselAllMixin {

    private static boolean WARNED = false;

    @Inject(
        method = "handle(Lcom/supermartijn642/core/network/PacketContext;)V",
        at = @At("HEAD"), cancellable = true, remap = false
    )
    private void arcadiaguard$blockChiselAll(Object context, CallbackInfo ci) {
        try {
            Object player = context.getClass().getMethod("getPlayer").invoke(context);
            if (!(player instanceof Player mcPlayer)) return;
            if (!(mcPlayer.level() instanceof net.minecraft.world.level.Level level)) return;
            if (level.isClientSide()) return;
            if (FlagMixinHelper.isDenied(level, mcPlayer.blockPosition(), BuiltinFlags.RECHISELED_USE)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            if (!WARNED) {
                WARNED = true;
                com.arcadia.arcadiaguard.ArcadiaGuard.LOGGER.warn(
                    "[ArcadiaGuard] RECHISELED_USE mixin sur PacketChiselAll inoperant: {}", t.toString());
            }
        }
    }
}

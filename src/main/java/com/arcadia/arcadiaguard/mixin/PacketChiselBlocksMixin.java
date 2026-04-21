package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S-H17 T3 : Rechiseled n'emet aucun event NeoForge quand le packet C2S
 * arrive au serveur pour appliquer un chisel. On intercepte directement
 * {@code PacketChiselBlocks#handle(PacketContext)} pour canceller si la
 * zone a {@code RECHISELED_USE} denied.
 *
 * Utilise {@code @Pseudo} + {@code targets} pour eviter la dep compile-time
 * sur la lib SuperMartijn642.
 */
@Pseudo
@Mixin(targets = "com.supermartijn642.rechiseled.packet.PacketChiselBlocks", remap = false)
public abstract class PacketChiselBlocksMixin {

    /** Rate-limiter : log l'echec une seule fois par session serveur. */
    private static boolean WARNED = false;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadiaguard$blockChisel(Object context, CallbackInfo ci) {
        try {
            Object player = context.getClass().getMethod("getPlayer").invoke(context);
            if (!(player instanceof Player mcPlayer)) return;
            if (!(mcPlayer.level() instanceof net.minecraft.world.level.Level level)) return;
            if (level.isClientSide()) return;
            // Recuperer la position via reflection sur le field 'pos' du packet
            java.lang.reflect.Field posField = this.getClass().getDeclaredField("pos");
            posField.setAccessible(true);
            Object posObj = posField.get(this);
            if (!(posObj instanceof BlockPos pos)) return;
            if (FlagMixinHelper.isDenied(level, pos, BuiltinFlags.RECHISELED_USE)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            if (!WARNED) {
                WARNED = true;
                com.arcadia.arcadiaguard.ArcadiaGuard.LOGGER.warn(
                    "[ArcadiaGuard] RECHISELED_USE mixin sur PacketChiselBlocks inoperant (structure changee ?): {}",
                    t.toString());
            }
        }
    }
}

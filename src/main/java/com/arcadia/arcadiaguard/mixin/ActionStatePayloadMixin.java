package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S-H17 T2 : ParCool traite ses actions cote serveur via la methode
 * static {@code ActionStatePayload.handleServer(ActionStatePayload, IPayloadContext)}.
 * On intercepte en HEAD pour annuler l'execution quand le flag
 * {@code PARCOOL_ACTIONS} est denied dans la zone du joueur.
 */
@Pseudo
@Mixin(targets = "com.alrex.parcool.common.network.payload.ActionStatePayload", remap = false)
public abstract class ActionStatePayloadMixin {

    /** Throttle par UUID pour afficher le message max 1x/2s. */
    private static final java.util.Map<java.util.UUID, Long> LAST_MSG =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean WARNED = false;

    @Inject(
        method = "handleServer(Lcom/alrex/parcool/common/network/payload/ActionStatePayload;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void arcadiaguard$blockParCoolAction(Object payload, IPayloadContext context, CallbackInfo ci) {
        try {
            if (context == null) return;
            Player player = context.player();
            if (!(player instanceof ServerPlayer sp)) return;
            var level = sp.level();
            if (level == null || level.isClientSide()) return;
            if (!FlagMixinHelper.hasAnyZoneInDim(level)) return;
            if (FlagMixinHelper.isDenied(level, sp.blockPosition(), BuiltinFlags.PARCOOL_ACTIONS)) {
                ci.cancel();
                long now = System.currentTimeMillis();
                Long last = LAST_MSG.get(sp.getUUID());
                if (last == null || now - last > 2000L) {
                    LAST_MSG.put(sp.getUUID(), now);
                    sp.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("arcadiaguard.message.parcool_actions")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                }
            }
        } catch (Throwable t) {
            if (!WARNED) {
                WARNED = true;
                com.arcadia.arcadiaguard.ArcadiaGuard.LOGGER.warn(
                    "[ArcadiaGuard] PARCOOL_ACTIONS mixin inoperant: {}", t.toString());
            }
        }
    }
}

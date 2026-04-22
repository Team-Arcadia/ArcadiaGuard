package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.client.ClientParcoolState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S-H21 (v1.5) : mixin CLIENT-SIDE sur le tick ParCool.
 * Cancel tous les ticks d'action quand {@link ClientParcoolState#isBlocked()}
 * est true (push par le serveur via ParcoolBlockedPayload).
 */
@Pseudo
@Mixin(targets = "com.alrex.parcool.common.action.ActionProcessor", remap = false)
public abstract class ActionProcessorMixin {

    private static boolean WARNED = false;

    @Inject(
        method = "onTick(Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Post;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void arcadiaguard$maybeCancelParcoolTick(Object event, CallbackInfo ci) {
        try {
            if (ClientParcoolState.isBlocked()) {
                ci.cancel();
            }
        } catch (Throwable t) {
            if (!WARNED) {
                WARNED = true;
                com.arcadia.arcadiaguard.ArcadiaGuard.LOGGER.warn(
                    "[ArcadiaGuard] PARCOOL_ACTIONS client mixin inoperant: {}", t.toString());
            }
        }
    }
}

package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S-H17 T2 : ParCool declenche ses actions cote serveur via
 * {@code ActionStatePayload.processPlayer(ServerPlayer)} qui appelle
 * directement {@code action.start(...)} sans emettre d'event cancellable
 * (l'event {@code ParCoolActionEvent.TryToStart} est poste cote client
 * uniquement).
 *
 * On intercepte en HEAD pour annuler l'execution des actions cote serveur
 * quand le flag {@code PARCOOL_ACTIONS} est denied.
 */
@Pseudo
@Mixin(targets = "com.alrex.parcool.common.network.payload.ActionStatePayload", remap = false)
public abstract class ActionStatePayloadMixin {

    /** Throttle par UUID pour afficher le message ArcadiaGuard max 1x/2s. */
    private static final java.util.Map<java.util.UUID, Long> LAST_MSG =
        new java.util.concurrent.ConcurrentHashMap<>();

    @Inject(method = "processPlayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadiaguard$blockParCoolAction(ServerPlayer player, CallbackInfo ci) {
        if (player == null) return;
        var level = player.level();
        if (level == null || level.isClientSide()) return;
        if (!FlagMixinHelper.hasAnyZoneInDim(level)) return;
        if (FlagMixinHelper.isDenied(level, player.blockPosition(), BuiltinFlags.PARCOOL_ACTIONS)) {
            ci.cancel();
            // AC2 S-H17 : afficher un message actionbar ArcadiaGuard pour informer
            // le joueur que ses actions ParCool sont bloquees. Le HUD ParCool natif
            // (F6 keybind) reste cote client ; une solution plus propre passerait
            // par LimitationPayload S2C mais depasse le scope de ce mixin.
            long now = System.currentTimeMillis();
            Long last = LAST_MSG.get(player.getUUID());
            if (last == null || now - last > 2000L) {
                LAST_MSG.put(player.getUUID(), now);
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("arcadiaguard.message.parcool_actions")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
        }
    }
}

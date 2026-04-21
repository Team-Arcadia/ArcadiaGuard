package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S-H16 T6 : le magnet upgrade des Sophisticated Backpacks / Storage collecte
 * les {@link ItemEntity} via appel direct a {@code tryToInsertItem} sans passer
 * par {@code ItemEntityPickupEvent}. On intercepte cette methode pour bloquer
 * la collecte quand le flag {@code ITEM_PICKUP} est denied a la position de l'item.
 *
 * Cible la classe partagee {@code SophisticatedCore} (dep de Backpacks ET Storage).
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.upgrades.magnet.MagnetUpgradeWrapper", remap = false)
public abstract class MagnetUpgradeWrapperMixin {

    @Inject(
        method = "tryToInsertItem",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void arcadiaguard$blockMagnetPickup(Player player, ItemEntity itemEntity,
            CallbackInfoReturnable<Boolean> cir) {
        if (itemEntity == null) return;
        Level level = itemEntity.level();
        if (level == null || level.isClientSide()) return;
        if (!FlagMixinHelper.hasAnyZoneInDim(level)) return;
        if (FlagMixinHelper.isDenied(level, itemEntity.blockPosition(), BuiltinFlags.ITEM_PICKUP)) {
            cir.setReturnValue(false);
        }
    }
}

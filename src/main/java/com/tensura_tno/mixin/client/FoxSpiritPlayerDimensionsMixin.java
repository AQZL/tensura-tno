package com.tensura_tno.mixin.client;

import com.tensura_tno.race.fox_spirit.FoxSpiritPlayerFormHelper;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives fox-spirit players four-legged collision boxes and matching eye heights. */
@Mixin(Player.class)
public abstract class FoxSpiritPlayerDimensionsMixin {

    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void tno$foxSpiritDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Player player = (Player) (Object) this;
        if (!FoxSpiritPlayerFormHelper.shouldUseFoxForm(player)) return;

        EntityDimensions original = cir.getReturnValue();
        EntityDimensions replacement;
        if (pose == Pose.STANDING) {
            replacement = resize(original, 0.70F, 1.00F, 0.80F);
        } else if (pose == Pose.CROUCHING) {
            replacement = resize(original, 0.70F, 1.00F, 0.70F);
        } else if (pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK) {
            replacement = resize(original, 0.70F, 1.00F, 0.45F);
        } else {
            replacement = original;
        }
        cir.setReturnValue(replacement);
    }

    private static EntityDimensions resize(EntityDimensions original,
                                           float width, float height, float eyeHeight) {
        float widthScale = width / original.width();
        float heightScale = height / original.height();
        return original.scale(widthScale, heightScale).withEyeHeight(eyeHeight);
    }
}

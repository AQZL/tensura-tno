package com.tensura_tno.mixin.client;

import com.tensura_tno.compat.moreskills.MoreSkillsHardcodedLocalization;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Localizes only legacy literal components created by TensuraMoreSkills. */
@Mixin(Component.class)
public interface MoreSkillsLiteralLocalizationMixin {

    @Inject(method = "literal", at = @At("HEAD"), cancellable = true)
    private static void tensura_tno$localizeMoreSkillsLiteral(
            String text, CallbackInfoReturnable<MutableComponent> cir) {
        if (!MoreSkillsHardcodedLocalization.hasCandidate(text)
                || !MoreSkillsHardcodedLocalization.isCalledFromMoreSkills()) {
            return;
        }

        MutableComponent replacement = MoreSkillsHardcodedLocalization.createComponent(text);
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}


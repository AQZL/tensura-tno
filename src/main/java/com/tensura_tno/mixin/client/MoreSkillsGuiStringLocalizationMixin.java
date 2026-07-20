package com.tensura_tno.mixin.client;

import com.tensura_tno.client.localization.MoreSkillsHardcodedClientLocalization;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Covers legacy MoreSkills screens which pass raw String values to GuiGraphics. */
@Mixin(GuiGraphics.class)
public class MoreSkillsGuiStringLocalizationMixin {

    @ModifyVariable(
            method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private String tensura_tno$localizeMoreSkillsDrawString(String original) {
        return MoreSkillsHardcodedClientLocalization.localize(original);
    }

    @ModifyVariable(
            method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private String tensura_tno$localizeMoreSkillsCenteredString(String original) {
        return MoreSkillsHardcodedClientLocalization.localize(original);
    }
}


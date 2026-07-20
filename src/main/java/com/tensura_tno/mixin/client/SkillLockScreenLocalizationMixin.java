package com.tensura_tno.mixin.client;

import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "org.crypticdev.stextras.client.screen.STSkillLockScreen", remap = false)
public class SkillLockScreenLocalizationMixin {

    private static final String KEY_PREFIX = "tensura_tno.skill_lock.";

    @ModifyConstant(method = "renderLockButton", constant = @Constant(stringValue = "Lock"), remap = false)
    private String localizeLockButton(String original) {
        return tr("button.lock", original);
    }

    @ModifyConstant(method = "renderUnlockButton", constant = @Constant(stringValue = "Unlock"), remap = false)
    private String localizeUnlockButton(String original) {
        return tr("button.unlock", original);
    }

    private static String tr(String suffix, String fallback) {
        Language language = Language.getInstance();
        String key = KEY_PREFIX + suffix;
        return language.has(key) ? language.getOrDefault(key) : fallback;
    }
}
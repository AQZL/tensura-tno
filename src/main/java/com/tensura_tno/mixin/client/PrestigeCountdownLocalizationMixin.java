package com.tensura_tno.mixin.client;

import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "org.crypticdev.stextras.client.screen.prestige.STPrestigeProgress", remap = false)
public class PrestigeCountdownLocalizationMixin {

    private static final String COUNTDOWN_KEY_PREFIX = "tensura_tno.prestige.countdown.";

    @Inject(method = "formatCountdown", at = @At("HEAD"), remap = false, cancellable = true)
    private static void localizeCountdown(long seconds, CallbackInfoReturnable<String> cir) {
        if (seconds <= 0L) {
            cir.setReturnValue("0" + tr("seconds", "s"));
            return;
        }

        long days = seconds / 86400L;
        if (days > 0L) {
            cir.setReturnValue(days + tr("days", "D"));
            return;
        }

        long hours = seconds % 86400L / 3600L;
        if (hours > 0L) {
            cir.setReturnValue(hours + tr("hours", "h"));
            return;
        }

        long mins = seconds % 3600L / 60L;
        if (mins > 0L) {
            cir.setReturnValue(mins + tr("minutes", "m"));
            return;
        }

        cir.setReturnValue(seconds % 60L + tr("seconds", "s"));
    }

    private static String tr(String suffix, String fallback) {
        Language language = Language.getInstance();
        String key = COUNTDOWN_KEY_PREFIX + suffix;
        return language.has(key) ? language.getOrDefault(key) : fallback;
    }
}
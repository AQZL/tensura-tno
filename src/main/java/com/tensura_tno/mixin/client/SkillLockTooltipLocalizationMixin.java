package com.tensura_tno.mixin.client;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "org.crypticdev.stextras.client.screen.skilllock.STSkillLockTooltips", remap = false)
public class SkillLockTooltipLocalizationMixin {

    private static final String KEY_PREFIX = "tensura_tno.skill_lock.";
    private static final String REQUIREMENTS = "Requirements";
    private static final String SOUL_GRADE_COST = "Soul Grade Cost: ";
    private static final String MP_COST = "MP Cost: ";
    private static final String EP_NEEDED = "EP Needed: ";

    @Inject(method = "unlockTooltip", at = @At("RETURN"), cancellable = true, remap = false)
    private static void localizeUnlockTooltip(CallbackInfoReturnable<List<Component>> cir) {
        List<Component> tooltip = cir.getReturnValue();
        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }

        List<Component> localized = new ArrayList<>(tooltip.size());
        for (Component component : tooltip) {
            localized.add(localizeComponent(component));
        }
        cir.setReturnValue(localized);
    }

    private static Component localizeComponent(Component component) {
        String text = component.getString();
        if (REQUIREMENTS.equals(text)) {
            return copyLiteral(component, tr("tooltip.requirements", REQUIREMENTS));
        }
        if (text.startsWith(SOUL_GRADE_COST)) {
            return copyLiteral(component, tr("tooltip.soul_grade_cost", SOUL_GRADE_COST) + text.substring(SOUL_GRADE_COST.length()));
        }
        if (text.startsWith(MP_COST)) {
            return copyLiteral(component, tr("tooltip.mp_cost", MP_COST) + text.substring(MP_COST.length()));
        }
        if (text.startsWith(EP_NEEDED)) {
            return copyLiteral(component, tr("tooltip.ep_needed", EP_NEEDED) + text.substring(EP_NEEDED.length()));
        }
        return component;
    }

    private static MutableComponent copyLiteral(Component original, String text) {
        MutableComponent replacement = Component.literal(text).setStyle(original.getStyle());
        for (Component sibling : original.getSiblings()) {
            replacement.append(sibling.copy());
        }
        return replacement;
    }

    private static String tr(String suffix, String fallback) {
        Language language = Language.getInstance();
        String key = KEY_PREFIX + suffix;
        return language.has(key) ? language.getOrDefault(key) : fallback;
    }
}
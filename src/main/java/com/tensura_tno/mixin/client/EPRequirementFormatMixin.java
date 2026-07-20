package com.tensura_tno.mixin.client;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 将进化条件中的 EP 数值格式化为 k/M/G 单位，避免显示 100000.0 这样的长数字。
 * 例：100000.0 → 100k，1500000.0 → 1.5M
 */
@Mixin(targets = "io.github.manasmods.tensura.race.template.EvolutionRequirement$EPRequirement", remap = false)
public class EPRequirementFormatMixin {

    @Shadow
    public double getRequirement() {
        throw new AssertionError();
    }

    @Inject(
        method = "getRequirementComponent",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void tensuraTno$formatEPNumber(CallbackInfoReturnable<Component> cir) {
        String formatted = tensuraTno$formatEP(getRequirement());
        cir.setReturnValue(Component.translatable("tensura.evolution_menu.ep_requirement", formatted));
    }

    private static String tensuraTno$formatEP(double value) {
        if (value >= 1_000_000_000.0) {
            double v = value / 1_000_000_000.0;
            return tensuraTno$stripTrailingZero(v) + "G";
        } else if (value >= 1_000_000.0) {
            double v = value / 1_000_000.0;
            return tensuraTno$stripTrailingZero(v) + "M";
        } else if (value >= 1_000.0) {
            double v = value / 1_000.0;
            return tensuraTno$stripTrailingZero(v) + "k";
        } else {
            long lv = (long) value;
            return lv == value ? String.valueOf(lv) : String.valueOf(value);
        }
    }

    private static String tensuraTno$stripTrailingZero(double v) {
        long lv = (long) v;
        if (lv == v) {
            return String.valueOf(lv);
        }
        // 保留一位小数
        return String.format("%.1f", v);
    }
}

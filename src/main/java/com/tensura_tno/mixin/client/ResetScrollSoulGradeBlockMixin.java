package com.tensura_tno.mixin.client;

import com.tensura_tno.util.PrestigeGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Only character_reset_scroll (RESET_ALL) should grant soul grade.
 * skill_reset_scroll (RESET_SKILL) and race_reset_scroll (RESET_RACE) should NOT.
 *
 * STExtras' ResetScrollItemMixin hooks isFullReset() to call doPrestige().
 * We block doPrestige when the current scroll is NOT a character reset scroll.
 *
 * Detection: check if the ResetScrollItem instance's resetType == RESET_ALL.
 * Since isFullReset is static, we set the flag in releaseUsing (instance method)
 * which runs before isFullReset is called.
 */
@Mixin(targets = "io.github.manasmods.tensura.item.misc.ResetScrollItem", priority = 500)
public class ResetScrollSoulGradeBlockMixin {

    @Inject(method = "releaseUsing", at = @At("HEAD"), remap = true, require = 0)
    private void tno$markScrollType(ItemStack stack, net.minecraft.world.level.Level level,
                                     net.minecraft.world.entity.LivingEntity entity, int timeLeft,
                                     org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        try {
            Field resetTypeField = this.getClass().getDeclaredField("resetType");
            resetTypeField.setAccessible(true);
            Object resetType = resetTypeField.get(this);
            boolean isCharacterReset = "RESET_ALL".equals(((Enum<?>) resetType).name());
            PrestigeGuard.BLOCKED.set(!isCharacterReset);
        } catch (Exception e) {
            PrestigeGuard.BLOCKED.set(true);
        }
    }

    @Inject(method = "releaseUsing", at = @At("RETURN"), remap = true, require = 0)
    private void tno$clearScrollType(ItemStack stack, net.minecraft.world.level.Level level,
                                      net.minecraft.world.entity.LivingEntity entity, int timeLeft,
                                      org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        PrestigeGuard.BLOCKED.set(false);
    }
}

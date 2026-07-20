package com.tensura_tno.mixin.client;

import com.tensura_tno.race.fox_spirit.SpiritSummonEntityHelper;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Named Spirit Summons may feed skills into Unyielding, but never EP.
 */
@Mixin(targets = "io.github.manasmods.tensura.ability.skill.unique.UnyieldingSkill", remap = false)
public abstract class UnyieldingSpiritSummonNoEPMixin {
    @Redirect(
            method = "onSubordinateDeath",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(DD)D"),
            remap = false,
            require = 2
    )
    private double tno$removeSpiritSummonEPReward(double first, double second,
                                                   ManasSkillInstance instance, LivingEntity owner,
                                                   LivingEntity subordinate, DamageSource source) {
        return SpiritSummonEntityHelper.isSpiritSummon(subordinate) ? 0.0D : Math.min(first, second);
    }
}

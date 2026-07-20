package com.tensura_tno.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "io.github.manasmods.tensura.ability.skill.extra.HakiSkill", remap = false)
public abstract class HakiSkillLegacyCompatMixin {
    public static void hakiPush(LivingEntity target, Entity source, int fearLevel) {
        if (fearLevel < 1 || source == null) {
            return;
        }

        double knockResist = 1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double multiplier = Math.min(0.04D * fearLevel, 0.2D) * knockResist;
        Vec3 push = target.getEyePosition().subtract(source.getEyePosition()).normalize().scale(multiplier);
        target.push(push.x(), push.y(), push.z());
    }
}
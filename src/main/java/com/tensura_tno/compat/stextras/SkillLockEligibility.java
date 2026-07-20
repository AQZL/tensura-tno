package com.tensura_tno.compat.stextras;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;

public final class SkillLockEligibility {
    private SkillLockEligibility() {
    }

    public static boolean isResistanceSkill(ResourceLocation skillId) {
        ManasSkill skill = getRegisteredSkill(skillId);
        return skill instanceof Skill tensuraSkill
                && tensuraSkill.getType() == Skill.SkillType.RESISTANCE;
    }

    public static boolean isUltimateSkill(ResourceLocation skillId) {
        ManasSkill skill = getRegisteredSkill(skillId);
        return skill instanceof Skill tensuraSkill
                && tensuraSkill.getType() == Skill.SkillType.ULTIMATE;
    }

    private static ManasSkill getRegisteredSkill(ResourceLocation skillId) {
        if (skillId == null) return null;
        try {
            return SkillAPI.getSkillRegistry().get(skillId);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

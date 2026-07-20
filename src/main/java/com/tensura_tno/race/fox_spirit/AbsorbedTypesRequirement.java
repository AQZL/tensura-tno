package com.tensura_tno.race.fox_spirit;

import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.tensura.race.template.EvolutionRequirement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 进化条件：收纳口袋中需要拥有指定数量的不同实体种类。
 * <p>
 * 收纳数量由 {@link com.tensura_tno.ability.skill.SpiritSummonSkill} 在收纳时
 * 写入 {@code ManasRaceInstance.getOrCreateTag().putInt("absorbedTypesCount", count)}，
 * 该 tag 会同步到客户端，进化界面可正常显示进度。
 */
public class AbsorbedTypesRequirement extends EvolutionRequirement {

    private static final String TAG_ABSORBED_COUNT = "absorbedTypesCount";

    private final int requirement;

    public AbsorbedTypesRequirement(int requirement) {
        this.requirement = requirement;
    }

    @Override
    public float getProgress(ManasRaceInstance instance, LivingEntity entity) {
        if (!(entity instanceof Player)) return 0.0F;
        int count = instance.getOrCreateTag().getInt(TAG_ABSORBED_COUNT);
        return (float) count / (float) requirement;
    }

    @Override
    public Component getRequirementComponent(ManasRaceInstance instance, LivingEntity entity) {
        return Component.translatable("tensura_tno.evolution_menu.absorbed_types_requirement", requirement);
    }

    public int getRequirement() {
        return requirement;
    }
}

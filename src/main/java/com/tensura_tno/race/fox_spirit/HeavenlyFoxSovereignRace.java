package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.config.race.FoxSpiritRaceConfig;
import com.tensura_tno.registry.TensuraTNOSkills;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.config.race.RaceConfig;
import io.github.manasmods.tensura.race.template.DefaultRace;
import io.github.manasmods.tensura.race.template.EvolutionRequirement;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天灵狐尊 — 狐灵种族线的终极种族。
 * <p>
 * 种族线的顶点，无后续进化。
 */
public class HeavenlyFoxSovereignRace extends DefaultRace {

    private static final FoxSpiritRaceConfig.HeavenlyFoxSovereign CONFIG =
            new FoxSpiritRaceConfig.HeavenlyFoxSovereign();

    public HeavenlyFoxSovereignRace() {
        super(Difficulty.HARD);
        this.applyDefaultAttributeModifiers();
    }

    @Override
    public RaceConfig.Default getDefaultConfig() {
        return CONFIG;
    }

    @Override
    public List<ManasSkill> getIntrinsicSkills(ManasRaceInstance instance, LivingEntity entity) {
        List<ManasSkill> list = new ArrayList<>();
        list.add((ManasSkill) ExtraSkills.MAGIC_SENSE.get());
        list.add((ManasSkill) TensuraTNOSkills.CONTRACT_LITTLE_FOX.get());
        list.add((ManasSkill) TensuraTNOSkills.SPIRIT_SUMMON.get());
        list.add((ManasSkill) TensuraTNOSkills.SPIRIT_ENHANCEMENT.get());
        return list;
    }

    // 终极种族 — 无后续进化
    // getNextEvolutions / getDefaultEvolution / getAwakeningEvolution /
    // getHarvestFestivalEvolution 均返回空/null（DefaultRace 默认行为）

    @Override
    public Map<EvolutionRequirement, Float> getEvolutionRequirements(ManasRaceInstance previous, LivingEntity entity) {
        // 进化到天灵狐尊：成为魔王/勇者 + 灵之召唤中某个召唤物EP达到12万
        Map<EvolutionRequirement, Float> map = new LinkedHashMap<>();
        map.put(new EvolutionRequirement.AwakenRequirement(), 100.0F);
        map.put(new SummonMaxEPRequirement(120000), 100.0F);
        return map;
    }
}

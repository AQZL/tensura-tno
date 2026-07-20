package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.config.race.FoxSpiritRaceConfig;
import com.tensura_tno.registry.TensuraTNORaces;
import com.tensura_tno.registry.TensuraTNOSkills;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.config.race.RaceConfig;
import io.github.manasmods.tensura.race.template.DefaultRace;
import io.github.manasmods.tensura.race.template.EvolutionRequirement;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 狐灵使 — 狐灵种族线的第二阶。
 * <p>
 * 获得契约灵狐能力，可以召唤小狐灵协助战斗。
 * 进化条件：契约灵狐击杀 50 只敌对/中立生物。
 */
public class FoxSpiritEnvoyRace extends DefaultRace {

    private static final FoxSpiritRaceConfig.FoxSpiritEnvoy CONFIG = new FoxSpiritRaceConfig.FoxSpiritEnvoy();

    public FoxSpiritEnvoyRace() {
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

    @Override
    public List<ManasRace> getNextEvolutions(ManasRaceInstance instance, LivingEntity entity) {
        List<ManasRace> list = new ArrayList<>();
        list.add((ManasRace) TensuraTNORaces.SPIRIT_FOX_CONTRACT_MASTER.get());
        return list;
    }

    @Nullable
    @Override
    public ManasRace getDefaultEvolution(ManasRaceInstance instance, LivingEntity entity) {
        return (ManasRace) TensuraTNORaces.SPIRIT_FOX_CONTRACT_MASTER.get();
    }

    @Nullable
    @Override
    public ManasRace getAwakeningEvolution(ManasRaceInstance instance, LivingEntity entity) {
        return null;
    }

    @Nullable
    @Override
    public ManasRace getHarvestFestivalEvolution(ManasRaceInstance instance, LivingEntity entity) {
        return null;
    }

    @Override
    public Map<EvolutionRequirement, Float> getEvolutionRequirements(ManasRaceInstance previous, LivingEntity entity) {
        // 进化到狐灵使：需要 5000 EP
        return Map.of(new EvolutionRequirement.EPRequirement(5000.0), 100.0F);
    }
}

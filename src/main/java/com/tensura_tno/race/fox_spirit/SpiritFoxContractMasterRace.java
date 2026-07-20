package com.tensura_tno.race.fox_spirit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.tensura_tno.config.race.FoxSpiritRaceConfig;
import com.tensura_tno.registry.TensuraTNORaces;
import com.tensura_tno.registry.TensuraTNOSkills;

import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.config.race.RaceConfig;
import io.github.manasmods.tensura.race.template.DefaultRace;
import io.github.manasmods.tensura.race.template.EvolutionRequirement;
import io.github.manasmods.tensura.registry.magic.SummoningMagics;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import net.minecraft.world.entity.LivingEntity;

/**
 * 灵狐契主 — 狐灵种族线的第三阶。
 * <p>
 * 与灵狐的契约更加深厚，体力与精神力大幅提升。
 * 进化条件（占位符）：10000 EP。
 */
public class SpiritFoxContractMasterRace extends DefaultRace {

    private static final FoxSpiritRaceConfig.SpiritFoxContractMaster CONFIG =
            new FoxSpiritRaceConfig.SpiritFoxContractMaster();

    public SpiritFoxContractMasterRace() {
        super(Difficulty.INTERMEDIATE);
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
        // 灵狐契主进化奖励：召唤魔犬、召唤蛇怪、召唤中位精灵
        list.add((ManasSkill) SummoningMagics.SUMMON_HOUND_DOG.get());
        list.add((ManasSkill) SummoningMagics.SUMMON_BASILISK.get());
        list.add((ManasSkill) SummoningMagics.SUMMON_MEDIUM_ELEMENTAL.get());
        return list;
    }

    @Override
    public List<ManasRace> getNextEvolutions(ManasRaceInstance instance, LivingEntity entity) {
        List<ManasRace> list = new ArrayList<>();
        list.add((ManasRace) TensuraTNORaces.MYSTIC_FOX_MASTER.get());
        return list;
    }

    @Nullable
    @Override
    public ManasRace getDefaultEvolution(ManasRaceInstance instance, LivingEntity entity) {
        return (ManasRace) TensuraTNORaces.MYSTIC_FOX_MASTER.get();
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
        // 进化到灵狐契主：契约灵狐击杀 50 只敌对/中立生物
        return Map.of(new FoxKillRequirement(50), 100.0F);
    }
}

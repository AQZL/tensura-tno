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
import java.util.LinkedHashMap;

/**
 * 玄灵狐主 — 狐灵种族线的第四阶（占位符）。
 * <p>
 * 进化条件（占位符）：10000 EP。
 */
public class MysticFoxMasterRace extends DefaultRace {

    private static final FoxSpiritRaceConfig.MysticFoxMaster CONFIG =
            new FoxSpiritRaceConfig.MysticFoxMaster();

    public MysticFoxMasterRace() {
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
        // 灵狐契主阶段召唤魔法
        list.add((ManasSkill) SummoningMagics.SUMMON_HOUND_DOG.get());
        list.add((ManasSkill) SummoningMagics.SUMMON_BASILISK.get());
        list.add((ManasSkill) SummoningMagics.SUMMON_MEDIUM_ELEMENTAL.get());
        // 玄灵狐主进化奖励：召唤上位精灵、召唤恶魔
        list.add((ManasSkill) SummoningMagics.SUMMON_GREATER_ELEMENTAL.get());
        list.add((ManasSkill) SummoningMagics.SUMMON_DAEMON.get());
        return list;
    }

    @Override
    public List<ManasRace> getNextEvolutions(ManasRaceInstance instance, LivingEntity entity) {
        List<ManasRace> list = new ArrayList<>();
        list.add((ManasRace) TensuraTNORaces.HEAVENLY_FOX_SOVEREIGN.get());
        return list;
    }

    @Nullable
    @Override
    public ManasRace getDefaultEvolution(ManasRaceInstance instance, LivingEntity entity) {
        return (ManasRace) TensuraTNORaces.HEAVENLY_FOX_SOVEREIGN.get();
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
        // 进化到玄灵狐主：100000 EP + 灵之召唤收纳 30 种实体
        Map<EvolutionRequirement, Float> map = new LinkedHashMap<>();
        map.put(new EvolutionRequirement.EPRequirement(100000.0), 50.0F);
        map.put(new AbsorbedTypesRequirement(30), 50.0F);
        return map;
    }
}

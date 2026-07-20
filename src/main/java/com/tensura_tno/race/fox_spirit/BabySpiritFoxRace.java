package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.config.race.FoxSpiritRaceConfig;
import com.tensura_tno.registry.TensuraTNORaces;
import com.tensura_tno.registry.TensuraTNOSkills;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.config.race.RaceConfig;
import io.github.manasmods.tensura.race.template.DefaultRace;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 幼灵狐 — 狐灵种族线的初始种族。
 * <p>
 * 极其脆弱（10 HP），拥有基础的魔法感知和契约灵狐能力。
 * 选择界面描述：「来到大大异世想保住小小的命，可惜它太弱了」
 */
public class BabySpiritFoxRace extends DefaultRace {

    private static final FoxSpiritRaceConfig.BabySpiritFox CONFIG = new FoxSpiritRaceConfig.BabySpiritFox();

    public BabySpiritFoxRace() {
        super(Difficulty.EXTREME);
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
        return list;
    }

    @Override
    public List<ManasRace> getNextEvolutions(ManasRaceInstance instance, LivingEntity entity) {
        List<ManasRace> list = new ArrayList<>();
        list.add((ManasRace) TensuraTNORaces.FOX_SPIRIT_ENVOY.get());
        return list;
    }

    @Nullable
    @Override
    public ManasRace getDefaultEvolution(ManasRaceInstance instance, LivingEntity entity) {
        return (ManasRace) TensuraTNORaces.FOX_SPIRIT_ENVOY.get();
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

    // 起始种族 — 无 getEvolutionRequirements（进化 INTO 条件在目标种族上定义）
}

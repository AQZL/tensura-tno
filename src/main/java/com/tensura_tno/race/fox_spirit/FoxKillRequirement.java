package com.tensura_tno.race.fox_spirit;

import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.tensura.race.template.EvolutionRequirement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 召唤物击杀需求 — 玩家的召唤物需累计击杀指定数量的敌对/中立生物。
 * <p>
 * 击杀计数存储在玩家的 {@link ManasRaceInstance#getOrCreateTag()} 中，
 * 键名为 {@code summonKills}，由 {@link FoxKillEventHandler} 在召唤物击杀实体时递增。
 */
public class FoxKillRequirement extends EvolutionRequirement {

    private static final String TAG_SUMMON_KILLS = "summonKills";

    private final int requiredKills;

    public FoxKillRequirement(int requiredKills) {
        this.requiredKills = requiredKills;
    }

    @Override
    public float getProgress(ManasRaceInstance instance, LivingEntity entity) {
        if (!(entity instanceof Player)) return 0.0F;
        int kills = getSummonKills(instance);
        return Math.min(1.0F, (float) kills / (float) this.requiredKills);
    }

    /**
     * 从种族实例 tag 中读取召唤物累计击杀数。
     */
    public static int getSummonKills(ManasRaceInstance instance) {
        if (instance == null) return 0;
        CompoundTag tag = instance.getOrCreateTag();
        return tag.getInt(TAG_SUMMON_KILLS);
    }

    /**
     * 向种族实例 tag 中写入召唤物累计击杀数（递增 1）。
     *
     * @return 递增后的击杀数
     */
    public static int incrementSummonKills(ManasRaceInstance instance) {
        if (instance == null) return 0;
        CompoundTag tag = instance.getOrCreateTag();
        int kills = tag.getInt(TAG_SUMMON_KILLS) + 1;
        tag.putInt(TAG_SUMMON_KILLS, kills);
        instance.markDirty();
        return kills;
    }

    @Override
    public Component getRequirementComponent(ManasRaceInstance instance, LivingEntity entity) {
        return Component.translatable("tensura_tno.evolution_menu.summon_kill_requirement", this.requiredKills);
    }

    public int getRequiredKills() {
        return this.requiredKills;
    }
}

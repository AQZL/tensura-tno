package com.tensura_tno.race.fox_spirit;

import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.tensura.race.template.EvolutionRequirement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 进化条件：灵之召唤口袋中，至少有一个召唤物的累计 EP 达到指定值。
 * <p>
 * 最大召唤物 EP 由 {@link SpiritSummonEPBackflowEventHandler} 在 EP 回流时
 * 写入 {@code ManasRaceInstance.getOrCreateTag().putDouble("maxSummonBonusEP", max)}，
 * 该 tag 会同步到客户端，进化界面可正常显示进度。
 */
public class SummonMaxEPRequirement extends EvolutionRequirement {

    private static final String TAG_MAX_SUMMON_BONUS_EP = "maxSummonBonusEP";

    private final double requiredEP;

    public SummonMaxEPRequirement(double requiredEP) {
        this.requiredEP = requiredEP;
    }

    @Override
    public float getProgress(ManasRaceInstance instance, LivingEntity entity) {
        if (!(entity instanceof Player)) return 0.0F;
        double maxEP = instance.getOrCreateTag().getDouble(TAG_MAX_SUMMON_BONUS_EP);
        return (float) Math.min(1.0, maxEP / requiredEP);
    }

    @Override
    public Component getRequirementComponent(ManasRaceInstance instance, LivingEntity entity) {
        return Component.translatable("tensura_tno.evolution_menu.summon_max_ep_requirement", formatEP(requiredEP));
    }

    private static String formatEP(double value) {
        if (value >= 1_000_000_000.0) return stripTrailing(value / 1_000_000_000.0) + "G";
        if (value >= 1_000_000.0)     return stripTrailing(value / 1_000_000.0) + "M";
        if (value >= 1_000.0)         return stripTrailing(value / 1_000.0) + "k";
        return String.valueOf((long) value);
    }

    private static String stripTrailing(double v) {
        long lv = (long) v;
        return lv == v ? String.valueOf(lv) : String.format("%.1f", v);
    }

    /**
     * 更新种族实例 tag 中记录的最大召唤物累计 EP。
     * 当任何口袋中实体的 bonus_ep 增长时调用。
     */
    public static void updateMaxSummonBonusEP(ManasRaceInstance instance, double currentMax) {
        CompoundTag tag = instance.getOrCreateTag();
        double recorded = tag.getDouble(TAG_MAX_SUMMON_BONUS_EP);
        if (currentMax > recorded) {
            tag.putDouble(TAG_MAX_SUMMON_BONUS_EP, currentMax);
            instance.markDirty();
        }
    }

    public double getRequiredEP() {
        return requiredEP;
    }
}

package com.tensura_tno.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tensura_tno.ability.skill.SpiritSummonSkill;
import com.tensura_tno.race.fox_spirit.FoxSpiritSummonBonus;

import io.github.manasmods.tensura.data.TensuraTags;
import io.github.manasmods.tensura.storage.ep.ExistenceStorage;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.world.TensuraGameRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 狐灵种族死亡EP惩罚分摊：
 * 原版死亡时减少玩家全部EP的 penalty% ，狐灵种族改为：
 * - 玩家自身EP减少 penalty/2 %
 * - 收纳口袋中召唤物的bonusEP总共减少等量的EP
 */
@Mixin(value = ExistenceStorage.class, remap = false)
public class FoxSpiritDeathPenaltyMixin {

    @Inject(
            method = "applyDeathPenalty",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void tensuraTno$splitDeathPenalty(LivingEntity entity, IExistence storage, DamageSource source, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (!FoxSpiritSummonBonus.isFoxSpiritRace(player)) return;

        // 与原版相同的前置检查
        if (source.is(TensuraTags.DamageTypes.STOP_DEATH_PENALTY)) { ci.cancel(); return; }
        if (source.isCreativePlayer()) { ci.cancel(); return; }

        // 获取惩罚比例
        float penaltyRate = (float) entity.level().getGameRules().getInt(TensuraGameRules.EP_DEATH_PENALTY) / 100.0F;
        if (penaltyRate <= 0.0F) {
            ci.cancel();
            return;
        }

        // 计算总EP损失量
        double baseMaxEP = EnergyHelper.getBaseMaxEP(entity);
        double totalLoss = baseMaxEP * penaltyRate;
        double halfLoss = totalLoss / 2.0;

        // 玩家自身EP减少一半（即乘以 1 - penaltyRate/2）
        float halfPenaltyRate = penaltyRate / 2.0F;
        EnergyHelper.multiplyMaxEP(entity, (double)(1.0F - halfPenaltyRate));

        // 灵魂点也减少一半
        if (storage.getSoulPoints() > 0) {
            storage.setSoulPoints((int)((float) storage.getSoulPoints() * (1.0F - halfPenaltyRate)));
        }

        // 收纳口袋中的bonusEP减少另一半，按各实体比例分摊
        List<CompoundTag> entries = SpiritSummonSkill.SpiritSummonPockets.getAbsorbedEntries(player);
        if (!entries.isEmpty()) {
            // 计算所有bonusEP总和
            double totalBonusEP = 0.0;
            for (CompoundTag ct : entries) {
                totalBonusEP += ct.getDouble("bonus_ep");
            }

            if (totalBonusEP > 0.0) {
                // 实际扣除量不超过bonusEP总和
                double actualPocketLoss = Math.min(halfLoss, totalBonusEP);
                double ratio = actualPocketLoss / totalBonusEP;

                // 按比例扣减每个实体的bonusEP
                for (CompoundTag ct : entries) {
                    double currentBonus = ct.getDouble("bonus_ep");
                    if (currentBonus > 0.0) {
                        double loss = currentBonus * ratio;
                        SpiritSummonSkill.SpiritSummonPockets.addBonusEP(
                                player, ct.getString("id"), -loss);
                    }
                }
            }
            // 如果bonusEP总和为0，则召唤物那一半自然损失为0（不额外扣玩家EP）
        }

        ci.cancel();
    }
}

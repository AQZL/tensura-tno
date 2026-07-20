package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.ability.skill.SpiritSummonSkill;
import com.tensura_tno.entity.spirit.FoxSpiritEntity;
import com.tensura_tno.registry.TensuraTNOSkills;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.world.TensuraGameRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Optional;

import java.util.UUID;

/**
 * 灵之召唤 EP 回流事件处理器。
 * <p>
 * 当灵之召唤的临时召唤物击杀敌人时，100% EP 全部给到召唤物自身：
 * <ul>
 *   <li>立即通过 {@link EnergyHelper.GainType#MAX} 增加召唤物的 MAX_AURA / MAX_MAGICULE</li>
 *   <li>同时按 EP 增长比例等比增加 MAX_HEALTH</li>
 *   <li>持久化存入口袋 bonus_ep，下次召唤时自动注入</li>
 * </ul>
 * <p>
 * 注意：FoxSpiritEntity 的 EP 分配由 {@link com.tensura_tno.event.ContractLittleFoxEvents} 独立处理，
 * 本处理器会明确排除 FoxSpiritEntity，避免冲突。
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID)
public class SpiritSummonEPBackflowEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        // 击杀者必须是 Mob
        Entity killerEntity = event.getSource().getEntity();
        if (!(killerEntity instanceof Mob killerMob)) return;

        // 排除 FoxSpiritEntity（它有自己的 EP 分配机制）
        if (killerMob instanceof FoxSpiritEntity) return;

        // 检查击杀者是否为灵之召唤的召唤物
        ServerPlayer owner = findSpiritSummonOwner(killerMob);
        if (owner == null) return;

        // 执行 EP 回流
        tryEPBackflow(killerMob, victim, owner);
    }

    /**
     * 查找灵之召唤召唤物的拥有者。
     * 条件：IExistence.summoner != null 且 summonedAbility.skill == SPIRIT_SUMMON
     */
    private static ServerPlayer findSpiritSummonOwner(Mob mob) {
        try {
            IExistence ex = TensuraStorages.getExistenceFrom(mob);
            UUID summonerUUID = ex.getSummoner();
            if (summonerUUID == null) return null;

            // 检查是否为灵之召唤的召唤物
            var summonedAbility = ex.getSummonedAbility();
            if (summonedAbility == null) return null;
            if (summonedAbility.getSkill() != TensuraTNOSkills.SPIRIT_SUMMON.get()) return null;

            // 获取拥有者玩家
            if (mob.level() instanceof ServerLevel sl) {
                Entity owner = sl.getEntity(summonerUUID);
                if (owner instanceof ServerPlayer player) return player;
            }
        } catch (Exception ignored) {
            // 非 Tensura 实体
        }
        return null;
    }

    /**
     * 执行 EP 回流分配。
     * 100% EP 全部给到召唤物自身：立即增加 MAX EP 和 MAX HP，同时持久化到口袋。
     */
    private static void tryEPBackflow(Mob summon, LivingEntity victim, ServerPlayer owner) {
        // 与 Tensura 默认行为一致的过滤
        if (victim.getType().is(TensuraEntityTags.EP_DROP_EXCLUDED)) return;
        try {
            if (TensuraStorages.getExistenceFrom(victim).isSkippingEPDrop()) return;
        } catch (Exception ignored) {
            return;
        }

        // 以召唤物作为击杀者计算 EP 获取量
        double epGain = EnergyHelper.getEPGain(victim, summon, true);
        if (epGain <= 0) return;

        int multiplier = victim.level().getGameRules().getInt(TensuraGameRules.EP_GAIN_MULTIPLIER);
        double full = epGain * multiplier;

        // 阻止 Tensura 默认 EP 分配
        try {
            TensuraStorages.getExistenceFrom(victim).setSkippingEPDrop(true);
        } catch (Exception ignored) {}

        // ① 立即增加召唤物的 MAX EP（GainType.MAX 同时增加基础属性和当前值）
        double baseEPBefore = EnergyHelper.getBaseMaxEP(summon);
        double halfEP = full / 2.0;
        EnergyHelper.gainAura(summon, halfEP, EnergyHelper.GainType.MAX);
        EnergyHelper.gainMagicule(summon, halfEP, EnergyHelper.GainType.MAX);

        // ② EP 增长 → HP 等比增长
        if (baseEPBefore > 0.0) {
            AttributeInstance hpAttr = summon.getAttribute(Attributes.MAX_HEALTH);
            if (hpAttr != null) {
                double hpBoost = hpAttr.getBaseValue() * (full / baseEPBefore);
                hpAttr.setBaseValue(hpAttr.getBaseValue() + hpBoost);
                summon.setHealth(summon.getMaxHealth());
            }
        }

        // ③ 持久化存入口袋 bonus_ep（下次召唤时自动注入）
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(summon.getType());
        if (entityId != null) {
            SpiritSummonSkill.SpiritSummonPockets.addBonusEP(owner, entityId.toString(), full);
            // 同步最大 bonus EP 到种族 tag，用于天灵狐尊进化条件
            syncMaxBonusEPToRaceTag(owner, entityId.toString());
        }
    }

    /**
     * 将口袋中指定实体的 bonus EP 同步到种族实例 tag。
     * 用于 {@link SummonMaxEPRequirement} 进化条件在客户端显示进度。
     */
    private static void syncMaxBonusEPToRaceTag(ServerPlayer owner, String entityId) {
        double currentEP = SpiritSummonSkill.SpiritSummonPockets.getBonusEP(owner, entityId);
        Optional<ManasRaceInstance> raceOpt = RaceAPI.getRaceFrom(owner).getRace();
        raceOpt.ifPresent(instance -> SummonMaxEPRequirement.updateMaxSummonBonusEP(instance, currentEP));
    }
}

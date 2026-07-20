package com.tensura_tno.race.fox_spirit;

import java.util.Optional;
import java.util.UUID;

import com.tensura_tno.compat.stextras.STExtrasKillCredit;

import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 召唤物击杀事件处理器 — 当玩家的召唤物击杀敌对/中立生物时，
 * 将计数写入主人的种族实例 tag，用于 {@link FoxKillRequirement} 进化判定。
 */
@EventBusSubscriber(modid = "tensura_tno")
public class FoxKillEventHandler {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 只在服务端处理
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        // 排除玩家死亡（不计数）
        if (victim instanceof Player) return;

        // 检查击杀者是否为 LivingEntity（召唤物）
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return;

        // 只对敌对/中立生物计数（排除被动动物，如牛、羊等）
        if (!(victim instanceof Mob)) return;
        if (!isHostileOrNeutral((Mob) victim)) return;

        // 检查击杀者是否为某玩家的召唤物（通过 IExistence.getSummoner）
        IExistence killerExistence = TensuraStorages.getExistenceFrom(killer);
        UUID ownerUUID = killerExistence.getSummoner();
        if (ownerUUID == null) return;

        Player owner = victim.level().getPlayerByUUID(ownerUUID);
        if (owner == null) return;

        // 获取主人的种族实例，递增击杀计数（进化用）
        Optional<ManasRaceInstance> raceOpt = RaceAPI.getRaceFrom(owner).getRace();
        raceOpt.ifPresent(FoxKillRequirement::incrementSummonKills);

        // 将击杀计入主人的 STExtras 任务进度（种族声望"召唤物击杀Boss"：仅计入最大生命值 ≥100 的 Boss 级生物）
        if (owner instanceof ServerPlayer sp && victim.getMaxHealth() >= 100.0F) {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
            STExtrasKillCredit.creditKill(sp, entityId, victim);
        }
    }

    /**
     * 判断一个 Mob 是否为敌对或中立生物。
     * <p>
     * 敌对生物实现 {@link Enemy} 接口；中立生物默认不主动攻击但被攻击后会反击。
     * 完全被动的动物（牛、羊、猪、鸡等）不计数。
     */
    private static boolean isHostileOrNeutral(Mob mob) {
        // 敌对生物（僵尸、骷髅、苦力怕等）
        if (mob instanceof Enemy) return true;
        // 中立生物：非 Enemy 但被攻击后有反击行为（末影人、僵尸猪灵等）
        // 简化判断：排除确定是被动的动物类型
        return !isPassiveAnimal(mob);
    }

    /**
     * 判断一个 Mob 是否为完全被动的动物（如牛、羊、猪、鸡等）。
     * <p>
     * 判定标准：既不是 {@link Enemy}，当前也没有攻击目标。
     */
    private static boolean isPassiveAnimal(Mob mob) {
        return !(mob instanceof Enemy)
                && mob.getTarget() == null;
    }
}

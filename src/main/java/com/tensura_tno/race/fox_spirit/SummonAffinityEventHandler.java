package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.TensuraTNOMod;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.UUID;

/**
 * 召唤物亲和事件处理器 — 防止狐灵种族玩家的召唤物被外部控制。
 * <p>
 * 覆盖所有召唤物（不仅是 FoxSpiritEntity），包括 Edo Tensei 召唤、
 * Tensura 召唤魔法（Elemental、Hound Dog、Basilisk、Daemon）等。
 * <p>
 * 保护机制：
 * <ul>
 *   <li>阻止精神控制效果（MindControl）作用于召唤物</li>
 *   <li>定期清除已存在的精神控制效果（兜底）</li>
 * </ul>
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID)
public class SummonAffinityEventHandler {

    /** 每隔多少 tick 扫描一次召唤物身上的非法效果（兜底检查，1 秒一次） */
    private static final int SCAN_INTERVAL = 20;

    @SubscribeEvent
    public static void onMobEffectApply(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof Player) return;

        // 检查是否为狐灵种族玩家的召唤物
        if (!isFoxSpiritSummon(entity)) return;

        // 阻挡精神控制效果
        MobEffectInstance effectInstance = event.getEffectInstance();
        if (effectInstance != null && effectInstance.getEffect().equals(TensuraMobEffects.MIND_CONTROL)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof LivingEntity living)) return;
        if (living instanceof Player) return;

        // 限制扫描频率
        if (living.tickCount % SCAN_INTERVAL != 0) return;

        // 检查是否为狐灵种族玩家的召唤物
        if (!isFoxSpiritSummon(living)) return;

        // 兜底：移除已存在的精神控制效果
        Holder<MobEffect> mindControl = TensuraMobEffects.MIND_CONTROL;
        if (living.hasEffect(mindControl)) {
            living.removeEffect(mindControl);
        }
    }

    /**
     * 判断一个实体是否为狐灵种族玩家的召唤物。
     */
    private static boolean isFoxSpiritSummon(LivingEntity entity) {
        Player owner = findOwner(entity);
        if (owner == null) return false;
        return FoxSpiritSummonBonus.isFoxSpiritRace(owner);
    }

    /**
     * 查找召唤物的主人。
     */
    private static Player findOwner(LivingEntity entity) {
        // 方式1：Tensura IExistence 召唤者
        try {
            IExistence existence = TensuraStorages.getExistenceFrom(entity);
            UUID summonerUUID = existence.getSummoner();
            if (summonerUUID != null && entity.level() instanceof ServerLevel sl) {
                Entity owner = sl.getEntity(summonerUUID);
                if (owner instanceof Player player) return player;
            }
        } catch (Exception ignored) {
        }

        // 方式2：OwnableEntity
        if (entity instanceof net.minecraft.world.entity.OwnableEntity ownable) {
            UUID ownerUUID = ownable.getOwnerUUID();
            if (ownerUUID != null && entity.level() instanceof ServerLevel sl) {
                Entity owner = sl.getEntity(ownerUUID);
                if (owner instanceof Player player) return player;
            }
        }

        return null;
    }
}

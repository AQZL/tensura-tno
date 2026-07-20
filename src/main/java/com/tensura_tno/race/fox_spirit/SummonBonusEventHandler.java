package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.entity.spirit.FoxSpiritEntity;
import com.tensura_tno.TensuraTNOMod;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.UUID;

/**
 * 召唤物数值加成事件处理器。
 * <p>
 * 当任何实体加入世界时，检测其是否为狐灵种族玩家的召唤物，
 * 若是则根据玩家进化阶段给予 HP / 攻击力百分比加成。
 * <p>
 * 识别召唤物的方式：
 * <ul>
 *   <li>通过 Tensura 的 {@link IExistence#getSummoner()} 检测（覆盖 Tensura 召唤魔法、Edo Tensei 等）</li>
 *   <li>通过 {@link net.minecraft.world.entity.OwnableEntity#getOwnerUUID()} 检测（覆盖契约灵狐等）</li>
 * </ul>
 * <p>
 * 加成使用 {@link AttributeModifier} 以 ADD_SCALAR 模式叠加，
 * 不影响实体的 base value，便于与其他加成共存。
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID)
public class SummonBonusEventHandler {

    /** 加成 Modifier 的 ResourceLocation，避免与其他模组冲突 */
    private static final ResourceLocation HP_BONUS_ID = ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "fox_spirit_summon_hp_bonus");
    private static final ResourceLocation ATK_BONUS_ID = ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "fox_spirit_summon_atk_bonus");

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity summon)) return;
        // 排除玩家自身
        if (summon instanceof Player) return;
        // FoxSpiritEntity 的 HP/ATK bonus 由自身的 syncAttackFromSummoner /
        // syncMaxStatsFromSummoner 每秒直接写入 base value，不需要外加修饰符
        if (summon instanceof FoxSpiritEntity) return;

        // 查找召唤物主人
        Player owner = findOwner(summon);
        if (owner == null) return;

        // 获取加成比例
        float bonus = FoxSpiritSummonBonus.getBonusPercentage(owner);
        if (bonus <= 0.0F) return;

        // 应用 HP 加成
        applyHealthBonus(summon, bonus);

        // 应用攻击力加成
        applyAttackBonus(summon, bonus);
    }

    /**
     * 查找实体的主人（召唤者）。
     * 优先检查 IExistence（Tensura 从属系统），其次检查 OwnableEntity。
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
            // IExistence 可能不适用于所有实体
        }

        // 方式2：OwnableEntity（契约灵狐等）
        if (entity instanceof net.minecraft.world.entity.OwnableEntity ownable) {
            UUID ownerUUID = ownable.getOwnerUUID();
            if (ownerUUID != null && entity.level() instanceof ServerLevel sl) {
                Entity owner = sl.getEntity(ownerUUID);
                if (owner instanceof Player player) return player;
            }
        }

        return null;
    }

    /**
     * 应用 HP 加成（ADD_SCALAR 模式：base × (1 + bonus)）。
     */
    private static void applyHealthBonus(LivingEntity summon, float bonus) {
        AttributeInstance hpAttr = summon.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr == null) return;

        // 移除旧加成（避免重复叠加）
        hpAttr.removeModifier(HP_BONUS_ID);

        AttributeModifier modifier = new AttributeModifier(
                HP_BONUS_ID,
                bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );
        hpAttr.addPermanentModifier(modifier);

        // 同步当前生命值（如果满血则拉满）
        if (summon.getHealth() >= summon.getMaxHealth() - 0.5F) {
            summon.setHealth(summon.getMaxHealth());
        }
    }

    /**
     * 应用攻击力加成（ADD_SCALAR 模式：base × (1 + bonus)）。
     */
    private static void applyAttackBonus(LivingEntity summon, float bonus) {
        AttributeInstance atkAttr = summon.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atkAttr == null) return;

        atkAttr.removeModifier(ATK_BONUS_ID);

        AttributeModifier modifier = new AttributeModifier(
                ATK_BONUS_ID,
                bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );
        atkAttr.addPermanentModifier(modifier);
    }
}

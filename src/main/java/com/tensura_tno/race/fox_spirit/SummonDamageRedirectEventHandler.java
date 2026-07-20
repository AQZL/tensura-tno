package com.tensura_tno.race.fox_spirit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.entity.spirit.FoxSpiritEntity;
import com.tensura_tno.network.ContractLittleFoxPackets;
import com.tensura_tno.registry.TensuraTNOSkills;

import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 召唤物替承伤事件处理器。
 * <p>
 * 当灵狐契主及以上阶段的狐灵种族玩家受到伤害时，按以下优先级转移伤害：
 * <ol>
 *   <li>临时召唤物（灵之召唤的召唤物）— 多个时随机选一个承受全部伤害</li>
 *   <li>契约小狐灵</li>
 *   <li>玩家自己（无召唤物可转移时）</li>
 * </ol>
 * <p>
 * 不转移的伤害类型：{@link DamageTypeTags#BYPASSES_INVULNERABILITY}（虚空、/kill 等）。
 * <p>
 * <b>重入保护：</b>{@link #REDIRECTING} 防止与 Tensura {@code GuardianSkill}
 * （从属→玩家）形成无限递归循环。当玩家同时拥有 Guardian 切换为 ON 时：
 * <pre>
 *   原始伤害 → 玩家 → 我们重定向给召唤物
 *               → Guardian 拦截 → 重定向回玩家
 *                 → 我们的处理器（REDIRECTING=true）→ 跳过 → 玩家吃下伤害（打破循环）
 * </pre>
 * 这一守卫修复了两个反馈 Bug：<br>
 *  · 玩家与从属在 Guardian + 替承伤同开时互相抵消导致都不掉血（实际是无限递归 → StackOverflowError）<br>
 *  · 持续伤害（如火焰操作）同时打到玩家和召唤物时游戏崩溃。
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID)
public class SummonDamageRedirectEventHandler {

    /** 替承伤解锁的最低进化等级：灵狐契主 = 2 */
    private static final int MIN_EVOLUTION_LEVEL = 2;

    /**
     * 重入守卫。事件总线在服务端主线程上派发，使用 ThreadLocal 足以隔离一次伤害链。
     * 当为 {@code true} 时，表示当前事件是我们正在执行的重定向链路被 Guardian 等
     * 其它"从属→主人"重定向机制弹回所触发；此时不再二次重定向，由玩家吃下伤害以终止循环。
     */
    private static final ThreadLocal<Boolean> REDIRECTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerHurt(LivingDamageEvent.Pre event) {
        // 仅服务端
        if (event.getEntity().level().isClientSide()) return;

        // 仅玩家
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 重入：当前事件是 Guardian 等机制反弹回来的，让玩家吃下伤害终止循环。
        if (Boolean.TRUE.equals(REDIRECTING.get())) return;

        // 检查进化等级 >= 灵狐契主
        if (FoxSpiritSummonBonus.getEvolutionLevel(player) < MIN_EVOLUTION_LEVEL) return;

        float damage = event.getContainer().getNewDamage();
        if (damage <= 0) return;

        // 不转移虚空/kill等绕过无敌的伤害
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        // 优先级1：临时召唤物（灵之召唤）
        List<Mob> tempSummons = findActiveTempSummons(player);
        if (!tempSummons.isEmpty()) {
            // 多个临时召唤物时，随机选一个承受全部伤害
            Mob target = tempSummons.get(player.getRandom().nextInt(tempSummons.size()));
            event.getContainer().setNewDamage(0);
            redirectTo(target, event.getSource(), damage);
            return;
        }

        // 优先级2：契约小狐灵
        FoxSpiritEntity fox = ContractLittleFoxPackets.findActiveFox(player);
        if (fox != null) {
            event.getContainer().setNewDamage(0);
            redirectTo(fox, event.getSource(), damage);
            return;
        }

        // 优先级3：无召唤物可转移，玩家自己承受（不修改伤害）
    }

    /** 转移伤害到目标实体，并设置重入标记以防止 Guardian 等机制造成的无限递归。 */
    private static void redirectTo(net.minecraft.world.entity.LivingEntity target,
                                    net.minecraft.world.damagesource.DamageSource source,
                                    float damage) {
        REDIRECTING.set(Boolean.TRUE);
        try {
            target.hurt(source, damage);
        } finally {
            REDIRECTING.set(Boolean.FALSE);
        }
    }

    /**
     * 查找玩家当前所有存活的灵之召唤临时召唤物。
     * 条件：IExistence.summoner == playerUUID 且 summonedAbility.skill == SPIRIT_SUMMON。
     */
    private static List<Mob> findActiveTempSummons(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        List<Mob> result = new ArrayList<>();
        for (Entity entity : ((ServerLevel) player.level()).getAllEntities()) {
            if (!(entity instanceof Mob mob)) continue;
            if (!mob.isAlive()) continue;
            try {
                IExistence ex = TensuraStorages.getExistenceFrom(mob);
                if (playerUUID.equals(ex.getSummoner())
                        && ex.getSummonedAbility() != null
                        && ex.getSummonedAbility().getSkill() == TensuraTNOSkills.SPIRIT_SUMMON.get()) {
                    result.add(mob);
                }
            } catch (Exception ignored) {
                // 非 Tensura 实体
            }
        }
        return result;
    }
}

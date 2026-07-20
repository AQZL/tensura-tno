package com.tensura_tno.event;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.ability.skill.SpiritSummonSkill;
import com.tensura_tno.entity.spirit.FoxSpiritEntity;
import com.tensura_tno.network.ContractLittleFoxPackets;
import com.tensura_tno.registry.TensuraTNOSkills;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.handler.DeathHandler;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.world.TensuraGameRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 与"契约小狐"技能相关的事件钩子。
 *
 * <p>负责两件事：
 * <ol>
 *   <li><b>EP 分配</b>：当被攻击者由 {@link FoxSpiritEntity} 杀死时，把本应给狐灵的 EP 拆成
 *       50% / 50% —— 一半给玩家、一半给狐灵自身。通过把死者的 {@code isSkippingEPDrop}
 *       置 true 阻止 Tensura 默认的 100% 给狐灵，再手动调用 {@link DeathHandler#gainEPEntity}
 *       两次完成分配。</li>
 *   <li><b>清理</b>：玩家死亡 / 狐灵死亡时清空玩家 NBT 中的契约状态，确保下次召唤是"重置过的"。</li>
 * </ol>
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID)
public final class ContractLittleFoxEvents {

    private ContractLittleFoxEvents() {}

    /**
     * 注册 Architectury 事件监听器。由 {@link TensuraTNOMod} 初始化时调用。
     * <p>监听 {@link SkillEvents#REMOVE_SKILL}，在玩家使用重置卷轴（种族重置 / 人物重置）
     * 导致技能被移除时，同步清理对应状态。
     */
    public static void registerArchitecturyEvents() {
        SkillEvents.REMOVE_SKILL.register((skillInstance, owner, forgetMessage) -> {
            if (!(owner instanceof ServerPlayer player)) return EventResult.pass();
            if (skillInstance == null || skillInstance.getSkill() == null) return EventResult.pass();
            // 契约小狐灵重置
            if (skillInstance.getSkill() == TensuraTNOSkills.CONTRACT_LITTLE_FOX.get()) {
                ContractLittleFoxPackets.onSkillReset(player);
            }
            // 灵之召唤重置：清空口袋数据 + 移除所有活跃召唤物
            if (skillInstance.getSkill() == TensuraTNOSkills.SPIRIT_SUMMON.get()) {
                SpiritSummonSkill.SpiritSummonPockets.clearAll(player);
                SpiritSummonSkill.removeAllActiveSummons(player);
            }
            return EventResult.pass();
        });
    }

    /** HIGHEST 优先级——必须在 Tensura 的 EP 死亡处理（Architectury 的 DEATH_EVENT_FIRST）之前生效。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        // ① 玩家死亡——立即解除契约
        if (victim instanceof ServerPlayer player) {
            ContractLittleFoxPackets.onPlayerDeath(player);
            return;
        }

        // ② 小狐灵自身死亡——清空 NBT，下次召唤按"50% 重置"
        if (victim instanceof FoxSpiritEntity fox) {
            ContractLittleFoxPackets.onFoxSpiritDeath(fox);
            return;
        }

        // ③ 被某个生物杀死，且攻击者是契约小狐灵 —— 50/50 分 EP
        if (event.getSource().getEntity() instanceof FoxSpiritEntity killerFox) {
            tryFiftyFiftyEPSplit(killerFox, victim);
        }
    }

    private static void tryFiftyFiftyEPSplit(FoxSpiritEntity fox, LivingEntity victim) {
        // 与 Tensura 默认行为一致的过滤
        if (victim.getType().is(TensuraEntityTags.EP_DROP_EXCLUDED)) return;
        if (TensuraStorages.getExistenceFrom(victim).isSkippingEPDrop()) return;

        var summonerOpt = fox.getSummonerUUID();
        if (summonerOpt.isEmpty()) return;
        if (!(fox.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(summonerOpt.get());
        if (owner == null) return;

        double base = EnergyHelper.getEPGain(victim, fox, false);
        if (base <= 0) return;

        int multiplier = victim.level().getGameRules().getInt(TensuraGameRules.EP_GAIN_MULTIPLIER);
        // 与 Tensura.DeathHandler 一致的"含等级 / 衰减"那条计算
        double full = EnergyHelper.getEPGain(victim, fox, true) * multiplier;
        double half = full * 0.5;

        // 阻止 Tensura 默认 EP 分配（它会把 100% 给狐灵）
        TensuraStorages.getExistenceFrom(victim).setSkippingEPDrop(true);

        // 手动按 50/50 分到玩家和狐灵
        DeathHandler.gainEPEntity(owner, victim, half);
        DeathHandler.gainEPEntity(fox, victim, half);
    }
}

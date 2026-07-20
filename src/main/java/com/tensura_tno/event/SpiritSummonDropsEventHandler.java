package com.tensura_tno.event;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.registry.TensuraTNOSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * 灵之召唤的召唤物死亡时不掉落任何物品。
 * <p>
 * 防止玩家通过"收纳 → 召唤 → 击杀"的循环刷取物品掉落。
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID)
public final class SpiritSummonDropsEventHandler {

    private SpiritSummonDropsEventHandler() {}

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        try {
            IExistence existence = TensuraStorages.getExistenceFrom(entity);
            if (existence.getSummonedAbility() != null
                    && existence.getSummonedAbility().getSkill() == TensuraTNOSkills.SPIRIT_SUMMON.get()) {
                // 灵之召唤的召唤物——取消所有掉落
                event.setCanceled(true);
            }
        } catch (Exception ignored) {
            // 非 Tensura 实体，忽略
        }
    }
}

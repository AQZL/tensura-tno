package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.TensuraTNOMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID)
public final class SpiritSummonRetaliationEventHandler {
    private static final double RETALIATION_RANGE = 64.0D;

    private SpiritSummonRetaliationEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) return;
        if (!isValidAttacker(player, attacker)) return;

        for (Mob summon : SpiritSummonEntityHelper.findActiveSpiritSummons(player)) {
            orderRetaliation(summon, attacker);
        }
    }

    private static boolean isValidAttacker(ServerPlayer player, LivingEntity attacker) {
        if (!attacker.isAlive()) return false;
        if (attacker == player) return false;
        if (attacker.level() != player.level()) return false;
        if (player.isSpectator()) return false;
        if (SpiritSummonEntityHelper.isOwnedBy(attacker, player)) return false;
        return !player.isAlliedTo(attacker);
    }

    private static void orderRetaliation(Mob summon, LivingEntity attacker) {
        if (!summon.isAlive()) return;
        if (summon.level() != attacker.level()) return;
        if (summon.distanceToSqr(attacker) > RETALIATION_RANGE * RETALIATION_RANGE) return;
        if (!TargetingConditions.DEFAULT.test(summon, attacker)) return;

        summon.setTarget(attacker);
        try {
            summon.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
            summon.getBrain().setMemory(MemoryModuleType.HURT_BY_ENTITY, attacker);
        } catch (Exception ignored) {
            // Some custom mobs do not declare these memories; Mob#setTarget still covers goal-based AI.
        }
    }
}

package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.TensuraTNOMod;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Supplies tame-like behaviour to Spirit Summons whose vanilla entity class
 * has no tame state: following, stay/wander commands, owner protection and
 * friendly-fire protection.
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID)
public final class SpiritSummonTameBehaviorHandler {
    private static final String TAG_COMMAND = "tensura_tno_spirit_summon_command";
    private static final int FOLLOW = 0;
    private static final int WANDER = 1;
    private static final int STAY = 2;
    private static final double START_FOLLOW_DISTANCE_SQR = 8.0D * 8.0D;
    private static final double STOP_FOLLOW_DISTANCE_SQR = 3.0D * 3.0D;
    private static final double TELEPORT_DISTANCE_SQR = 48.0D * 48.0D;
    private static final double MAX_GUARD_DISTANCE_SQR = 64.0D * 64.0D;
    private static final int UNIVERSAL_ATTACK_INTERVAL = 20;

    private SpiritSummonTameBehaviorHandler() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob summon)) return;
        if (summon.level().isClientSide()) return;
        if (!SpiritSummonEntityHelper.isSpiritSummon(summon)) return;

        ServerPlayer owner = findOwner(summon);
        if (owner == null || owner.isSpectator()) return;
        if (owner.level() != summon.level()) return;

        LivingEntity target = summon.getTarget();
        if (target != null && (!target.isAlive()
                || target.level() != summon.level()
                || summon.distanceToSqr(target) > MAX_GUARD_DISTANCE_SQR
                || isOwnerOrOwnersAlly(target, owner))) {
            clearTarget(summon);
            target = null;
        }

        int command = summon.getPersistentData().getInt(TAG_COMMAND);
        if (command == STAY) {
            clearTarget(summon);
            summon.getNavigation().stop();
            return;
        }
        if (target != null) {
            guardOwner(summon, target);
            return;
        }
        if (command == WANDER) return;

        double distance = summon.distanceToSqr(owner);
        if (distance > TELEPORT_DISTANCE_SQR) {
            if (teleportNearOwner(summon, owner)) {
                summon.getNavigation().stop();
            } else {
                summon.getNavigation().moveTo(owner, 1.2D);
            }
        } else if (distance > START_FOLLOW_DISTANCE_SQR) {
            summon.getNavigation().moveTo(owner, 1.2D);
        } else if (distance < STOP_FOLLOW_DISTANCE_SQR) {
            summon.getNavigation().stop();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) return;

        if (SpiritSummonEntityHelper.isSpiritSummon(victim)) {
            UUID ownerId = SpiritSummonEntityHelper.getSpiritSummonOwnerUUID(victim);
            if (ownerId != null && (ownerId.equals(attacker.getUUID())
                    || ownerId.equals(SpiritSummonEntityHelper.getSpiritSummonOwnerUUID(attacker))
                    || isAlliedWithOwner(victim, attacker, ownerId))) {
                event.setCanceled(true);
                return;
            }
        }

        if (SpiritSummonEntityHelper.isSpiritSummon(attacker)) {
            UUID ownerId = SpiritSummonEntityHelper.getSpiritSummonOwnerUUID(attacker);
            if (ownerId != null && (ownerId.equals(victim.getUUID())
                    || ownerId.equals(SpiritSummonEntityHelper.getSpiritSummonOwnerUUID(victim))
                    || isAlliedWithOwner(attacker, victim, ownerId))) {
                event.setCanceled(true);
            }
        }
    }

    /** Sneak + empty-hand right click cycles Follow -> Wander -> Stay. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty()) return;
        if (!(event.getTarget() instanceof Mob summon)) return;
        if (!SpiritSummonEntityHelper.isSpiritSummonOf(summon, player)) return;

        int next = (summon.getPersistentData().getInt(TAG_COMMAND) + 1) % 3;
        summon.getPersistentData().putInt(TAG_COMMAND, next);
        clearTarget(summon);
        summon.getNavigation().stop();

        Component message = switch (next) {
            case WANDER -> Component.translatable("tensura.message.pet.wander", summon.getDisplayName());
            case STAY -> Component.translatable("tensura.message.pet.stay", summon.getDisplayName());
            default -> Component.translatable("tensura.message.pet.follow", summon.getDisplayName());
        };
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.AQUA), true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static ServerPlayer findOwner(Mob summon) {
        if (!(summon.level() instanceof ServerLevel level)) return null;
        UUID ownerId = SpiritSummonEntityHelper.getSpiritSummonOwnerUUID(summon);
        return ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
    }

    private static boolean isOwnerOrOwnersAlly(LivingEntity entity, ServerPlayer owner) {
        if (entity.getUUID().equals(owner.getUUID())) return true;
        UUID entityOwner = SpiritSummonEntityHelper.getSpiritSummonOwnerUUID(entity);
        return Objects.equals(owner.getUUID(), entityOwner) || owner.isAlliedTo(entity);
    }

    private static boolean isAlliedWithOwner(LivingEntity summon, LivingEntity other, UUID ownerId) {
        if (!(summon.level() instanceof ServerLevel level)) return false;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        return owner != null && owner.level() == other.level() && owner.isAlliedTo(other);
    }

    /**
     * Goal-based hostile mobs keep their native attacks. Passive and Brain-driven
     * mobs receive a small fallback melee strike so every Spirit Summon can
     * actually carry out an owner-protection order.
     */
    private static void guardOwner(Mob summon, LivingEntity target) {
        summon.getLookControl().setLookAt(target, 30.0F, 30.0F);
        summon.getNavigation().moveTo(target, 1.25D);

        double reach = Math.max(2.25D,
                (double) summon.getBbWidth() * summon.getBbWidth()
                        + (double) target.getBbWidth() * target.getBbWidth() + 1.0D);
        if (summon.tickCount % UNIVERSAL_ATTACK_INTERVAL != 0
                || summon.distanceToSqr(target) > reach
                || !summon.hasLineOfSight(target)) return;

        float oldHealth = target.getHealth();
        boolean attacked = summon.doHurtTarget(target);
        if ((!attacked || target.getHealth() >= oldHealth) && target.isAlive()) {
            target.hurt(summon.damageSources().mobAttack(summon), 2.0F);
        }
        summon.swing(InteractionHand.MAIN_HAND);
    }

    /** Finds a collision-free, dry, supported block around the owner. */
    private static boolean teleportNearOwner(Mob summon, ServerPlayer owner) {
        ServerLevel level = (ServerLevel) summon.level();
        BlockPos center = owner.blockPosition();
        for (int yOffset = 1; yOffset >= -1; yOffset--) {
            for (int radius = 2; radius <= 4; radius++) {
                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        if (Math.abs(xOffset) != radius && Math.abs(zOffset) != radius) continue;
                        BlockPos pos = center.offset(xOffset, yOffset, zOffset);
                        if (!level.getFluidState(pos).isEmpty()) continue;
                        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(),
                                net.minecraft.core.Direction.UP)) continue;

                        double x = pos.getX() + 0.5D;
                        double y = pos.getY();
                        double z = pos.getZ() + 0.5D;
                        if (!level.noCollision(summon,
                                summon.getBoundingBox().move(x - summon.getX(), y - summon.getY(), z - summon.getZ()))) {
                            continue;
                        }
                        summon.teleportTo(x, y, z);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void clearTarget(Mob summon) {
        summon.setTarget(null);
        summon.setLastHurtMob(null);
        summon.setLastHurtByMob(null);
        try {
            summon.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
            summon.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY_ENTITY);
            summon.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT);
        } catch (Exception ignored) {
        }
    }
}

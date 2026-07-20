package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.TensuraTNOMod;
import io.github.manasmods.tensura.entity.template.TensuraRideableEntity;
import io.github.manasmods.tensura.entity.template.subclass.ILivingPartEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID)
public final class SpiritSummonRidingEventHandler {
    private static final TagKey<EntityType<?>> SPIRIT_SUMMON_RIDEABLE =
            TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "spirit_summon_rideable"));
    private static final TagKey<EntityType<?>> SPIRIT_SUMMON_FLYING_MOUNT =
            TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "spirit_summon_flying_mount"));
    private static final Map<UUID, Boolean> PREVIOUS_NO_GRAVITY = new HashMap<>();

    private SpiritSummonRidingEventHandler() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (player.isPassenger()) return;
        if (player.isSecondaryUseActive()) return;
        if (!player.getItemInHand(event.getHand()).isEmpty()) return;

        Entity target = normalizeTarget(event.getTarget());
        if (!(target instanceof Mob mob)) return;
        if (!canPlayerMount(player, mob)) return;

        mob.getNavigation().stop();
        player.setYRot(mob.getYRot());
        player.setXRot(mob.getXRot());
        if (player.startRiding(mob, true)) {
            event.setCancellationResult(InteractionResult.sidedSuccess(false));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMountChange(EntityMountEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity mounted = normalizeTarget(event.getEntityBeingMounted());
        if (!(mounted instanceof Mob mob)) return;
        if (!mob.getType().is(SPIRIT_SUMMON_FLYING_MOUNT)) return;

        if (event.isMounting()) {
            PREVIOUS_NO_GRAVITY.putIfAbsent(mob.getUUID(), mob.isNoGravity());
        } else {
            Boolean previous = PREVIOUS_NO_GRAVITY.remove(mob.getUUID());
            if (previous != null) {
                mob.setNoGravity(previous);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mount)) return;
        if (mount.level().isClientSide()) return;
        if (mount instanceof TensuraRideableEntity) return;
        if (!(mount.getFirstPassenger() instanceof ServerPlayer rider)) return;
        if (!SpiritSummonEntityHelper.isSpiritSummonOf(mount, rider)) return;

        boolean flying = mount.getType().is(SPIRIT_SUMMON_FLYING_MOUNT);
        boolean genericRideable = flying || mount.getType().is(SPIRIT_SUMMON_RIDEABLE);
        if (!genericRideable) return;

        applyGenericControl(mount, rider, flying);
    }

    private static Entity normalizeTarget(Entity target) {
        if (target instanceof ILivingPartEntity part && part.getHead() instanceof Entity head) {
            return head;
        }
        return target;
    }

    private static boolean canPlayerMount(ServerPlayer player, Mob mob) {
        if (!mob.isAlive()) return false;
        if (mob.isBaby()) return false;
        if (mob.level() != player.level()) return false;
        if (!SpiritSummonEntityHelper.isSpiritSummonOf(mob, player)) return false;
        if (mob instanceof TensuraRideableEntity) return true;
        return mob.getType().is(SPIRIT_SUMMON_RIDEABLE) || mob.getType().is(SPIRIT_SUMMON_FLYING_MOUNT);
    }

    private static void applyGenericControl(Mob mount, ServerPlayer rider, boolean flying) {
        if (rider.isSpectator()) return;
        mount.getNavigation().stop();
        mount.setYRot(rider.getYRot());
        mount.setYHeadRot(rider.getYRot());
        mount.setYBodyRot(rider.getYRot());
        if (flying) {
            PREVIOUS_NO_GRAVITY.putIfAbsent(mount.getUUID(), mount.isNoGravity());
            mount.setNoGravity(true);
            mount.fallDistance = 0.0F;
        }

        float forwardInput = rider.zza;
        float strafeInput = rider.xxa;
        boolean hasHorizontalInput = Math.abs(forwardInput) > 0.01F || Math.abs(strafeInput) > 0.01F;
        if (!hasHorizontalInput) {
            if (flying) {
                mount.setDeltaMovement(mount.getDeltaMovement().multiply(0.55D, 0.55D, 0.55D));
            }
            return;
        }

        double speed = getControlledSpeed(mount, rider, flying, forwardInput);
        double yaw = Math.toRadians(rider.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 strafe = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 horizontal = forward.scale(forwardInput * speed)
                .add(strafe.scale(strafeInput * speed * 0.65D));

        double vertical = 0.0D;
        if (flying) {
            double pitch = Math.toRadians(rider.getXRot());
            vertical = -Math.sin(pitch) * Math.max(0.0F, forwardInput) * speed;
            vertical = Mth.clamp(vertical, -0.7D, 0.7D);
        }

        Vec3 movement = new Vec3(horizontal.x, flying ? vertical : 0.0D, horizontal.z);
        mount.move(MoverType.SELF, movement);
        mount.setDeltaMovement(mount.getDeltaMovement().multiply(0.35D, flying ? 0.35D : 1.0D, 0.35D));
        mount.hasImpulse = true;
    }

    private static double getControlledSpeed(Mob mount, ServerPlayer rider, boolean flying, float forwardInput) {
        double speed = mount.getAttributeValue(Attributes.MOVEMENT_SPEED) * (rider.isSprinting() ? 1.8D : 1.25D);
        if (forwardInput < 0.0F) {
            speed *= 0.5D;
        }
        return Mth.clamp(speed, 0.12D, flying ? 1.0D : 0.55D);
    }
}

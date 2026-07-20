package com.tensura_tno.client.race;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Client-only animation proxy for the fox-spirit player race model. */
public final class PlayerFoxSpiritAnimatable implements GeoAnimatable {

    private static final float NORMAL_VISUAL_HEIGHT = 1.45F;
    private static final double SPRINT_ANIMATION_SPEED = 1.5D;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.kitsune.idle");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.kitsune.sit");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.kitsune.walk");
    private static final RawAnimation CROUCH_IDLE = RawAnimation.begin()
            .thenLoop("animation.kitsune.crouch_idle");
    private static final RawAnimation CROUCH_WALK = RawAnimation.begin()
            .thenLoop("animation.kitsune.crouch_walk");
    private static final RawAnimation PRONE_IDLE = RawAnimation.begin()
            .thenLoop("animation.kitsune.prone_idle");
    private static final RawAnimation PRONE_WALK = RawAnimation.begin()
            .thenLoop("animation.kitsune.prone_walk");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final long instanceId;
    private Player player;
    private boolean wasSleeping;

    public PlayerFoxSpiritAnimatable(Player player) {
        this.player = player;
        this.instanceId = player.getUUID().getMostSignificantBits()
                ^ player.getUUID().getLeastSignificantBits();
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public long getInstanceId() {
        return instanceId;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(
                this, "loopController", 0, this::loopController));
    }

    private PlayState loopController(AnimationState<PlayerFoxSpiritAnimatable> state) {
        Player p = this.player;
        if (p == null) return PlayState.STOP;

        if (p.isSleeping()) {
            if (!this.wasSleeping) {
                state.getController().forceAnimationReset();
            }
            this.wasSleeping = true;
            // Freeze prone_idle at animation time zero while the player sleeps.
            state.getController().setAnimationSpeed(0.0D);
            return state.setAndContinue(PRONE_IDLE);
        }
        if (this.wasSleeping) {
            state.getController().forceAnimationReset();
            this.wasSleeping = false;
        }
        state.getController().setAnimationSpeed(1.0D);
        if (shouldSit(p)) {
            return state.setAndContinue(SIT);
        }
        if (shouldProne(p)) {
            return state.setAndContinue(shouldUseMovingProne(p) ? PRONE_WALK : PRONE_IDLE);
        }
        if (shouldCrouch(p)) {
            return state.setAndContinue(isMoving(p) ? CROUCH_WALK : CROUCH_IDLE);
        }
        if (isMoving(p)) {
            state.getController().setAnimationSpeed(
                    p.isSprinting() ? SPRINT_ANIMATION_SPEED : 1.0D);
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    public static boolean shouldSit(Player player) {
        Entity vehicle = player.getVehicle();
        return player.isPassenger() && vehicle != null && vehicle.shouldRiderSit();
    }

    public static boolean shouldCrouch(Player player) {
        return !player.isSleeping()
                && !shouldProne(player)
                && !shouldSit(player)
                && (player.isShiftKeyDown() || isUnderLowCeiling(player));
    }

    /** Keeps the crouch appearance while a one-block body is below a low ceiling. */
    private static boolean isUnderLowCeiling(Player player) {
        if (player.level() == null) return false;
        AABB current = player.getBoundingBox();
        double requiredHeight = NORMAL_VISUAL_HEIGHT * player.getScale();
        if (current.getYsize() + 1.0E-4D >= requiredHeight) return false;

        AABB standingVisualBox = new AABB(
                current.minX, current.minY, current.minZ,
                current.maxX, current.minY + requiredHeight, current.maxZ)
                .deflate(1.0E-7D);
        return !player.level().noBlockCollision(player, standingVisualBox);
    }

    public static boolean shouldProne(Player player) {
        return player.isSleeping()
                || player.isVisuallySwimming()
                || player.getPose() == Pose.SWIMMING
                || player.isFallFlying()
                || player.isAutoSpinAttack();
    }

    private static boolean shouldUseMovingProne(Player player) {
        return !player.isSleeping()
                && (isMoving(player)
                || player.isVisuallySwimming()
                || player.isFallFlying()
                || player.isAutoSpinAttack());
    }

    private static boolean isMoving(Player player) {
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        return dx * dx + dz * dz > 1.0E-4D;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return player != null && player.level() != null ? player.level().getGameTime() : 0.0D;
    }
}

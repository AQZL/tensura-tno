package com.tensura_tno.client.race;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Client-side proxy animatable used to render the Tensura orc Geo model at a
 * player position without creating a real OrcEntity.
 */
public final class PlayerOrcAnimatable implements GeoAnimatable {

    private static final RawAnimation ATTACK = RawAnimation.begin()
            .then("animation.orc.attack", Animation.LoopType.PLAY_ONCE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final long instanceId;
    private Player player;
    private AnimationController<PlayerOrcAnimatable> actionController;
    private boolean wasSwinging;
    private int lastSwingTime = Integer.MIN_VALUE;

    public PlayerOrcAnimatable(Player player) {
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

    public void syncPlayerState(float partialTick) {
        Player p = this.player;
        if (p == null) return;

        boolean swinging = p.swinging;
        boolean restartedSwing = swinging && this.wasSwinging && p.swingTime < this.lastSwingTime;
        if (swinging && (!this.wasSwinging || restartedSwing)) {
            triggerAttackAnimation();
        }
        this.wasSwinging = swinging;
        this.lastSwingTime = swinging ? p.swingTime : Integer.MIN_VALUE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "loopController", 0, this::loopController));
        this.actionController = new AnimationController<PlayerOrcAnimatable>(
                this, "actionController", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK);
        controllers.add(this.actionController);
    }

    private void triggerAttackAnimation() {
        if (this.actionController == null) return;
        this.actionController.forceAnimationReset();
        this.actionController.tryTriggerAnimation("attack");
    }

    private PlayState loopController(AnimationState<PlayerOrcAnimatable> state) {
        Player p = this.player;
        if (p == null) return PlayState.STOP;

        String name;
        if (shouldSit(p)) {
            name = "animation.orc.sit";
        } else if (p.isVisuallySwimming()) {
            name = "animation.orc.swim";
        } else if (shouldCrouch(p) && isMoving(p)) {
            name = "animation.orc.crouch_walk";
        } else if (shouldCrouch(p)) {
            name = "animation.orc.new";
        } else if (isMoving(p)) {
            name = p.isSprinting() ? "animation.orc.run" : "animation.orc.walk";
        } else {
            name = "animation.orc.idle";
        }

        if ("animation.orc.new".equals(name)) {
            return state.setAndContinue(RawAnimation.begin()
                    .then(name, Animation.LoopType.HOLD_ON_LAST_FRAME));
        }
        return state.setAndContinue(RawAnimation.begin().thenLoop(name));
    }

    public static boolean shouldSit(Player player) {
        Entity vehicle = player.getVehicle();
        return player.isPassenger() && vehicle != null && vehicle.shouldRiderSit();
    }

    public static boolean shouldCrouch(Player player) {
        return player.isShiftKeyDown()
                && !player.isSleeping()
                && !player.isVisuallySwimming()
                && !shouldSit(player);
    }

    private static boolean isMoving(Player p) {
        double dx = p.getX() - p.xOld;
        double dz = p.getZ() - p.zOld;
        return (dx * dx + dz * dz) > 1.0E-4;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        if (player != null && player.level() != null) {
            return player.level().getGameTime();
        }
        return 0;
    }
}

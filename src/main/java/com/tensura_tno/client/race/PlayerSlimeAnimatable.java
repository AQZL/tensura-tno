package com.tensura_tno.client.race;

import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 客户端虚拟 GeoAnimatable，用于把 tensura:slime 的 geckolib 模型与动画
 * 渲染到玩家位置上。每个玩家一个独立实例，由 {@link PlayerSlimeRenderManager} 管理。
 *
 * <p>动画选择跟随原版 SlimeEntity 的 loopController（参见 tensura
 * {@code slime.animation.json}）：
 * <ul>
 *     <li>水中移动 → animation.slime.swim</li>
 *     <li>陆地移动 → animation.slime.walk</li>
 *     <li>静止     → animation.slime.idle</li>
 * </ul>
 *
 * <p>该实例不会被注册到任何注册表，只在客户端持有，没有服务端副本。
 */
public final class PlayerSlimeAnimatable implements GeoAnimatable {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 关联的玩家。每帧渲染前由管理器更新，避免持有过期 Player 引用。 */
    private Player player;

    /** 唯一的 geckolib 实例 ID（由 player UUID 推导，保证每玩家独立动画状态）。 */
    private final long instanceId;

    public PlayerSlimeAnimatable(Player player) {
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
        controllers.add(new AnimationController<>(this, "loopController", 3, this::loopController));
    }

    private PlayState loopController(AnimationState<PlayerSlimeAnimatable> state) {
        Player p = this.player;
        if (p == null) return PlayState.STOP;

        boolean moving = isMoving(p);
        String name;
        if (shouldKeepDefaultShape(p)) {
            name = "animation.slime.idle";
        } else if (moving) {
            name = "animation.slime.walk";
        } else {
            name = "animation.slime.idle";
        }
        return state.setAndContinue(RawAnimation.begin().thenLoop(name));
    }

    private static boolean isMoving(Player p) {
        // 用水平移动距离判定。0.0001 阈值避免微小漂移产生抖动切换。
        double dx = p.getX() - p.xOld;
        double dz = p.getZ() - p.zOld;
        return (dx * dx + dz * dz) > 1.0E-4;
    }

    public static boolean shouldKeepDefaultShape(Player player) {
        return player.isSleeping()
                || player.isInWater()
                || player.isInWaterOrBubble()
                || player.isSwimming()
                || player.isVisuallySwimming();
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object obj) {
        // 用世界 game time 作为时钟，geckolib 内部会乘 50 ms。
        if (player != null && player.level() != null) {
            return player.level().getGameTime();
        }
        return 0;
    }
}

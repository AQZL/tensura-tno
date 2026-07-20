package com.tensura_tno.client.race;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensura_tno.TensuraTNOMod;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 玩家是 4 种 slime 种族（slime / metal_slime / demon_slime / god_slime）之一时，
 * 在第三人称视角下用主模组 tensura:slime 的 geckolib 模型与动画替代玩家原渲染。
 *
 * <p><b>第一人称行为</b>：第一人称视角看自己时，{@link RenderPlayerEvent} 不触发，
 * 所以本 handler 自动只对第三人称（F5 切换 / 看其他玩家）生效。第一人称
 * 看自己手部的渲染走 {@code RenderHandEvent} 通路，本类不拦截，保留原版人手。
 * 这与 woodwalkers 默认行为一致。
 *
 * <p><b>物品栏 / 角色预览</b>：物品栏内的玩家预览也走 {@link RenderPlayerEvent}，
 * 仍然渲染为 slime（用户要求）。GUI 上下文的 PoseStack 已被 vanilla 按
 * {@code 30 / entity.getBbHeight()} 缩放过（玩家 1.8m → 16.67× 放大），
 * 由 {@link PlayerSlimeRenderer#preRender} 检测 {@link net.minecraft.client.Minecraft#screen}
 * 是否非 null，GUI 内换用更小的固定缩放（不再走世界用的"按碰撞箱填充"），
 * 让 slime 在预览框里大小适中。
 *
 * <p><b>玩家碰撞箱不变</b>：仅替换视觉渲染，玩家自身的 0.6×1.8 碰撞箱保持原样。
 *
 * <p><b>装备 / 名字 / 手中物品</b>：跟随 vanilla 玩家渲染一起被取消（不显示）。
 * 此为最稳妥的"完整变身"行为，与 woodwalkers 默认 config 一致。
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, value = Dist.CLIENT)
public final class SlimeRacePlayerRenderHandler {

    private SlimeRacePlayerRenderHandler() {}

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (player.isSpectator() && PlayerSlimeRenderManager.isSlimeRace(player)) {
            event.setCanceled(true);
            return;
        }
        if (!PlayerSlimeRenderManager.shouldRenderAsSlime(player)) return;

        // 取消 vanilla 渲染：玩家身体、盔甲、所有 layer（含名字 / 装备）全部不画
        event.setCanceled(true);

        PoseStack poseStack = event.getPoseStack();

        // 渲染 slime 模型。RenderPlayerEvent 触发时 PoseStack 已经平移到玩家位置。
        poseStack.pushPose();
        try {
            PlayerSlimeRenderManager.render(
                    player,
                    poseStack,
                    event.getMultiBufferSource(),
                    event.getPackedLight(),
                    event.getPartialTick());
        } catch (Throwable t) {
            TensuraTNOMod.LOGGER.error(
                    "[TensuraTNO] Failed to render slime for player {}: {}",
                    player.getGameProfile().getName(), t.toString());
        }
        poseStack.popPose();
    }

    /** 玩家退出时清理 animatable 缓存，避免内存泄漏。 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerSlimeRenderManager.forget(event.getEntity().getUUID());
    }
}

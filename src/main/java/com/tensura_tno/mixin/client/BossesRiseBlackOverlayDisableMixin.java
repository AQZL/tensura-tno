package com.tensura_tno.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁用 block_factorys_bosses 的全屏黑屏 cinematic overlay。
 *
 * <p>BossesRise 的 {@code BlackRenderer} 监听 {@code RenderGuiEvent.Post}，并在
 * {@code BossesRiseClientCinematicCamera.isHandlerActive(CinematicCameraTypes.BLACK)}
 * 为 true 时调用 {@code onRenderGui(GuiGraphics)} 用 {@code -16777216}（不透明黑色）
 * 全屏 fill。受影响阶段最典型的就是赫尔瓦（Helvar, the Underworld Knight）的 intro
 * 与第二阶段转换：黑屏期间 boss 的 melee / dash / AoE 仍在结算，玩家盲打中常被秒杀。
 *
 * <p>本 mixin 在 {@code onRenderGui} 入口直接 {@link CallbackInfo#cancel()}，不再
 * 绘制黑色填充。BossesRise 的状态机、phase 推进、动画、镜头偏移、letterbox bars
 * （由独立的 {@code BarsRenderer} 处理）等其余 cinematic 元素全部保留，玩家始终能
 * 看见 boss 与战斗场景。所有继承自 {@code AbstractBossEntity} 的 boss
 * （Helvar / Ashlord / Sirok / Nerakyss 等）共享同一个 {@code BLACK} cinematic
 * 渠道，单一 mixin 即可覆盖全部。
 *
 * <p>{@code BlackRenderer} 是 client-only 类，因此本 mixin 必须注册在
 * {@code tensura_tno.mixins.json} 的 {@code client} 数组而不是 {@code mixins} 数组，
 * 否则服务端启动会因 {@code ClassNotFoundException} 失败。
 *
 * <p>{@link Pseudo @Pseudo} + {@code remap = false} + {@code require = 0}：BossesRise
 * 未加载时该 mixin 静默跳过；BossesRise 内部 API 改名时也仅是该修复失效，不影响其余
 * mixin。
 */
@Pseudo
@Mixin(targets = "net.unusual.block_factorys_bosses.client.camera.renderer.BlackRenderer", remap = false)
public abstract class BossesRiseBlackOverlayDisableMixin {

    @Inject(
        method = "onRenderGui",
        at = @At("HEAD"),
        cancellable = true,
        remap = true,
        require = 0
    )
    private static void tensuraTno$skipBlackOverlay(GuiGraphics graphics, CallbackInfo ci) {
        // 完全短路 BLACK overlay 的全屏黑色 fill；其余 cinematic（letterbox bars
        // 由 BarsRenderer / 镜头偏移由 ShakeRenderer / camera bone 由 CameraRenderer
        // 处理）不受影响。
        ci.cancel();
    }
}

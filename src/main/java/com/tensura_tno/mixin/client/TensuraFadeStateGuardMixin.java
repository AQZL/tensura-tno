package com.tensura_tno.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 与 Tensura 主模组的菜单 fade 效果兼容性补丁。
 *
 * <p>Tensura 的 {@code RenderHelper.handleFade(GuiGraphics, Runnable)} 在 fade
 * 期间会全屏调用 {@link RenderSystem#setShaderColor(float, float, float, float)}
 * 设置 alpha，再 run 内部的 {@code runnable}（也就是整张 Tensura GUI 的渲染）。
 * 如果 runnable 在中途抛异常、或被其它模组的 RenderSystem 状态污染，alpha 状态
 * 不会被还原，导致接下去整帧画面带着错误 alpha / blend，表现就是 GUI 全黑或半透明。
 *
 * <p>本 mixin 在 {@code handleFade} 的 RETURN 处兜底重置：把 shader color 强制
 * 回到 {@code (1, 1, 1, 1)}、关掉 blend，让任何后续渲染进入一个干净的状态。
 * 不取消原方法、不改返回值，纯防御。
 *
 * <p>{@link Pseudo @Pseudo} + {@code require = 0}：主模组缺失或方法签名变了
 * 也不会强制 fail-load。
 */
@Pseudo
@Mixin(targets = "io.github.manasmods.tensura.util.client.RenderHelper", remap = false)
public abstract class TensuraFadeStateGuardMixin {

    @Inject(
        method = "handleFade(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Runnable;)Z",
        at = @At("RETURN"),
        remap = false,
        require = 0
    )
    private static void tensuraTno$resetRenderState(
            GuiGraphics graphics, Runnable runnable, CallbackInfoReturnable<Boolean> cir) {
        try {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        } catch (Throwable ignored) {
            // 渲染防御层，绝不向上抛
        }
    }
}

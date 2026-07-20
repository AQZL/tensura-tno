package com.tensura_tno.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 与 Tensura 主模组的菜单 blur 兼容性补丁。
 *
 * <p>Tensura 的 {@code RenderHelper.renderCustomBlur(int)} 直接调用
 * {@code Minecraft.getInstance().gameRenderer.blurEffect.process(1.0F)}，
 * 这是原版共享的同一份 {@link PostChain}，会被 vanilla 暂停菜单 blur、
 * 任何动 {@code GameRenderer} 内部状态的模组（Modern UI / Blur / NoMoreBlur /
 * Cleaner Menus / Embeddium / Iris / Optifine 系列等）以及切了主 RT 又没还原
 * 的模组（Create Ponder、Flywheel）抢用。
 *
 * <p>主模组的写法存在两处脆弱点：
 * <ol>
 *   <li>没有先把 PostChain 自身 resize 到当前主 RT 尺寸 —— 一旦冲突模组在更早的
 *       帧改了 PostChain 内部 target 大小，这帧 {@code process()} 就会向尺寸
 *       不匹配的目标采样，导致黑屏 / 缺画面。</li>
 *   <li>{@code process()} 抛异常时不会再调 {@code mainRT.bindWrite(false)}，
 *       后续 widget / 文字会画到一个未绑定（或仍是 PostChain 内部）的 target，
 *       同样表现为 GUI 不出现。</li>
 * </ol>
 *
 * <p>本 mixin 在 {@code renderCustomBlur} HEAD 处取消原方法，重新做安全版本：
 * 先 {@code resize} 同步尺寸，把 {@code process()} 包进 try/catch，并且在
 * finally 里 <b>无条件</b> bindWrite 主 RT。这样即便 PostChain 抛异常，
 * 后续渲染依旧画在正确的 main RT 上，GUI 不会消失。
 *
 * <p>{@link Pseudo @Pseudo} + {@code remap = false} + {@code require = 0}：
 * 工程编译期不强依赖 Tensura 反编译类；运行时 Tensura 总在场，mixin 一定生效。
 * 即便未来主模组换了 RenderHelper 的实现，这里也不会强制 fail-load。
 */
@Pseudo
@Mixin(targets = "io.github.manasmods.tensura.util.client.RenderHelper", remap = false)
public abstract class TensuraBlurSafetyMixin {

    /**
     * 反射拿 {@code GameRenderer#blurEffect}：NeoForge 1.21.1 把这个字段写成 private，
     * 编译期不可达；运行时通过 setAccessible 拿到。Mojang 名（{@code blurEffect}）和
     * SRG 名（{@code f_172573_}）都试一遍，谁先命中用谁。
     *
     * <p>只在第一次调用时反射，结果缓存到 {@link #BLUR_FIELD_RESOLVED} 标志位；
     * 失败也只发一次 warning，不重复。
     */
    private static volatile Field BLUR_FIELD;
    private static volatile boolean BLUR_FIELD_RESOLVED = false;

    private static PostChain tensuraTno$readBlurEffect(GameRenderer gr) {
        Field f = tensuraTno$resolveBlurField();
        if (f == null) return null;
        try {
            Object value = f.get(gr);
            return value instanceof PostChain ? (PostChain) value : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field tensuraTno$resolveBlurField() {
        if (BLUR_FIELD_RESOLVED) return BLUR_FIELD;
        synchronized (TensuraBlurSafetyMixin.class) {
            if (BLUR_FIELD_RESOLVED) return BLUR_FIELD;
            Field found = null;
            // 先按字段类型扫，避免硬编码 obfuscation name
            for (Field f : GameRenderer.class.getDeclaredFields()) {
                if (PostChain.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        found = f;
                        break;
                    } catch (RuntimeException ignored) {
                        // 字段不可访问就跳过
                    }
                }
            }
            BLUR_FIELD = found;
            BLUR_FIELD_RESOLVED = true;
            return found;
        }
    }

    @Inject(
        method = "renderCustomBlur(I)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void tensuraTno$safeBlur(int blurStrength, CallbackInfo ci) {
        ci.cancel();
        if (blurStrength <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        GameRenderer gr = mc.gameRenderer;
        if (gr == null) return;
        PostChain chain = tensuraTno$readBlurEffect(gr);
        if (chain == null) return;
        RenderTarget mainRT = mc.getMainRenderTarget();
        if (mainRT == null) return;

        try {
            // 关键修复 1：先把 PostChain 同步到当前主 RT 尺寸，避免别的 mod 改过尺寸后撞错
            chain.resize(mainRT.width, mainRT.height);
            chain.setUniform("Radius", (float) blurStrength);
            chain.process(1.0F);
        } catch (Throwable ignored) {
            // 渲染管线绝对不能向上抛 —— 任何异常都吞掉，进入 finally 保护
        } finally {
            // 关键修复 2：无论成功失败都把主 RT 绑回去，让后续 widget 渲染指向正确目标
            try {
                mainRT.bindWrite(false);
                // 顺手清理潜在的着色器色调污染
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            } catch (Throwable ignored) {
                // 双层保险，不让恢复路径自己崩
            }
        }
    }
}

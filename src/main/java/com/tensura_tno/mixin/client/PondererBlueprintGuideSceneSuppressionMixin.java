package com.tensura_tno.mixin.client;

import com.tensura_tno.util.PondererPaperFeatureAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 Ponderer 不再为 carrier 物品 (默认 minecraft:paper) 注册"基础教程"思索场景,
 * 在 {@link PondererPaperFeatureAccess#isEnabled()} 关闭时彻底跳过整个注册步骤。
 *
 * <p>原版行为: {@code DynamicPonderPlugin.registerScenes} 在场景加载时会调用
 * {@code registerBlueprintGuideScene(helper)}, 后者拿到当前 carrier 物品 (默认是 paper)
 * 然后通过 {@code helper.forComponents(carrier).addStoryBoard(...)} 给该物品挂上一个
 * "Blueprint Usage" 场景, 表现为: 拿着 paper 进入思索界面就能看到 ponderer 的基础教程分栏。</p>
 *
 * <p>本 mixin 在该方法 HEAD 直接 cancel: access 关闭时 paper 不会被推到 ponder 系统中,
 * 原本只为蓝图教程创造的"思索"入口也就消失了。</p>
 *
 * <p>注意场景注册只在 mod 启动 / {@code PondererClientCommands.reloadLocal()} 时执行,
 * 因此运行时通过 {@code /tno admin ponderer_paper true} 启用后, 需要在 ponderer 的
 * "功能 → 重载"按钮触发一次 reload, paper 上的教程才会重新出现。
 * 关闭则下一次 reload 时立刻消失。</p>
 *
 * <p>{@code targets = "..."} + {@code require = 0}: ponderer 没装时静默跳过。
 * 不写 method descriptor 因为该方法签名引用了 createmod 的 PonderSceneRegistrationHelper,
 * 该接口不在 compileClasspath 里, 用方法名匹配即可 (该类内仅此一个同名方法)。</p>
 */
@Mixin(
    targets = "com.nododiiiii.ponderer.ponder.DynamicPonderPlugin",
    remap = false
)
public abstract class PondererBlueprintGuideSceneSuppressionMixin {

    @Inject(
        method = "registerBlueprintGuideScene",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void tensuraTno$skipIfDisabled(CallbackInfo ci) {
        if (!PondererPaperFeatureAccess.isEnabled()) {
            ci.cancel();
        }
    }
}

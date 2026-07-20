package com.tensura_tno.mixin.client;

import com.tensura_tno.util.PondererScreenIds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复：Create 的 PonderUI（思索）场景界面左上角会出现 FTB Quests 的
 * 已钉住任务（Pinned Quests）和“当前观察任务”等 HUD 叠加层。
 *
 * <p>原因：FTB Quests 在 {@code FTBQuestsClientEventHandler#onScreenRender}
 * 里注册了 {@code ClientGuiEvent.RENDER_HUD}（= NeoForge
 * {@code RenderGuiEvent.Post}）。HUD 每帧都渲染，即使打开了屏幕——普通
 * 带有不透明背景的界面会把它盖住；但 {@code PonderUI} 使用的是
 * {@code BACKGROUND_TRANSPARENT} 半透明背景，底下的 FTB HUD 就会从
 * 左上角“透”出来。其他 Ponderer 子界面背景不透明所以不会出现。</p>
 *
 * <p>本 Mixin 在 HUD 回调入口处判断：如果当前屏幕是
 * {@code net.createmod.ponder.*}（Create 思索界面）或
 * {@code com.nododiiiii.ponderer.*}（Ponderer 扩展界面），就跳过
 * 这次 HUD 绘制，避免在思索画面上出现 FTB 元素。</p>
 *
 * <p>用 {@code targets=".."} 字符串 + {@code require=0} 的写法，
 * 是为了 FTB Quests 未安装时 Mixin 静默跳过、不引发崩溃。</p>
 */
@Mixin(
    targets = "dev.ftb.mods.ftbquests.client.FTBQuestsClientEventHandler",
    remap = false
)
public class FTBQuestsHudSuppressOnPonderUIMixin {

    @Inject(
        method = "onScreenRender(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void tensuraTno$skipHudOnPonderUI(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (PondererScreenIds.isPonderUI(Minecraft.getInstance().screen)) {
            ci.cancel();
        }
    }
}

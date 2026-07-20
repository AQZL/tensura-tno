package com.tensura_tno.mixin.client;

import com.tensura_tno.util.PondererScreenIds;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 FTB Library 的侧栏按钮组 (重放 / 任务书 / Tg / 成就等, 按
 * {@code ftblibrary-client.snbt} 里的 {@code sidebar.enabled=true} +
 * {@code sidebar.position=TOP_LEFT} 铺在左上角) 不再出现在 Create/Ponderer
 * 的思索界面上。
 *
 * <p>原理: FTB Library 用
 * {@code FTBLibraryClient.areButtonsVisible(Screen)} 决定是否往当前屏幕
 * 添加 {@code SidebarGroupGuiButton}; 虽然它默认只对
 * {@code AbstractContainerScreen} 返回 true, 但不同版本 / 整合包里有
 * 其他 mod 会改这个判断, 导致 PonderUI / PonderTagScreen 等屏上也被挂上
 * 侧栏按钮。</p>
 *
 * <p>本 Mixin 在 {@code areButtonsVisible} 的 RETURN 处, 如果当前屏是
 * {@code net.createmod.ponder.*} 或 {@code com.nododiiiii.ponderer.*}
 * 包下的任何 Screen, 强制把返回值改为 false。</p>
 *
 * <p>{@code targets = "..."} 字符串 + {@code require = 0}:
 * FTB Library 没装时 Mixin 静默跳过。</p>
 */
@Mixin(targets = "dev.ftb.mods.ftblibrary.FTBLibraryClient", remap = false)
public class FTBLibrarySidebarSuppressOnPonderUIMixin {

    @Inject(
        method = "areButtonsVisible(Lnet/minecraft/client/gui/screens/Screen;)Z",
        at = @At("RETURN"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void tensuraTno$hideOnPonderScreens(Screen gui, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        if (PondererScreenIds.isPonderUI(gui)) {
            cir.setReturnValue(false);
        }
    }
}

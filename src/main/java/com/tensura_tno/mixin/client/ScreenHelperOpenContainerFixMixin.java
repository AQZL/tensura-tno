package com.tensura_tno.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复：关闭 Ponderer/Ponder（W 键）界面后，按 B 键无法打开 Tensura 主界面的问题。
 */
@Mixin(targets = "io.github.manasmods.tensura.util.client.ScreenHelper", remap = false)
public class ScreenHelperOpenContainerFixMixin {

    @Redirect(
        method = "openScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;hasContainerOpen()Z"
        ),
        remap = false
    )
    private static boolean tensuraTno$bypassContainerOpenCheck(LocalPlayer player) {
        // 始终报告“没有容器打开”，让 Tensura 界面可以执行 minecraft.setScreen()
        return false;
    }
}

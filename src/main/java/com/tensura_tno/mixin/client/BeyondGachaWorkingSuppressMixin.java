package com.tensura_tno.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class BeyondGachaWorkingSuppressMixin {
    @Inject(method = "displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
    private void tensura_tno$suppressBeyondGachaWorking(Component component, boolean actionBar, CallbackInfo ci) {
        if (actionBar || component == null) {
            return;
        }

        String text = component.getString();
        if (!"working".equalsIgnoreCase(text == null ? "" : text.trim())) {
            return;
        }

        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("com.trbeyond.")) {
                ci.cancel();
                return;
            }
        }
    }
}
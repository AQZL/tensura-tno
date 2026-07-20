package com.tensura_tno.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.crypticdev.stextras.support.LoadingOverlayHooks", remap = false)
public class STExtrasLoadingOverlayDisableMixin {

    @Inject(method = "injectTextures", at = @At("HEAD"), cancellable = true, remap = false)
    private static void disableTextureHook(CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }

    @Inject(method = "injectRender", at = @At("HEAD"), cancellable = true, remap = false)
    private static void disableRenderHook(CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }
}
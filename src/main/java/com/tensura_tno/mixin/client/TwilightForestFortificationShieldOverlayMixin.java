package com.tensura_tno.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "twilightforest.client.event.OverlayHandler", remap = false)
public abstract class TwilightForestFortificationShieldOverlayMixin {

    @Inject(
        method = "renderShieldCount",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void tensuraTno$skipFortificationShieldHud(
        GuiGraphics graphics,
        Gui gui,
        int screenWidth,
        int screenHeight,
        int shieldCount,
        CallbackInfo ci
    ) {
        ci.cancel();
    }
}

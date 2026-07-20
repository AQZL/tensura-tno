package com.tensura_tno.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.trbeyond.gui.gacha.MainGuiScreen", remap = false)
public abstract class BeyondGachaServerModButtonSuppressMixin {

    @Inject(method = "hasServerModInfo", at = @At("HEAD"), cancellable = true)
    private void tensura_tno$hideServerModificationsInfoInSingleplayer(CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().isSingleplayer()) {
            cir.setReturnValue(false);
        }
    }
}

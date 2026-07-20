package com.tensura_tno.mixin.client;

import com.tensura_tno.client.screen.ReincarnationRulesScreen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ReincarnationScreenKeepMenuForRulesMixin {
    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void tensuraTno$keepReincarnationMenuForRules(CallbackInfo ci) {
        if (ReincarnationRulesScreen.consumeSuppressReincarnationRemoved((Screen) (Object) this)) {
            ci.cancel();
        }
    }
}

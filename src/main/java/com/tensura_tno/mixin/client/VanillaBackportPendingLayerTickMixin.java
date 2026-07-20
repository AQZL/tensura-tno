package com.tensura_tno.mixin.client;

import com.tensura_tno.client.VanillaBackportPendingLayers;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class VanillaBackportPendingLayerTickMixin {

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void tensura_tno$flushPendingVanillaBackportLayers(CallbackInfo ci) {
        VanillaBackportPendingLayers.flush();
    }
}
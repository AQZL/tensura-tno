package com.tensura_tno.mixin.client;

import net.minecraft.world.level.levelgen.feature.treedecorators.BeehiveDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeehiveDecorator.class)
public class GeophilicBeehiveDecoratorCrashFixMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void tensuraTno$skipIfTreeHasNoPlacementAnchors(TreeDecorator.Context context, CallbackInfo ci) {
        if (context.logs().isEmpty() || context.leaves().isEmpty()) {
            ci.cancel();
        }
    }
}

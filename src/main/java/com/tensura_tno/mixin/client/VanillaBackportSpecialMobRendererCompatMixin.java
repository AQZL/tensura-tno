package com.tensura_tno.mixin.client;

import com.tensura_tno.TensuraTNOCompatConfig;
import com.tensura_tno.client.VanillaBackportPendingLayers;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.blackgear.vanillabackport.client.api.renderer.SpecialMobRenderer", remap = false)
public class VanillaBackportSpecialMobRendererCompatMixin {

    @Inject(
        method = "addLayer(Ljava/util/Optional;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void tensura_tno$guardConfigBeforeLayerInit(Optional<?> renderer, Consumer<Object> consumer,
                                                               CallbackInfo ci) {
        if (!TensuraTNOCompatConfig.isVanillaBackportDelayedSheepLayerEnabled()) {
            return;
        }

        if (renderer.isEmpty()) {
            ci.cancel();
            return;
        }

        Object wrappedSupplier = renderer.get();
        if (!(wrappedSupplier instanceof Supplier<?> supplier)) {
            return;
        }

        final Object layer;
        try {
            layer = supplier.get();
        } catch (RuntimeException exception) {
            if (VanillaBackportPendingLayers.isConfigNotLoaded(exception)) {
                VanillaBackportPendingLayers.enqueue(supplier, consumer);
                ci.cancel();
                return;
            }

            throw exception;
        }

        if (layer != null) {
            consumer.accept(layer);
        }

        ci.cancel();
    }
}
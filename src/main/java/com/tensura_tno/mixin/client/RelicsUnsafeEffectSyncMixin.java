package com.tensura_tno.mixin.client;

import com.tensura_tno.TensuraTNOMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, priority = 1001)
public class RelicsUnsafeEffectSyncMixin {

    private static final ResourceLocation TENSURA_FROST_ID = ResourceLocation.fromNamespaceAndPath("tensura", "frost");
    private static final ResourceLocation TENSURA_CHILL_ID = ResourceLocation.fromNamespaceAndPath("tensura", "chill");
    private static final ResourceLocation TENSURA_PARALYSIS_ID = ResourceLocation.fromNamespaceAndPath("tensura", "paralysis");

    @Inject(method = {"handler$een000$relics$onEffectAdded", "handler$eek000$relics$onEffectAdded"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void tensuraTno$skipUnsafeAddedSync(MobEffectInstance effect, Entity target, CallbackInfo relicsCi, CallbackInfo ci) {
        if (tensuraTno$isUnsafeConnectorEffect(effect)) {
            ci.cancel();
        }
    }

    @Inject(method = {"handler$een000$relics$onEffectUpdated", "handler$eek000$relics$onEffectUpdated"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void tensuraTno$skipUnsafeUpdatedSync(MobEffectInstance effect, boolean forced, Entity target, CallbackInfo relicsCi, CallbackInfo ci) {
        if (tensuraTno$isUnsafeConnectorEffect(effect)) {
            ci.cancel();
        }
    }

    @Inject(method = {"handler$een000$relics$onEffectRemoved", "handler$eek000$relics$onEffectRemoved"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void tensuraTno$skipUnsafeRemovedSync(MobEffectInstance effect, CallbackInfo relicsCi, CallbackInfo ci) {
        if (tensuraTno$isUnsafeConnectorEffect(effect)) {
            ci.cancel();
        }
    }

    private static boolean tensuraTno$isUnsafeConnectorEffect(MobEffectInstance effect) {
        ResourceLocation effectId = effect.getEffect().unwrapKey()
                .map(key -> key.location())
                .orElse(null);
        boolean unsafe = TENSURA_FROST_ID.equals(effectId)
                || TENSURA_CHILL_ID.equals(effectId)
                || TENSURA_PARALYSIS_ID.equals(effectId);
        if (unsafe) {
            TensuraTNOMod.LOGGER.debug("[TensuraTNO] Skipping Relics sync for unsafe connector effect {}", effectId);
        }
        return unsafe;
    }
}
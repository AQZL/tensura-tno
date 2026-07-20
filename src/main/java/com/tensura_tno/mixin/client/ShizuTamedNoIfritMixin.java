package com.tensura_tno.mixin.client;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Prevents a tamed Shizu from summoning Ifrit on death.
 *
 * ShizuEntity.die() checks {@code getTransformTick() < 100}: if true it calls summonIfrit(),
 * otherwise it falls through to {@code super.die()}.  By setting TransformTick to 100
 * at the HEAD of die() for tamed instances, the original method naturally skips the
 * summonIfrit branch and processes a normal death.
 */
@Pseudo
@Mixin(targets = "io.github.manasmods.tensura.entity.human.ShizuEntity", remap = false)
public class ShizuTamedNoIfritMixin {

    @Inject(method = "die", at = @At("HEAD"), remap = true, require = 0)
    private void tensuraTno$preventIfritForTamed(DamageSource source, CallbackInfo ci) {
        if (!((Object) this instanceof TamableAnimal ta)) return;
        if (!ta.isTame()) return;
        try {
            Method setTransformTick = ta.getClass().getMethod("setTransformTick", int.class);
            setTransformTick.invoke(ta, 100);
        } catch (Exception ignored) {}
    }
}

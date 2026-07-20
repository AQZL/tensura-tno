package com.tensura_tno.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes StackOverflowError in ElementalColossusEntity.markAsPassed().
 *
 * The method recursively calls itself (line 273) when target is a Player
 * and getSubordinateOwner returns a non-null owner. If the owner chain
 * forms a cycle or the owner is also a Player, the recursion never terminates.
 *
 * Fix: ThreadLocal recursion guard — if already inside markAsPassed, skip.
 */
@Mixin(targets = "io.github.manasmods.tensura.entity.monster.ElementalColossusEntity", remap = false)
public class ElementalColossusRecursionFixMixin {

    @Unique
    private static final ThreadLocal<Boolean> tensuraTno$inMarkAsPassed = ThreadLocal.withInitial(() -> false);

    @Inject(method = "markAsPassed", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void tensuraTno$guardRecursion(LivingEntity protector, LivingEntity target,
                                                   boolean teleportToEntrance, boolean defeatProtector,
                                                   CallbackInfo ci) {
        if (tensuraTno$inMarkAsPassed.get()) {
            ci.cancel();
            return;
        }
        tensuraTno$inMarkAsPassed.set(true);
    }

    @Inject(method = "markAsPassed", at = @At("RETURN"), remap = false, require = 0)
    private static void tensuraTno$clearGuard(LivingEntity protector, LivingEntity target,
                                               boolean teleportToEntrance, boolean defeatProtector,
                                               CallbackInfo ci) {
        tensuraTno$inMarkAsPassed.set(false);
    }
}

package com.tensura_tno.mixin.client;

import com.tensura_tno.compat.TrAddonCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the premature {@code TrAddon.init()} call that traddon's {@code TrAddonNeoForge}
 * constructor triggers directly.
 *
 * <p>Because traddon's init() runs during NeoForge parallel mod construction, the
 * {@code manascore_skill:skills} registry may not yet exist, causing a crash when
 * {@code TrAddonIntrinsicSkills.init()} and {@code TrAddonUniqueSkills.init()} call
 * {@code SKILLS.register()} on an Architectury DeferredRegister backed by that registry.</p>
 *
 * <p>This Mixin intercepts the call and allows it only when
 * {@link TrAddonCompat#allowInit} is {@code true} (i.e., during
 * {@code NewRegistryEvent} LOW priority, replayed by {@link TrAddonCompat}).</p>
 */
@Pseudo
@Mixin(targets = "com.github.dominickwd04.traddon.TrAddon", remap = false)
public class TrAddonInitFixMixin {

    @Inject(
        method = "init",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void tensuraTno$cancelPrematureInit(CallbackInfo ci) {
        if (!TrAddonCompat.allowInit) {
            ci.cancel();
        }
    }
}

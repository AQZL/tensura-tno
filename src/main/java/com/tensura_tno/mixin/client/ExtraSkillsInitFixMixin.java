package com.tensura_tno.mixin.client;

import com.tensura_tno.compat.TRUniqueMonstersCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the premature {@code ExtraSkills.init()} call that tr_unique_monsters injects
 * into Tensura's {@code init()} via its own Mixin.
 *
 * <p>Because Tensura's init() runs during NeoForge parallel mod construction, the
 * {@code manascore_skill:skills} registry may not yet exist, causing a crash.
 * This Mixin intercepts the call and allows it only when
 * {@link TRUniqueMonstersCompat#allowInit} is {@code true} (i.e., during
 * {@code FMLCommonSetupEvent.enqueueWork()}).</p>
 */
@Pseudo
@Mixin(targets = "net.crypticmc.tr_unique_monsters.registry.skill.ExtraSkills", remap = false)
public class ExtraSkillsInitFixMixin {

    @Inject(
        method = "init",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void tensuraTno$cancelPrematureInit(CallbackInfo ci) {
        if (!TRUniqueMonstersCompat.allowInit) {
            ci.cancel();
        }
    }
}

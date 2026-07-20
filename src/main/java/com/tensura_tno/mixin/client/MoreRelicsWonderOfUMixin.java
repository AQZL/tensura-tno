package com.tensura_tno.mixin.client;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes a crash in MoreRelics WonderOfU on servers where the JVM does not
 * provide the "L32X64MixRandom" algorithm required by RandomGenerator.getDefault().
 *
 * Root cause: Collections.shuffle(list, RandomGenerator.getDefault()) at
 * WonderOfU.applyCalamity line 231 calls RandomGenerator.getDefault() which
 * returns L32X64MixRandom, unavailable on stripped JRE environments.
 *
 * Fix: Redirect the getDefault() call to ThreadLocalRandom.current(), which
 * is always available and implements RandomGenerator.
 */
@Pseudo
@Mixin(targets = "com.blorb.morerelics.relics.WonderOfU", remap = false)
public class MoreRelicsWonderOfUMixin {

    @Redirect(
        method = "applyCalamity",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/random/RandomGenerator;getDefault()Ljava/util/random/RandomGenerator;"
        ),
        remap = false,
        require = 0
    )
    private static RandomGenerator tensuraTno$useThreadLocalRandom() {
        return ThreadLocalRandom.current();
    }
}

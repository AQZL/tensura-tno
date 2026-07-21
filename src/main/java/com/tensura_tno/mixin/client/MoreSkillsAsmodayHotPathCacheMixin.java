package com.tensura_tno.mixin.client;

import com.tensura_tno.client.compat.MoreSkillsAsmodayHotPathCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Removes repeated exact trigonometry from Asmoday's two hottest meshes. */
@Pseudo
@Mixin(targets = "com.github.wal_bos.moreskills.client.fx.AsmodayBlackHoleClient", remap = false)
public abstract class MoreSkillsAsmodayHotPathCacheMixin {
    @Redirect(
            method = {"ribbonVertex", "yDiskVertex"},
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Math;cos(D)D",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static double tensuraTno$cachedCos(double angle) {
        return MoreSkillsAsmodayHotPathCache.cos(angle);
    }

    @Redirect(
            method = {"ribbonVertex", "yDiskVertex"},
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Math;sin(D)D",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static double tensuraTno$cachedSin(double angle) {
        return MoreSkillsAsmodayHotPathCache.sin(angle);
    }

}

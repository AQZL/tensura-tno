package com.tensura_tno.mixin.client;

import com.tensura_tno.client.compat.MoreSkillsElfariaVfxOptimizer;
import java.util.Iterator;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Filters only the per-frame iterators; MoreSkills keeps every effect instance
 * and its original lifetime, while off-screen and excessive duplicates are not drawn.
 */
@Pseudo
@Mixin(
        targets = {
                "com.github.wal_bos.moreskills.client.fx.ElfariaMyrdasFridolieteVfxClient",
                "com.github.wal_bos.moreskills.client.fx.ElfariaFruzelCardeneiaVfxClient",
                "com.github.wal_bos.moreskills.client.fx.ElfariaGlaciaLastAlbisVfxClient",
                "com.github.wal_bos.moreskills.client.fx.ElfariaStellasNateaVfxClient",
                "com.github.wal_bos.moreskills.client.fx.ElfariaPassiveIceStepsVfxClient",
                "com.github.wal_bos.moreskills.client.fx.ElfariaArsWeissBloomVfxClient",
                "com.github.wal_bos.moreskills.client.fx.ElfariaOpIceShineVfxClient"
        },
        remap = false
)
public abstract class MoreSkillsElfariaVfxIteratorMixin {

    @Redirect(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;isEmpty()Z",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static boolean tensuraTno$skipRendererWhenNothingIsVisible(List<?> effects) {
        return MoreSkillsElfariaVfxOptimizer.isEffectivelyEmpty(effects);
    }

    @Redirect(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;iterator()Ljava/util/Iterator;",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static Iterator<?> tensuraTno$iterateVisibleEffects(List<?> effects) {
        return MoreSkillsElfariaVfxOptimizer.visibleIterator(effects);
    }
}

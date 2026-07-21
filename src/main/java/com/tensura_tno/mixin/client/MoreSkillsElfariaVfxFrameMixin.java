package com.tensura_tno.mixin.client;

import com.tensura_tno.client.compat.MoreSkillsElfariaVfxOptimizer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplies the camera/frustum context used by the optional Elfaria VFX optimizations. */
@Pseudo
@Mixin(
        targets = "com.github.wal_bos.moreskills.client.fx.ElfariaIcemaidenVfxClient",
        remap = false
)
public abstract class MoreSkillsElfariaVfxFrameMixin {

    @Inject(
            method = "render(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private static void tensuraTno$beginElfariaFrame(RenderLevelStageEvent event, CallbackInfo ci) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            MoreSkillsElfariaVfxOptimizer.beginFrame(event);
        }
    }

    @Inject(
            method = "render(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private static void tensuraTno$endElfariaFrame(RenderLevelStageEvent event, CallbackInfo ci) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            MoreSkillsElfariaVfxOptimizer.endFrame();
        }
    }

    @Inject(
            method = "onClientTick(Lnet/neoforged/neoforge/client/event/ClientTickEvent$Post;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private static void tensuraTno$clearOldWorldEffects(ClientTickEvent.Post event, CallbackInfo ci) {
        MoreSkillsElfariaVfxOptimizer.observeClientLevel();
    }
}

package com.tensura_tno.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensura_tno.client.compat.MoreSkillsAsmodayFrustumCulling;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips a black-hole instance only when all of its geometry is outside the current view frustum. */
@Pseudo
@Mixin(targets = "com.github.wal_bos.moreskills.client.fx.AsmodayBlackHoleClient", remap = false)
public abstract class MoreSkillsAsmodayBlackHoleFrustumCullingMixin {
    @Inject(
            method = "render(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private static void tensuraTno$beginBlackHoleFrame(RenderLevelStageEvent event, CallbackInfo ci) {
        MoreSkillsAsmodayFrustumCulling.beginFrame(event);
    }

    @Inject(
            method = "render(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private static void tensuraTno$endBlackHoleFrame(RenderLevelStageEvent event, CallbackInfo ci) {
        MoreSkillsAsmodayFrustumCulling.endFrame();
    }

    @Inject(
            method = "renderHole(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/Camera;Lcom/github/wal_bos/moreskills/client/fx/AsmodayBlackHoleClient$ClientBlackHole;F)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void tensuraTno$cullFullyHiddenBlackHole(
            PoseStack poseStack,
            Vec3 cameraPosition,
            Camera camera,
            @Coerce Object hole,
            float partialTick,
            CallbackInfo ci
    ) {
        if (MoreSkillsAsmodayFrustumCulling.shouldCull(hole)) {
            ci.cancel();
        }
    }
}

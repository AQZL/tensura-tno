package com.tensura_tno.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensura_tno.client.race.PlayerKumodesuRenderManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "top.theillusivec4.curios.client.render.CuriosLayer", remap = false)
public abstract class CuriosKumodesuRenderSuppressMixin {

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void tensuraTno$skipCuriosOnKumodesuModel(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {
        if (entity instanceof Player player && PlayerKumodesuRenderManager.shouldRenderAsSpider(player)) {
            ci.cancel();
        }
    }
}

package com.tensura_tno.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensura_tno.client.race.PlayerKumodesuRenderManager;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemInHandRenderer.class, priority = 900)
public abstract class KumodesuFirstPersonItemMixin {

    @Shadow @Final private ItemRenderer itemRenderer;

    @Redirect(
        method = "renderArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
        ),
        require = 0
    )
    private void tensuraTno$renderKumodesuFirstPersonItem(
            ItemInHandRenderer renderer,
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        if (!stack.isEmpty() && PlayerKumodesuRenderManager.shouldBypassKumodesuFirstPersonItemCancel(entity)) {
            this.itemRenderer.renderStatic(
                    entity,
                    stack,
                    displayContext,
                    leftHand,
                    poseStack,
                    bufferSource,
                    entity.level(),
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    entity.getId() + displayContext.ordinal()
            );
            return;
        }

        renderer.renderItem(entity, stack, displayContext, leftHand, poseStack, bufferSource, packedLight);
    }
}

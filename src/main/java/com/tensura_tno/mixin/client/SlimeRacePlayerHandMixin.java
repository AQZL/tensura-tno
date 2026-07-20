package com.tensura_tno.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensura_tno.client.race.PlayerKumodesuRenderManager;
import com.tensura_tno.client.race.PlayerLizardmanRenderManager;
import com.tensura_tno.client.race.PlayerOrcRenderManager;
import com.tensura_tno.client.race.PlayerFoxSpiritRenderManager;
import com.tensura_tno.client.race.PlayerSlimeRenderManager;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 史莱姆种族家族玩家在第一人称看自己手部时，把手臂 / 袖子贴图替换为
 * 我们专属的 {@code tensura_tno:textures/entity/slime_player_arm.png}。
 *
 * <p>实现方式参考 woodwalkers 的 PlayerEntityRendererMixin#onRenderArm：
 * 拦截 {@code PlayerRenderer.renderHand} HEAD，使用传入的 {@code arm} 和
 * {@code sleeve} ModelPart 配合我们的贴图重新渲染，然后 {@code ci.cancel()}
 * 跳过原版渲染。
 *
 * <p>非史莱姆种族玩家：不拦截，走原版手部渲染（保留玩家自己的皮肤）。
 *
 * <p>方法签名 {@code renderHand(...)} 来源：1.21 NeoForge {@code PlayerRenderer}
 * 的私有方法，参数顺序 (PoseStack, MultiBufferSource, light, player, arm, sleeve)。
 */
@Mixin(value = PlayerRenderer.class, priority = 900)
public abstract class SlimeRacePlayerHandMixin {

    private static final ResourceLocation SLIME_ARM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tensura_tno", "textures/entity/slime_player_arm.png");
    private static final ResourceLocation LIZARDMAN_ARM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tensura_tno", "textures/entity/lizardman_player_arm.png");
    private static final ResourceLocation SPIDER_ARM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tensura_tno", "textures/entity/spider_player_arm.png");
    private static final ResourceLocation ORC_ARM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tensura_tno", "textures/entity/orc_player_arm.png");
    private static final ResourceLocation FOX_SPIRIT_ARM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tensura_tno", "textures/entity/fox_spirit_player_arm.png");

    @Inject(
        method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void tensuraTno$replaceArmTexture(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            AbstractClientPlayer player,
            ModelPart arm,
            ModelPart sleeve,
            CallbackInfo ci) {

        boolean slime = PlayerSlimeRenderManager.shouldRenderAsSlime(player);
        boolean lizardman = PlayerLizardmanRenderManager.shouldRenderAsLizardman(player);
        boolean spider = PlayerKumodesuRenderManager.shouldRenderAsSpider(player);
        boolean orc = PlayerOrcRenderManager.shouldRenderAsOrc(player);
        boolean foxSpirit = PlayerFoxSpiritRenderManager.shouldRenderAsFoxSpirit(player);
        if (!slime && !lizardman && !spider && !orc && !foxSpirit) return;

        // 用我们的史莱姆手臂贴图重画 arm 和 sleeve（保留原 ModelPart 几何，只换贴图）
        // entityTranslucent 与主模组 SlimeModel 一致，让手臂呈现半透明胶质感。
        // tint = 0xC0FFFFFF（白色 75% alpha）让贴图整体看起来更浅、更透。
        ResourceLocation texture = slime
                ? SLIME_ARM_TEXTURE
                : spider ? SPIDER_ARM_TEXTURE
                : orc ? ORC_ARM_TEXTURE
                : foxSpirit ? FOX_SPIRIT_ARM_TEXTURE
                : LIZARDMAN_ARM_TEXTURE;
        RenderType renderType = slime
                ? RenderType.entityTranslucent(texture)
                : RenderType.entityCutoutNoCull(texture);
        int tintColor = slime ? 0xC0FFFFFF : 0xFFFFFFFF;
        var consumer = bufferSource.getBuffer(renderType);

        boolean armVisible = arm.visible;
        boolean sleeveVisible = sleeve.visible;
        arm.visible = true;
        sleeve.visible = true;

        try {
            arm.xRot = 0.0F;
            arm.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, tintColor);

            sleeve.xRot = 0.0F;
            sleeve.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, tintColor);
        } finally {
            arm.visible = armVisible;
            sleeve.visible = sleeveVisible;
        }

        ci.cancel();  // 跳过原版手部渲染
    }
}

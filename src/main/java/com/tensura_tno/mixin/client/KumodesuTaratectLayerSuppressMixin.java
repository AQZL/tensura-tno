package com.tensura_tno.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensura_tno.ability.skill.HumanFormSkill;
import com.tensura_tno.client.race.PlayerKumodesuRenderManager;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 与 Kumodesu 模组兼容：屏蔽 {@code TaratectLayer} 在玩家身上叠加蜘蛛 / Taratect
 * 实体模型的渲染。当玩家激活人形状态时，本 mixin 在 layer 的 render 入口直接 cancel。
 *
 * <p>用 {@link Pseudo @Pseudo} 注解 + {@code targets} 字符串：Kumodesu 不在
 * 编译期依赖里，只有用户安装该模组时这个 mixin 才会找到目标，否则 {@code require = 0}
 * 让 Mixin 框架在缺失目标时安静跳过。
 */
@Pseudo
@Mixin(targets = "com.shin.tensura_kumodesu.client.renderer.layers.TaratectLayer", remap = false)
public abstract class KumodesuTaratectLayerSuppressMixin extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private KumodesuTaratectLayerSuppressMixin() {
        super(null);
    }

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void tensuraTno$skipTaratectOverlay(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {
        if (player == null) return;
        if (player.isSpectator() && PlayerKumodesuRenderManager.isSpiderRace(player)) {
            ci.cancel();
            return;
        }
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null) return;
        if (scale.getModifier(HumanFormSkill.HUMAN_FORM_SCALE) != null) {
            ci.cancel();
        }
    }
}

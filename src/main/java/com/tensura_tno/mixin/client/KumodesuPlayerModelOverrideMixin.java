package com.tensura_tno.mixin.client;

import com.tensura_tno.ability.skill.HumanFormSkill;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 与 Kumodesu 模组兼容：当玩家激活了人形状态（{@link HumanFormSkill}）时，
 * 强制把 {@link PlayerModel} 的所有部位恢复可见，覆盖掉 Kumodesu 的
 * {@code PlayerRenderMixin#renderConditional} 给 Arachne / SmallLesserTaratect
 * 等种族隐藏腿部 / 全身的处理。
 *
 * <p>判定条件：玩家身上存在我们的 SCALE 修饰器
 * {@code tensura_tno:human_form_scale} —— 这是按下人形状态技能后注入的标记。
 *
 * <p>本 mixin 的 priority 大于 Kumodesu 的默认值（1000），确保我们的逻辑在
 * 它之后跑，把它隐藏的部位再设回 visible。
 */
@Mixin(value = PlayerRenderer.class, priority = 1500)
public abstract class KumodesuPlayerModelOverrideMixin {

    @Inject(
        method = "setModelProperties(Lnet/minecraft/client/player/AbstractClientPlayer;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void tensuraTno$forceHumanModel(AbstractClientPlayer player, CallbackInfo ci) {
        if (player == null) return;
        if (!isHumanFormActive(player)) return;
        PlayerModel<AbstractClientPlayer> model =
                ((PlayerRenderer) (Object) this).getModel();
        // 把 Kumodesu 隐藏的腿 / 裤子 / 全身重新显示
        model.setAllVisible(true);
    }

    private static boolean isHumanFormActive(AbstractClientPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null) return false;
        return scale.getModifier(HumanFormSkill.HUMAN_FORM_SCALE) != null;
    }
}

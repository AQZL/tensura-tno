package com.tensura_tno.mixin.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Localizes SkillReward's soul-mismatch message:
 *   "X's soul doesn't match the unique skill Y, so it can not be obtained"
 * The component is built as sibling[0]=entity name, sibling[2]=skill name.
 */
@Pseudo
@Mixin(targets = "com.trbeyond.quest.reward.SkillReward", remap = false)
public class BeyondSkillRewardLocaleMixin {

    @Redirect(
        method = "award",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"),
        remap = false, require = 0
    )
    private void tensura_tno$localizeSkillMismatch(Player player, Component original) {
        List<Component> siblings = original.getSiblings();
        if (siblings.size() >= 3) {
            player.sendSystemMessage(
                Component.translatable("tensura_tno.beyond.skill.soul_mismatch",
                        siblings.get(0), siblings.get(2))
                        .withStyle(ChatFormatting.RED)
            );
        } else {
            player.sendSystemMessage(original);
        }
    }
}

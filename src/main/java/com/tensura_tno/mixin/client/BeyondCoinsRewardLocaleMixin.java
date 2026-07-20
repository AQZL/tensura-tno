package com.tensura_tno.mixin.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Localizes CoinsReward's hardcoded "§aReceived Reward: N Coins" system message.
 */
@Pseudo
@Mixin(targets = "com.trbeyond.quest.reward.CoinsReward", remap = false)
public class BeyondCoinsRewardLocaleMixin {

    @Shadow(remap = false)
    private int amount;

    @Redirect(
        method = "award",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"),
        remap = false, require = 0
    )
    private void tensura_tno$localizeRewardCoins(ServerPlayer player, Component original) {
        player.sendSystemMessage(
            Component.translatable("tensura_tno.beyond.reward.received_coins", String.valueOf(this.amount))
                    .withStyle(ChatFormatting.GREEN)
        );
    }
}

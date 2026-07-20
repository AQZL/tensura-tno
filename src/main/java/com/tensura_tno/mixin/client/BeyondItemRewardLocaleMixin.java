package com.tensura_tno.mixin.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Localizes ItemReward's hardcoded "§aReceived Item: " prefix.
 * The .append(stack.getDisplayName()) still runs on the returned translatable,
 * so the item name is preserved.
 */
@Pseudo
@Mixin(targets = "com.trbeyond.quest.reward.ItemReward", remap = false)
public class BeyondItemRewardLocaleMixin {

    @Redirect(
        method = "award",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
        remap = false, require = 0
    )
    private MutableComponent tensura_tno$localizeReceivedItemPrefix(String ignored) {
        return Component.translatable("tensura_tno.beyond.reward.received_item")
                .withStyle(ChatFormatting.GREEN);
    }
}

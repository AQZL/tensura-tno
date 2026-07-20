package com.tensura_tno.mixin.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Localizes TryBuyC2SPacket's hardcoded system message:
 *   ordinal 0: "Max copies reached for X. Converted to N souls."
 * Lives in the synthetic lambda method lambda$processBuy$1.
 * Extracts the hero name and soul count from the original text by substring.
 */
@Pseudo
@Mixin(targets = "com.trbeyond.network.packets.c2s.TryBuyC2SPacket", remap = false)
public class BeyondTryBuyLocaleMixin {

    private static final String PREFIX        = "Max copies reached for ";
    private static final String CONVERTED_TO  = ". Converted to ";
    private static final String SOULS_SUFFIX  = " souls.";

    @Redirect(
        method = "lambda$processBuy$1",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            ordinal = 0),
        remap = false, require = 0
    )
    private static void tensura_tno$localizeMaxCopies(ServerPlayer player, Component original) {
        String text = original.getString();
        int nameEnd    = text.indexOf(CONVERTED_TO);
        int soulsStart = nameEnd > 0 ? nameEnd + CONVERTED_TO.length() : -1;
        int soulsEnd   = text.lastIndexOf(SOULS_SUFFIX);
        if (nameEnd > PREFIX.length() && soulsEnd > soulsStart) {
            String heroName   = text.substring(PREFIX.length(), nameEnd);
            String soulsCount = text.substring(soulsStart, soulsEnd);
            player.sendSystemMessage(
                Component.translatable("tensura_tno.beyond.gacha.max_copies_converted", heroName, soulsCount)
                        .withStyle(ChatFormatting.GOLD)
            );
        } else {
            player.sendSystemMessage(original);
        }
    }
}

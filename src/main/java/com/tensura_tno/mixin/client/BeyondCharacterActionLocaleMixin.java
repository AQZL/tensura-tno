package com.tensura_tno.mixin.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Localizes CharacterActionC2SPacket's hardcoded system messages:
 *   - ordinal 0: "Maximum deployment limit reached."  (switch case DEPLOY)
 *   - ordinal 1: "Not enough souls to evolve"         (switch case EVOLVE, else branch)
 * Both live in the synthetic lambda method lambda$handle$3.
 * "Evolving with souls..." is inside a nested thenRun lambda and is skipped.
 */
@Pseudo
@Mixin(targets = "com.trbeyond.network.packets.c2s.CharacterActionC2SPacket", remap = false)
public class BeyondCharacterActionLocaleMixin {

    @Redirect(
        method = "lambda$handle$3",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            ordinal = 0),
        remap = false, require = 0
    )
    private static void tensura_tno$localizeDeployLimit(ServerPlayer player, Component original) {
        player.sendSystemMessage(Component.translatable("tensura_tno.beyond.character.deploy_limit"));
    }

    @Redirect(
        method = "lambda$handle$3",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            ordinal = 1),
        remap = false, require = 0
    )
    private static void tensura_tno$localizeNotEnoughSouls(ServerPlayer player, Component original) {
        player.sendSystemMessage(
            Component.translatable("tensura_tno.beyond.character.not_enough_souls")
                    .withStyle(ChatFormatting.RED)
        );
    }
}

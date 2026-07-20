package com.tensura_tno.mixin.client;

import com.tensura_tno.race.fox_spirit.SpiritSummonEntityHelper;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allows an owner to open Tensura's naming menu for any Spirit Summon. */
@Mixin(targets = "io.github.manasmods.tensura.network.c2s.RequestNamingKeyPacket", remap = false)
public abstract class SpiritSummonNamingKeyMixin {
    @Inject(method = "canName", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tno$allowOwnedSpiritSummon(Player player, LivingEntity summon,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (!SpiritSummonEntityHelper.isSpiritSummonOf(summon, player)) return;
        if (!summon.isAlive()) {
            cir.setReturnValue(false);
            return;
        }
        if (TensuraStorages.getExistenceFrom(summon).getName() != null) {
            player.displayClientMessage(Component.translatable("tensura.naming.already_named")
                    .withStyle(ChatFormatting.RED), false);
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(true);
    }
}

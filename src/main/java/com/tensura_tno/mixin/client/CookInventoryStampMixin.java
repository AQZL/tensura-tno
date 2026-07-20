package com.tensura_tno.mixin.client;

import com.tensura_tno.food.CookStamp;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stamps food with cook EP data the instant it enters a cook-player's inventory.
 * No polling, no delay — fires on every setItem/add call at the Inventory level.
 */
@Mixin(Inventory.class)
public class CookInventoryStampMixin {

    @Shadow @Final public Player player;

    @Inject(method = "setItem", at = @At("HEAD"))
    private void tensuraTno$stampOnSet(int slot, ItemStack stack, CallbackInfo ci) {
        if (stack.isEmpty()) return;
        if (player.level().isClientSide()) return;
        CookStamp.tryStamp(stack, player);
    }
}

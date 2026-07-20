package com.tensura_tno.mixin.client;

import io.github.manasmods.tensura.registry.item.misc.TensuraDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复背包锻造台（Smithing Table）升级后 Tensura EP 数据没有重置的问题。
 *
 * <p>与 BackpackTierUpgradeEPResetMixin 原理相同，针对的是
 * SmithingBackpackUpgradeRecipe（如原版 SB 的烈焰背包等锻造路径）。
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.crafting.SmithingBackpackUpgradeRecipe", remap = false)
public class SmithingBackpackTierUpgradeEPResetMixin {

    @Inject(method = "assemble", at = @At("RETURN"), remap = false, require = 0)
    private void tensura_tno$resetEPOnSmithingTierUpgrade(
            SmithingRecipeInput inv,
            HolderLookup.Provider registryAccess,
            CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }
        result.remove(TensuraDataComponents.EP.get());
        result.remove(TensuraDataComponents.MAX_EP.get());
        result.remove(TensuraDataComponents.EP_DURABILITY.get());
        result.remove(TensuraDataComponents.EP_GAIN.get());
        result.remove(TensuraDataComponents.EVOLUTION.get());
        result.remove(TensuraDataComponents.UNIQUE_EVOLUTIONS.get());
    }
}

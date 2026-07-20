package com.tensura_tno.mixin.client;

import io.github.manasmods.tensura.registry.item.misc.TensuraDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复背包工作台合成升级后 Tensura EP 数据没有重置的问题。
 *
 * <p>BackpackUpgradeRecipe 在升级时会把旧背包的所有 DataComponent（包括
 * Tensura 的 EP 组件）直接复制到新背包。这会导致 GearHandler.initiateGearExistence
 * 因检测到 MAX_EP 已存在而跳过初始化，新等级背包永远保持旧等级的 EP 配置。
 *
 * <p>修复方案：在每次合成升级完成后，移除新背包上的所有 Tensura 装备 EP 组件，
 * 让 initiateGearExistence 在背包首次被装备时按新等级正确初始化。
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.crafting.BackpackUpgradeRecipe", remap = false)
public class BackpackTierUpgradeEPResetMixin {

    @Inject(method = "assemble", at = @At("RETURN"), remap = false, require = 0)
    private void tensura_tno$resetEPOnTierUpgrade(
            CraftingInput inv,
            HolderLookup.Provider registries,
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

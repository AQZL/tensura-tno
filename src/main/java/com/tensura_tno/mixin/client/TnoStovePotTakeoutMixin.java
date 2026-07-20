package com.tensura_tno.mixin.client;

import com.tensura_tno.food.TnoStoveCookeryBonus;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity", remap = false)
public abstract class TnoStovePotTakeoutMixin {
    @Redirect(
            method = {
                    "takeOutProduct",
                    "takeOutWithoutCarrier",
                    "takeOutWithCarrier"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/ysbbbbbb/kaleidoscopecookery/util/ItemUtils;getItemToLivingEntity(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void tensuraTno$giveTnoStovePotResult(LivingEntity entity, ItemStack stack) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        TnoStoveCookeryBonus.giveCookeryResult(blockEntity.getLevel(), blockEntity.getBlockPos(), entity, stack);
    }
}

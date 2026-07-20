package com.tensura_tno.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class EnchantmentStoneItem extends Item {

    public EnchantmentStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag flag) {
        tooltipComponents.add(Component.translatable("item.tensura_tno.enchantment_stone.tooltip"));
    }
}

package com.tensura_tno.client.race;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;

final class PlayerRaceArmorRenderCompatibility {

    private PlayerRaceArmorRenderCompatibility() {}

    static ItemStack getRenderableArmor(@Nullable Player player, EquipmentSlot slot) {
        if (player == null) return ItemStack.EMPTY;

        ItemStack stack = player.getItemBySlot(slot);
        return canRenderOnRaceModel(stack) ? stack : ItemStack.EMPTY;
    }

    static boolean hasRenderableArmor(@Nullable Player player, EquipmentSlot slot) {
        return !getRenderableArmor(player, slot).isEmpty();
    }

    private static boolean canRenderOnRaceModel(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) return false;

        // Gecko/custom armor models are authored for humanoid players and clip badly
        // when forced onto race entity bones.
        return !(stack.getItem() instanceof GeoItem);
    }
}

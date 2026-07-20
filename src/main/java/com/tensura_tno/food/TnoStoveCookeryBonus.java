package com.tensura_tno.food;

import com.tensura_tno.registry.TensuraTNOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class TnoStoveCookeryBonus {
    private static final double EFFECT_SCALE = 0.7;

    private TnoStoveCookeryBonus() {}

    public static void giveCookeryResult(Level level, BlockPos cookwarePos, LivingEntity entity, ItemStack stack) {
        ItemStack result = stack;
        if (isTnoStoveHeatSource(level, cookwarePos)) {
            result = stack.copy();
            // 魔素灶台走"灶台版"戳记：除了精通度基础 multiplier 之外，
            // 持有 Cook 的玩家会再叠加一个 +1%~+5% 的随机额外 bonus。
            CookStamp.tryStampScaledWithStoveBonus(result, entity, EFFECT_SCALE);
        }
        giveItemToLivingEntity(entity, result);
    }

    private static boolean isTnoStoveHeatSource(Level level, BlockPos cookwarePos) {
        if (level == null || cookwarePos == null) {
            return false;
        }
        BlockState belowState = level.getBlockState(cookwarePos.below());
        return belowState.is(TensuraTNOBlocks.TNO_STOVE.get())
                && belowState.hasProperty(BlockStateProperties.LIT)
                && belowState.getValue(BlockStateProperties.LIT);
    }

    private static void giveItemToLivingEntity(LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (entity.getMainHandItem().isEmpty()) {
            RandomSource random = entity.level().random;
            entity.setItemInHand(InteractionHand.MAIN_HAND, stack);
            entity.playSound(SoundEvents.ITEM_PICKUP, 0.2F, ((random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
        } else if (entity instanceof Player player) {
            ItemHandlerHelper.giveItemToPlayer(player, stack);
        } else {
            ItemEntity dropItem = entity.spawnAtLocation(stack);
            if (dropItem != null) {
                dropItem.setPickUpDelay(0);
            }
        }
    }
}

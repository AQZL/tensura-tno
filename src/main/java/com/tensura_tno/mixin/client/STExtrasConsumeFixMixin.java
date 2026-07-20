package com.tensura_tno.mixin.client;

import com.tensura_tno.ftb.STExtrasHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 STExtras CONSUME 委托追踪 bug（count = 0 导致进度永远无法增加）。
 *
 * <p><b>Bug 根因</b>：STExtras 在 {@code ItemStack.finishUsingItem} 的 TAIL 处触发
 * {@code STPlayerEvents.fireConsume}，此时 {@code ItemStack.consume()} 已将物品 count
 * 减为 0，导致监听器中 {@code food.getCount() = 0}，进度 delta = 0，
 * 形如"饮用赞恩血"（A Light Snack）等 CONSUME 类型委托永远无法完成。</p>
 *
 * <p><b>修复方式</b>：在 HEAD（物品消耗之前）抢先触发一次 CONSUME 事件，
 * 此时 count 仍为 1，进度可正确递增。
 * STExtras 随后在 TAIL 触发的第二次事件 count = 0，delta = 0，
 * 而此时委托已被移出 active 列表，监听器找不到匹配的活跃委托，不会产生双重完成。</p>
 */
@Mixin(ItemStack.class)
public class STExtrasConsumeFixMixin {

    @Inject(
        method = "finishUsingItem",
        at = @At("HEAD")
    )
    private void stno$fireConsumeBeforeItemConsumed(
            Level level, LivingEntity entity,
            CallbackInfoReturnable<ItemStack> cir) {
        if (level.isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;

        ItemStack stack = (ItemStack) (Object) this;
        if (stack.isEmpty()) return;

        STExtrasHelper.fireConsumeEarly(player, stack.copy());
    }
}

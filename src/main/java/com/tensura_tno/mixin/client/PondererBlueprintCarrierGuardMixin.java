package com.tensura_tno.mixin.client;

import com.tensura_tno.util.PondererPaperFeatureAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Ponderer 的"圈结构 / 蓝图选区"功能在
 * {@link PondererPaperFeatureAccess#isEnabled()} 关闭时彻底失活。
 *
 * <p>原版行为: {@code BlueprintFeature.matchesCarrierStack(stack)} 把当前主手物品
 * 与 {@code Config.BLUEPRINT_CARRIER_ITEM} (默认 {@code minecraft:paper}) 比对,
 * 匹配则认为是圈结构工具, {@code BlueprintHandler} 每帧都用这个判断来决定是否激活
 * 选区/缩放/右键等行为, 表现为"拿张纸右键就进入圈结构模式"。</p>
 *
 * <p>本 mixin 在该方法 HEAD 加守卫:
 * <ul>
 *   <li>access 已开启 → 走原逻辑, 完全不干预。</li>
 *   <li>access 关闭, 但当前手持的 stack 是 {@code ponderer:blueprint}
 *       (服务器启用了 ponderer 自家物品) → 也不干预。这是 ponderer 自家蓝图,
 *       不应被本开关一并屏蔽。</li>
 *   <li>其他情况 (典型: 拿着 paper, access 又默认关闭) → 直接返回 false,
 *       原方法不再执行, 圈结构功能瞬间失活。</li>
 * </ul>
 *
 * <p>切换 access 后立即生效, 无需 reload。</p>
 *
 * <p>{@code targets = "..."} + {@code require = 0}: ponderer 没装时静默跳过。</p>
 */
@Mixin(
    targets = "com.nododiiiii.ponderer.blueprint.BlueprintFeature",
    remap = false
)
public abstract class PondererBlueprintCarrierGuardMixin {

    @Inject(
        method = "matchesCarrierStack(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void tensuraTno$gateByAccess(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (PondererPaperFeatureAccess.isEnabled()) {
            return;
        }
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (item == null) {
            cir.setReturnValue(false);
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null && "ponderer".equals(id.getNamespace()) && "blueprint".equals(id.getPath())) {
            return;
        }
        cir.setReturnValue(false);
    }
}

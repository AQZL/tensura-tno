package com.tensura_tno.recipe;

import io.github.manasmods.tensura.registry.item.misc.TensuraDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.level.Level;
import com.tensura_tno.registry.TensuraTNORecipeSerializers;

/**
 * 下界合金→下级魔钓升级配方。
 * 继承下界合金的附魔，但强制设置固定 EP/maxEP/gain/evolution，
 * 不继承下界合金装备原有的 Tensura 存在值组件。
 */
public class NetheriteToMagisteel extends SmithingTransformRecipe {

    // 额外存储一份供序列化器访问（父类字段为包私有，子包无法直接读取）
    final Ingredient myTemplate;
    final Ingredient myBase;
    final Ingredient myAddition;
    final ItemStack myResult;
    final double minEp;
    final double maxEp;
    final double gain;
    final ResourceLocation evolution;

    public NetheriteToMagisteel(Ingredient template, Ingredient base,
                                Ingredient addition, ItemStack result,
                                double minEp, double maxEp, double gain, ResourceLocation evolution) {
        super(template, base, addition, result);
        this.myTemplate = template;
        this.myBase = base;
        this.myAddition = addition;
        this.myResult = result;
        this.minEp = minEp;
        this.maxEp = maxEp;
        this.gain = gain;
        this.evolution = evolution;
    }

    /**
     * 覆写 matches()，对 base 槽位（武器/防具）只比较物品类型，
     * 忽略附魔等 DataComponent，使有附魔的下界合金装备同样可升级。
     */
    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        ItemStack base = input.getItem(1);
        boolean baseMatches = !base.isEmpty() && java.util.Arrays.stream(myBase.getItems())
                .anyMatch(rep -> base.is(rep.getItem()));
        return myTemplate.test(input.getItem(0)) && baseMatches && myAddition.test(input.getItem(2));
    }

    @Override
    @SuppressWarnings("unchecked")
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        // transmuteCopy 会把 base（下界合金）的所有 DataComponent 复制到 result，
        // 包括附魔。之后我们强制覆写 Tensura 存在值，确保不继承下界合金的旧 EP。
        ItemStack result = super.assemble(input, registries);
        result.set((DataComponentType<Double>) TensuraDataComponents.MAX_EP.get(), maxEp);
        result.set((DataComponentType<Double>) TensuraDataComponents.EP.get(), minEp);
        result.set((DataComponentType<Double>) TensuraDataComponents.EP_DURABILITY.get(), minEp);
        result.set((DataComponentType<Double>) TensuraDataComponents.EP_GAIN.get(), gain);
        result.set((DataComponentType<ResourceLocation>) TensuraDataComponents.EVOLUTION.get(), evolution);
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TensuraTNORecipeSerializers.NETHERITE_TO_MAGISTEEL.get();
    }
}

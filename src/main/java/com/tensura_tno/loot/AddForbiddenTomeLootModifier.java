package com.tensura_tno.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tensura_tno.registry.TensuraTNOItems;
import com.tensura_tno.registry.TensuraTNOLootModifiers;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Adds one {@code tensura_tno:forbidden_summon_tome} to matched loot tables
 * with the same rarity as Tensura's epic wizard-tower magic tome (weight 3/118 ≈ 2.5%
 * per pool roll).  Applied to all five wizard-tower chest loot tables.
 */
public class AddForbiddenTomeLootModifier extends LootModifier {

    public static final MapCodec<AddForbiddenTomeLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, AddForbiddenTomeLootModifier::new));

    public AddForbiddenTomeLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        generatedLoot.add(new ItemStack(TensuraTNOItems.FORBIDDEN_SUMMON_TOME.get()));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return TensuraTNOLootModifiers.ADD_FORBIDDEN_TOME.get();
    }
}

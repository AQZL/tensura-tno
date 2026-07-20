package com.tensura_tno.loot;

import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public class ReplaceIceAndFireSilverLootModifier extends LootModifier {

    public static final MapCodec<ReplaceIceAndFireSilverLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, ReplaceIceAndFireSilverLootModifier::new));

    private static final Map<ResourceLocation, ResourceLocation> REPLACEMENTS = Map.of(
            id("iceandfire", "silver_ingot"), id("tensura", "silver_ingot"),
            id("iceandfire", "silver_nugget"), id("tensura", "silver_nugget"),
            id("iceandfire", "raw_silver"), id("tensura", "raw_silver"),
            id("iceandfire", "silver_block"), id("tensura", "silver_block"),
            id("iceandfire", "raw_silver_block"), id("tensura", "raw_silver_block"),
            id("iceandfire", "silver_ore"), id("tensura", "silver_ore"),
            id("iceandfire", "deepslate_silver_ore"), id("tensura", "deepslate_silver_ore"));

    public ReplaceIceAndFireSilverLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        for (int i = 0; i < generatedLoot.size(); i++) {
            ItemStack stack = generatedLoot.get(i);
            ResourceLocation replacementId = REPLACEMENTS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));

            if (replacementId == null) {
                continue;
            }

            Item replacement = BuiltInRegistries.ITEM.get(replacementId);
            if (replacement == stack.getItem()) {
                continue;
            }

            generatedLoot.set(i, new ItemStack(replacement, stack.getCount()));
        }

        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}

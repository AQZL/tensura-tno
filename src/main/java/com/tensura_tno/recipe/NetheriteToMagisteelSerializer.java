package com.tensura_tno.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class NetheriteToMagisteelSerializer implements RecipeSerializer<NetheriteToMagisteel> {

    public static final MapCodec<NetheriteToMagisteel> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Ingredient.CODEC.fieldOf("template").forGetter(r -> r.myTemplate),
                    Ingredient.CODEC.fieldOf("base").forGetter(r -> r.myBase),
                    Ingredient.CODEC.fieldOf("addition").forGetter(r -> r.myAddition),
                    ItemStack.STRICT_SINGLE_ITEM_CODEC.fieldOf("result").forGetter(r -> r.myResult),
                    Codec.DOUBLE.fieldOf("min_ep").forGetter(r -> r.minEp),
                    Codec.DOUBLE.fieldOf("max_ep").forGetter(r -> r.maxEp),
                    Codec.DOUBLE.fieldOf("gain").forGetter(r -> r.gain),
                    ResourceLocation.CODEC.fieldOf("evolution").forGetter(r -> r.evolution)
            ).apply(instance, NetheriteToMagisteel::new));

    // StreamCodec.composite 最多支持 6 个字段；8 个字段改用匿名类手动编解码
    public static final StreamCodec<RegistryFriendlyByteBuf, NetheriteToMagisteel> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public NetheriteToMagisteel decode(RegistryFriendlyByteBuf buf) {
                    Ingredient template = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
                    Ingredient base = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
                    Ingredient addition = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
                    ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
                    double minEp = buf.readDouble();
                    double maxEp = buf.readDouble();
                    double gain = buf.readDouble();
                    ResourceLocation evolution = ResourceLocation.STREAM_CODEC.decode(buf);
                    return new NetheriteToMagisteel(template, base, addition, result, minEp, maxEp, gain, evolution);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, NetheriteToMagisteel value) {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, value.myTemplate);
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, value.myBase);
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, value.myAddition);
                    ItemStack.STREAM_CODEC.encode(buf, value.myResult);
                    buf.writeDouble(value.minEp);
                    buf.writeDouble(value.maxEp);
                    buf.writeDouble(value.gain);
                    ResourceLocation.STREAM_CODEC.encode(buf, value.evolution);
                }
            };

    @Override
    public MapCodec<NetheriteToMagisteel> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, NetheriteToMagisteel> streamCodec() {
        return STREAM_CODEC;
    }
}

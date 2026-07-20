package com.tensura_tno.registry;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.recipe.NetheriteToMagisteel;
import com.tensura_tno.recipe.NetheriteToMagisteelSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TensuraTNORecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, TensuraTNOMod.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, NetheriteToMagisteelSerializer> NETHERITE_TO_MAGISTEEL =
            SERIALIZERS.register("netherite_to_magisteel", NetheriteToMagisteelSerializer::new);

    private TensuraTNORecipeSerializers() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}

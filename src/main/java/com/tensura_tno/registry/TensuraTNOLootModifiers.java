package com.tensura_tno.registry;

import com.mojang.serialization.MapCodec;
import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.loot.AddForbiddenTomeLootModifier;
import com.tensura_tno.loot.ReplaceIceAndFireSilverLootModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class TensuraTNOLootModifiers {

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS, TensuraTNOMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AddForbiddenTomeLootModifier>>
            ADD_FORBIDDEN_TOME = LOOT_MODIFIER_SERIALIZERS.register(
                    "add_forbidden_tome",
                    () -> AddForbiddenTomeLootModifier.CODEC);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ReplaceIceAndFireSilverLootModifier>>
            REPLACE_ICEANDFIRE_SILVER = LOOT_MODIFIER_SERIALIZERS.register(
                    "replace_iceandfire_silver",
                    () -> ReplaceIceAndFireSilverLootModifier.CODEC);

    private TensuraTNOLootModifiers() {}

    public static void register(IEventBus modBus) {
        LOOT_MODIFIER_SERIALIZERS.register(modBus);
    }
}

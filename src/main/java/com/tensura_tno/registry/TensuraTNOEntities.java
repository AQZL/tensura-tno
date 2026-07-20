package com.tensura_tno.registry;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.entity.spirit.FoxSpiritEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体类型注册表。所有由本模组新增的实体在此集中登记。
 */
public final class TensuraTNOEntities {

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, TensuraTNOMod.MOD_ID);

    /** 契约小狐灵 —— "契约小狐"内在技能召唤的协助战斗单位。 */
    public static final DeferredHolder<EntityType<?>, EntityType<FoxSpiritEntity>> FOX_SPIRIT =
            ENTITIES.register("fox_spirit",
                    () -> EntityType.Builder.<FoxSpiritEntity>of(FoxSpiritEntity::new, MobCategory.MISC)
                            .sized(0.6F, 0.7F)
                            .clientTrackingRange(10)
                            .build(ResourceLocation.fromNamespaceAndPath(
                                    TensuraTNOMod.MOD_ID, "fox_spirit").toString()));

    private TensuraTNOEntities() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(TensuraTNOEntities::onAttributesCreate);
    }

    private static void onAttributesCreate(EntityAttributeCreationEvent event) {
        event.put(FOX_SPIRIT.get(), FoxSpiritEntity.createAttributes().build());
    }
}

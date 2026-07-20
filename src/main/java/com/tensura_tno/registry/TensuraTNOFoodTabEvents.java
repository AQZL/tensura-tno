package com.tensura_tno.registry;

import com.tensura_tno.TensuraTNOMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Adds TNO's Kaleidoscope-style foods to Kaleidoscope Cookery's food creative tab. */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TensuraTNOFoodTabEvents {

    private static final ResourceLocation COOKERY_FOOD_TAB =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "cookery_food");
    private static final ResourceLocation COOKERY_MAIN_TAB =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "cookery_main");

    private TensuraTNOFoodTabEvents() {}

    @SubscribeEvent
    public static void addFoodsToKaleidoscopeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(COOKERY_MAIN_TAB)) {
            event.accept(TensuraTNOItems.TNO_STOVE.get());
            return;
        }

        if (!event.getTabKey().location().equals(COOKERY_FOOD_TAB)) {
            return;
        }

        event.accept(TensuraTNOItems.STORM_DAEMON_HIPOKUTE_SOUP.get());
        event.accept(TensuraTNOItems.MONSTER_MEAT_PLATTER.get());
        event.accept(TensuraTNOItems.SLIME_SAUCE_BUN.get());
        event.accept(TensuraTNOItems.BLADE_TIGER_MINCED_MEAT.get());
        event.accept(TensuraTNOItems.COOKED_BLADE_TIGER_MINCED_MEAT.get());
        event.accept(TensuraTNOItems.CHARYBDIS_MINCED_MEAT.get());
        event.accept(TensuraTNOItems.COOKED_CHARYBDIS_MINCED_MEAT.get());
        event.accept(TensuraTNOItems.ARMORSAURUS_MINCED_MEAT.get());
        event.accept(TensuraTNOItems.COOKED_ARMORSAURUS_MINCED_MEAT.get());
        event.accept(TensuraTNOItems.KNIGHT_SPIDER_LEG_MEAT.get());
        event.accept(TensuraTNOItems.COOKED_KNIGHT_SPIDER_LEG_MEAT.get());
        event.accept(TensuraTNOItems.CURED_MEAT.get());
        event.accept(TensuraTNOItems.SLICED_CURED_MEAT.get());
    }
}

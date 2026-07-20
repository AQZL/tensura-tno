package com.tensura_tno.registry;

import com.tensura_tno.TensuraTNOMod;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TensuraTNOItemTabEvents {

    private TensuraTNOItemTabEvents() {}

    @SubscribeEvent
    public static void addItemsToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(TensuraTNOItems.REQUIRED_QUEST_RESET_SCROLL.get());
        }
    }
}

package com.tensura_tno.registry;

import com.tensura_tno.TensuraTNOMod;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TensuraTNOBackpackEvents {
    private TensuraTNOBackpackEvents() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Set<Block> validBlocks = new HashSet<>(ModBlocks.BACKPACK_TILE_TYPE.get().validBlocks);
            validBlocks.add(TensuraTNOBlocks.LOW_MAGISTEEL_BACKPACK.get());
            validBlocks.add(TensuraTNOBlocks.HIGH_MAGISTEEL_BACKPACK.get());
            validBlocks.add(TensuraTNOBlocks.PURE_MAGISTEEL_BACKPACK.get());
            validBlocks.add(TensuraTNOBlocks.ADAMANTITE_BACKPACK.get());
            validBlocks.add(TensuraTNOBlocks.HIHIIROKANE_BACKPACK.get());
            ModBlocks.BACKPACK_TILE_TYPE.get().validBlocks = validBlocks;
        });
    }

    @SubscribeEvent
    public static void addToSophisticatedBackpacksTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTab().equals(ModItems.CREATIVE_TAB.get())) {
            return;
        }

        event.accept(TensuraTNOBackpacks.LOW_MAGISTEEL_BACKPACK.get());
        event.accept(TensuraTNOBackpacks.HIGH_MAGISTEEL_BACKPACK.get());
        event.accept(TensuraTNOBackpacks.PURE_MAGISTEEL_BACKPACK.get());
        event.accept(TensuraTNOBackpacks.ADAMANTITE_BACKPACK.get());
        event.accept(TensuraTNOBackpacks.HIHIIROKANE_BACKPACK.get());
    }
}
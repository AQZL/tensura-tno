package com.tensura_tno.client;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.registry.TensuraTNOBackpacks;
import com.tensura_tno.registry.TensuraTNOBlocks;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.util.Lazy;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.client.render.BackpackItemStackRenderer;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;

@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TensuraTNOBackpackClientEvents {
    private TensuraTNOBackpackClientEvents() {
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private final Lazy<BlockEntityWithoutLevelRenderer> ister = Lazy.of(
                    () -> new BackpackItemStackRenderer(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels())
            );

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ister.get();
            }
        },
                TensuraTNOBackpacks.LOW_MAGISTEEL_BACKPACK.get(),
                TensuraTNOBackpacks.HIGH_MAGISTEEL_BACKPACK.get(),
                TensuraTNOBackpacks.PURE_MAGISTEEL_BACKPACK.get(),
                TensuraTNOBackpacks.ADAMANTITE_BACKPACK.get(),
                TensuraTNOBackpacks.HIHIIROKANE_BACKPACK.get());
    }

    @SubscribeEvent
    public static void registerItemColorHandlers(RegisterColorHandlersEvent.Item event) {
        event.register((backpack, layer) -> {
            if (layer > 1 || !(backpack.getItem() instanceof BackpackItem)) {
                return -1;
            }

            IBackpackWrapper backpackWrapper = BackpackWrapper.fromStack(backpack);
            if (layer == 0) {
                return backpackWrapper.getMainColor();
            }
            if (layer == 1) {
                return backpackWrapper.getAccentColor();
            }
            return -1;
        },
                TensuraTNOBackpacks.LOW_MAGISTEEL_BACKPACK.get(),
                TensuraTNOBackpacks.HIGH_MAGISTEEL_BACKPACK.get(),
                TensuraTNOBackpacks.PURE_MAGISTEEL_BACKPACK.get(),
                TensuraTNOBackpacks.ADAMANTITE_BACKPACK.get(),
                TensuraTNOBackpacks.HIHIIROKANE_BACKPACK.get());
    }

    @SubscribeEvent
    public static void registerBlockColorHandlers(RegisterColorHandlersEvent.Block event) {
        event.register((state, blockDisplayReader, pos, tintIndex) -> {
            if (tintIndex < 0 || tintIndex > 1 || pos == null) {
                return -1;
            }
            return WorldHelper.getBlockEntity(blockDisplayReader, pos, BackpackBlockEntity.class)
                    .map(be -> tintIndex == 0 ? be.getBackpackWrapper().getMainColor() : be.getBackpackWrapper().getAccentColor())
                    .orElse(tintIndex == 0 ? BackpackWrapper.DEFAULT_MAIN_COLOR : BackpackWrapper.DEFAULT_ACCENT_COLOR);
        },
                TensuraTNOBlocks.LOW_MAGISTEEL_BACKPACK.get(),
                TensuraTNOBlocks.HIGH_MAGISTEEL_BACKPACK.get(),
                TensuraTNOBlocks.PURE_MAGISTEEL_BACKPACK.get(),
                TensuraTNOBlocks.ADAMANTITE_BACKPACK.get(),
                TensuraTNOBlocks.HIHIIROKANE_BACKPACK.get());
    }
}
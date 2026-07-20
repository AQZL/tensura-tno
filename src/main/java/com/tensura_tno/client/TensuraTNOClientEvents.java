package com.tensura_tno.client;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.client.renderer.FoxSpiritRenderer;
import com.tensura_tno.registry.TensuraTNOEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 客户端 mod-bus 事件：把本模组的实体渲染器与 vanilla / Tensura 系统对接。
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TensuraTNOClientEvents {

    private TensuraTNOClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TensuraTNOEntities.FOX_SPIRIT.get(), FoxSpiritRenderer::new);
    }
}

package com.tensura_tno.client.browser;

import com.tensura_tno.TensuraTNOMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, value = Dist.CLIENT)
public final class WebView2BrowserCleanupEvents {
    private WebView2BrowserCleanupEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        WebView2BrowserSession.clientTickCleanup();
    }
}

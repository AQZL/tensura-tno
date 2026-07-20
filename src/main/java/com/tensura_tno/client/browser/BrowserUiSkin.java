package com.tensura_tno.client.browser;

import net.minecraft.resources.ResourceLocation;

public final class BrowserUiSkin {
    private static final ResourceLocation WIKI_ICON_OFF = ResourceLocation.fromNamespaceAndPath("tensura_tno", "textures/gui/wiki_off.png");
    private static final ResourceLocation WIKI_ICON_ON = ResourceLocation.fromNamespaceAndPath("tensura_tno", "textures/gui/wiki_on.png");
    private static final ResourceLocation NAV_BUTTON_BG = ResourceLocation.fromNamespaceAndPath("tensura_tno", "textures/gui/nav_button_bg.png");

    private BrowserUiSkin() {
    }

    public static ResourceLocation wikiIconOffTexture() {
        return WIKI_ICON_OFF;
    }

    public static ResourceLocation wikiIconOnTexture() {
        return WIKI_ICON_ON;
    }

    public static ResourceLocation navButtonBgTexture() {
        return NAV_BUTTON_BG;
    }
}
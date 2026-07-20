package com.tensura_tno.mixin.client;

import com.tensura_tno.client.browser.BrowserUiSkin;
import com.tensura_tno.client.browser.BrowserDisplayMode;
import com.tensura_tno.client.browser.FixedImageButton;
import com.tensura_tno.client.browser.TensuraScreenLayout;
import com.tensura_tno.client.browser.TensuraBrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
    "io.github.manasmods.tensura.client.screen.MainScreen",
    "io.github.manasmods.tensura.client.screen.AbilityCategoriesScreen",
    "io.github.manasmods.tensura.client.screen.AbilitySelectionScreen"
}, remap = false)
public abstract class TensuraBrowserButtonMixin extends Screen {
    @Unique
    private FixedImageButton tensuraTno$browserTabButton;

    protected TensuraBrowserButtonMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void tensuraTno$addBrowserButton(CallbackInfo ci) {
        if (BrowserDisplayMode.isFullscreenLike(Minecraft.getInstance())) {
            return;
        }

        int left = TensuraScreenLayout.readIntField(this, "guiLeft");
        int top = TensuraScreenLayout.readIntField(this, "guiTop");
        int buttonX = this.getClass().getName().endsWith("MainScreen") ? left + 176 : left + 202;

        this.tensuraTno$browserTabButton = this.addRenderableWidget(new FixedImageButton(
            buttonX,
            top + 2,
            24,
            21,
            BrowserUiSkin.wikiIconOffTexture(),
            BrowserUiSkin.wikiIconOnTexture(),
            button -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null) {
                    minecraft.setScreen(new TensuraBrowserScreen((Screen) (Object) this));
                }
            },
            Component.translatable("tensura_tno.wiki.title")
        ));
    }

}

package com.tensura_tno.client.browser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class SkinnedButton extends Button {
    public SkinnedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(BrowserUiSkin.navButtonBgTexture(), this.getX(), this.getY(), 0.0F, 0.0F, this.width, this.height, this.width, this.height);
        if (this.isHoveredOrFocused()) {
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x30FFFFFF);
        }

        int color = this.active ? 0xFFFFFF : 0xA0A0A0;
        graphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);
    }
}
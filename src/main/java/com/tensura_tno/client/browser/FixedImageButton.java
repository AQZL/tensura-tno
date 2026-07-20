package com.tensura_tno.client.browser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class FixedImageButton extends Button {
    private final ResourceLocation defaultTexture;
    private final ResourceLocation hoveredTexture;
    private final Component tooltip;

    public FixedImageButton(int x, int y, int width, int height, ResourceLocation texture, OnPress onPress, Component tooltip) {
        this(x, y, width, height, texture, texture, onPress, tooltip);
    }

    public FixedImageButton(int x, int y, int width, int height, ResourceLocation defaultTexture, ResourceLocation hoveredTexture, OnPress onPress, Component tooltip) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.defaultTexture = defaultTexture;
        this.hoveredTexture = hoveredTexture;
        this.tooltip = tooltip;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation texture = this.isHoveredOrFocused() ? this.hoveredTexture : this.defaultTexture;
        graphics.blit(texture, this.getX(), this.getY(), 0.0F, 0.0F, this.width, this.height, this.width, this.height);
        if (this.isHoveredOrFocused() && this.tooltip != null && Minecraft.getInstance().screen != null) {
            Minecraft.getInstance().screen.setTooltipForNextRenderPass(this.tooltip);
        }
    }
}
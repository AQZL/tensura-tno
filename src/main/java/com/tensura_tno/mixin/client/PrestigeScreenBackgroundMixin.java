package com.tensura_tno.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "org.crypticdev.stextras.client.screen.STPrestigeScreens", remap = false)
public class PrestigeScreenBackgroundMixin {

    private static final ResourceLocation CN_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "textures/gui/prestige_screen/st_prestige_gui_cn.png");
    private static final ResourceLocation CN_RACE_SECTION = ResourceLocation.fromNamespaceAndPath(
        "tensura_tno", "textures/gui/prestige_screen/race_prestige/race_prestige_section_cn.png");
    private static final ResourceLocation RACE_SECTION = ResourceLocation.fromNamespaceAndPath(
        "stextras", "textures/gui/prestige_screen/race_prestige/race_prestige_section.png");

    @Redirect(
            method = "renderBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"
            ),
            remap = false
    )
    private void redirectBackgroundBlit(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                        int u, int v, int width, int height) {
        graphics.blit(selectBackground(texture), x, y, u, v, width, height);
    }

    @Redirect(
            method = "renderRacePrestige",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"
            ),
            remap = false
        )
    private void redirectRaceSectionBlit(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                         float u, float v, int width, int height, int textureWidth,
                                         int textureHeight) {
        graphics.blit(selectRaceSection(texture), x, y, u, v, width, height, textureWidth, textureHeight);
    }

    private static ResourceLocation selectBackground(ResourceLocation fallback) {
        return isChineseMode() ? CN_BACKGROUND : fallback;
    }

    private static ResourceLocation selectRaceSection(ResourceLocation fallback) {
        if (isChineseMode() && RACE_SECTION.equals(fallback)) {
            return CN_RACE_SECTION;
        }
        return fallback;
    }

    private static boolean isChineseMode() {
        return Language.getInstance().has("stextras.quest.name.daily_novice_kill_slime");
    }
}

package com.tensura_tno.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "com.trbeyond.gui.tensura.MainScreen", remap = false)
public abstract class BeyondMainButtonLocalizationMixin {

    private static final ResourceLocation CN_CONTRACTS = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "textures/gui/beyond/buttons/contracts.png");
    private static final ResourceLocation CN_CONTRACTS_SEL = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "textures/gui/beyond/buttons/contracts_selected.png");
    private static final ResourceLocation CN_MISSIONS = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "textures/gui/beyond/buttons/missions.png");
    private static final ResourceLocation CN_MISSIONS_SEL = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "textures/gui/beyond/buttons/missions_selected.png");

    @Inject(method = "init", at = @At("RETURN"))
    private void tensura_tno$swapButtonTextures(CallbackInfo ci) {
        try {
            Screen screen = (Screen) (Object) this;
            boolean singleplayer = Minecraft.getInstance().isSingleplayer();
            List<GuiEventListener> toRemove = new ArrayList<>();

            for (GuiEventListener child : screen.children()) {
                String texturePath = getButtonTexturePath(child);
                if (texturePath == null) continue;

                if (singleplayer && (texturePath.contains("contracts") || texturePath.contains("rewards"))) {
                    if (child instanceof AbstractWidget w) w.visible = false;
                    toRemove.add(child);
                } else if (isLocalizationEnabled()) {
                    swapTextures(child, texturePath);
                }
            }

            for (GuiEventListener r : toRemove) {
                try {
                    java.lang.reflect.Method removeWidget = Screen.class.getDeclaredMethod("removeWidget", GuiEventListener.class);
                    removeWidget.setAccessible(true);
                    removeWidget.invoke(screen, r);
                } catch (Exception ignored2) {}
            }
        } catch (Exception ignored) {}
    }

    private static String getButtonTexturePath(Object widget) {
        try {
            Class<?> clazz = widget.getClass();
            if (!"com.trbeyond.gui.component.MainGuiButton".equals(clazz.getName())) return null;

            Field texturesField = clazz.getDeclaredField("textures");
            texturesField.setAccessible(true);
            Object textures = texturesField.get(widget);

            Field normalField = textures.getClass().getDeclaredField("normal");
            normalField.setAccessible(true);
            return ((ResourceLocation) normalField.get(textures)).getPath();
        } catch (Exception e) {
            return null;
        }
    }

    private static void swapTextures(Object widget, String path) {
        try {
            Class<?> clazz = widget.getClass();

            Field texturesField = clazz.getDeclaredField("textures");
            texturesField.setAccessible(true);
            Object textures = texturesField.get(widget);

            ResourceLocation newNormal = null, newSelected = null;
            if (path.contains("contracts")) {
                newNormal = CN_CONTRACTS;
                newSelected = CN_CONTRACTS_SEL;
            } else if (path.contains("missions")) {
                newNormal = CN_MISSIONS;
                newSelected = CN_MISSIONS_SEL;
            }

            if (newNormal != null) {
                Constructor<?> ctor = textures.getClass().getDeclaredConstructors()[0];
                ctor.setAccessible(true);
                Object newTextures = ctor.newInstance(newNormal, newSelected);
                texturesField.set(widget, newTextures);
            }
        } catch (Exception ignored) {}
    }

    private static boolean isLocalizationEnabled() {
        return Language.getInstance().has("tensura_tno.beyond_gacha.missions.category.daily");
    }
}

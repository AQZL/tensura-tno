package com.tensura_tno.mixin.client;

import com.tensura_tno.QuestNameCache;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Detects game language switches at render time and re-applies quest names
 * so the quest list reflects the current language.
 */
@Mixin(targets = "org.crypticdev.stextras.client.screen.STPrestigeScreens", remap = false)
public class QuestListLocalizationMixin {

    @Unique
    private static String tensura_tno$lastLangName = "";

    @Inject(method = "renderBackground", at = @At("HEAD"), remap = false)
    private void checkLanguageOnRender(@Coerce Object graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        String currentLang = Language.getInstance().getOrDefault("language.name");
        if (!currentLang.equals(tensura_tno$lastLangName)) {
            tensura_tno$lastLangName = currentLang;
            reapplyQuestNames();
        }
    }

    private static void reapplyQuestNames() {
        Language lang = Language.getInstance();
        for (Object def : QuestNameCache.QUESTS) {
            try {
                Field idField   = def.getClass().getField("id");
                Field nameField = def.getClass().getField("name");
                Object id = idField.get(def);
                if (id == null) continue;
                Method getPath = id.getClass().getMethod("getPath");
                String idPath  = (String) getPath.invoke(id);

                String langKey = QuestNameCache.LANG_KEYS.get(idPath);
                if (langKey != null) {
                    nameField.set(def, lang.has(langKey) ? lang.getOrDefault(langKey) : langKey);
                } else {
                    String key = "stextras.quest.name." + idPath;
                    if (lang.has(key)) {
                        nameField.set(def, lang.getOrDefault(key));
                    } else {
                        String original = QuestNameCache.ORIGINAL_NAMES.get(idPath);
                        if (original != null) {
                            nameField.set(def, original);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}

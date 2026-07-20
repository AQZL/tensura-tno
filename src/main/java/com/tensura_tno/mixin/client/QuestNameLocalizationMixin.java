package com.tensura_tno.mixin.client;

import com.tensura_tno.QuestNameCache;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Intercepts client-side quest sync to resolve @lang: prefixed quest names
 * and translate quest names via the Language system.
 *
 * <p>Supported {@code name} syntax:
 * <ul>
 *   <li><b>Single key</b> — {@code @lang:tensura_tno.race_prestige.foo.quest.0}
 *       → resolves to the lang value of that key.</li>
 *   <li><b>Composite key</b> — {@code @lang:keyA+keyB[+keyC...]}
 *       → resolves each key against the language registry and concatenates
 *       the results in order. Useful to build a localized phrase from a
 *       prefix (e.g. {@code tensura_tno.action.master}) and a foreign-mod
 *       term (e.g. {@code tensura.skill.wrath}) without duplicating the
 *       foreign translation in our own lang file.</li>
 * </ul>
 *
 * <p>Missing keys fall back to the raw key itself so that mistyped keys are
 * visible in the client UI rather than silently empty.
 */
@Mixin(targets = "org.crypticdev.stextras.quest.registry.QuestRegistry", remap = false)
public class QuestNameLocalizationMixin {

    private static final String LANG_PREFIX = "@lang:";
    private static final String COMPOSITE_SEPARATOR = "+";

    @Inject(method = "setQuestsOnClient", at = @At("TAIL"), remap = false)
    private void injectLocalizeNames(List<?> quests, CallbackInfo ci) {
        Language lang = Language.getInstance();
        for (Object def : quests) {
            try {
                Field idField   = def.getClass().getField("id");
                Field nameField = def.getClass().getField("name");
                Object id = idField.get(def);
                if (id == null) continue;
                Method getPath = id.getClass().getMethod("getPath");
                String idPath  = (String) getPath.invoke(id);
                String name    = (String) nameField.get(def);

                if (name != null && name.startsWith(LANG_PREFIX)) {
                    String langKey = name.substring(LANG_PREFIX.length());
                    QuestNameCache.LANG_KEYS.put(idPath, langKey);
                    nameField.set(def, resolveLangExpression(lang, langKey));
                } else {
                    QuestNameCache.ORIGINAL_NAMES.putIfAbsent(idPath, name);
                    String key = "stextras.quest.name." + idPath;
                    if (lang.has(key)) {
                        nameField.set(def, lang.getOrDefault(key));
                    }
                }
            } catch (Exception ignored) {}
        }
        QuestNameCache.QUESTS.clear();
        QuestNameCache.QUESTS.addAll(quests);
    }

    /**
     * Resolves either a single lang key or a composite expression of the form
     * {@code keyA+keyB[+keyC...]}. Each segment is looked up via {@link Language},
     * with missing keys falling back to the raw key string.
     *
     * <p>Segments prefixed with {@code #} are treated as <em>literal</em> text
     * and concatenated as-is without any lang lookup. This is useful for
     * embedding numbers (e.g. quest amounts) between two foreign-mod
     * translation keys without having to mirror the foreign translation in
     * our own lang file. Example: {@code action.mine+#50+block.tensura.magic_ore}
     * resolves to {@code "开采" + "50" + "魔矿石"}.
     */
    private static String resolveLangExpression(Language lang, String expression) {
        if (expression.indexOf(COMPOSITE_SEPARATOR.charAt(0)) < 0) {
            return lang.has(expression) ? lang.getOrDefault(expression) : expression;
        }
        String[] parts = expression.split("\\+");
        StringBuilder out = new StringBuilder(expression.length());
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.charAt(0) == '#') {
                out.append(part, 1, part.length());
                continue;
            }
            out.append(lang.has(part) ? lang.getOrDefault(part) : part);
        }
        return out.toString();
    }
}

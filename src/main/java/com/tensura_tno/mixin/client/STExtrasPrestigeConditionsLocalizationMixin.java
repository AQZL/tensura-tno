package com.tensura_tno.mixin.client;

import java.util.Map;
import com.tensura_tno.ftb.STExtrasHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Localizes the hardcoded English text in STExtras' Prestige Requirements tooltip
 * (the tooltip on the 重置计数器 / reset-counter button in tensura's main screen).
 *
 * STExtras' MainScreenMixin completely replaces tensura's translatable
 * getResetCounterProgress() return value with a hardcoded English Component built in
 * stextras$buildPrestigeRequirements(). Each bullet entry is rendered via the
 * private helper stextras$formatEntry(String label, String value, boolean isMet).
 *
 * We intercept stextras$formatEntry at HEAD and replace English labels/values with
 * translations looked up via Language keys, then rebuild the Component with correct
 * styles. require=0 ensures the injection silently no-ops when STExtras is absent.
 */
@Mixin(targets = "io.github.manasmods.tensura.client.screen.MainScreen", remap = false, priority = 1100)
public class STExtrasPrestigeConditionsLocalizationMixin {

    /** Maps the English label passed to stextras$formatEntry to its lang key. */
    private static final Map<String, String> LABEL_KEYS = Map.of(
        "Max Evo",    "tensura_tno.prestige.label.max_evo",
        "Awakened",   "tensura_tno.prestige.label.awakened",
        "Essence",    "tensura_tno.prestige.label.essence",
        "Boss Kills", "tensura_tno.prestige.label.boss_kills",
        "Quests",     "tensura_tno.prestige.label.quests"
    );

    private static final String COMPLETED_KEY = "tensura_tno.prestige.value.completed";
    private static final String NOT_COMPLETED_KEY = "tensura_tno.prestige.value.not_completed";
    @Unique
    private static long tensuraTno$lastPrestigeSyncRequestMs;

    @Inject(
        method = "stextras$buildPrestigeRequirements",
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void tensuraTno$requestFreshPrestigeQuestState(CallbackInfoReturnable<Component> cir) {
        long now = System.currentTimeMillis();
        if (now - tensuraTno$lastPrestigeSyncRequestMs < 1000L) return;
        tensuraTno$lastPrestigeSyncRequestMs = now;
        STExtrasHelper.requestPrestigeScreenSync();
    }

    @Inject(
        method = "stextras$formatEntry",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void tensuraTno$localizeEntry(
            String label, String value, boolean isMet,
            CallbackInfoReturnable<Component> cir) {

        Language lang = Language.getInstance();

        // Translate label
        String labelKey = LABEL_KEYS.get(label);
        if (labelKey == null) return; // unknown entry – fall through to original
        String translatedLabel = lang.has(labelKey) ? lang.getOrDefault(labelKey) : label;

        // Translate value: "Completed" → 已完成(met) or 未完成(not met); numbers pass through
        String translatedValue;
        if ("Completed".equals(value)) {
            if (isMet) {
                translatedValue = lang.has(COMPLETED_KEY) ? lang.getOrDefault(COMPLETED_KEY) : value;
            } else {
                translatedValue = lang.has(NOT_COMPLETED_KEY) ? lang.getOrDefault(NOT_COMPLETED_KEY) : "未完成";
            }
        } else {
            translatedValue = value;
        }

        // Rebuild the Component with same visual style as stextras$formatEntry
        MutableComponent bullet = Component.literal("  ● ")
                .withStyle(isMet ? ChatFormatting.GREEN : ChatFormatting.RED);
        MutableComponent labelComp = Component.literal(translatedLabel + ": ")
                .withStyle(ChatFormatting.GRAY);
        MutableComponent valueComp = Component.literal(translatedValue)
                .withStyle(isMet ? ChatFormatting.WHITE : ChatFormatting.RED);
        MutableComponent icon = Component.literal(isMet ? " ✔" : " ✘")
                .withStyle(isMet ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED);

        cir.setReturnValue(bullet.append(labelComp).append(valueComp).append(icon));
    }

    /**
     * Intercepts TextUtil.toSmallCaps calls inside stextras$buildPrestigeRequirements to
     * translate the hardcoded title "Prestige Requirements" and "Ready to Prestige".
     * Chinese text is returned directly without small-caps conversion.
     */
    @Redirect(
        method = "stextras$buildPrestigeRequirements",
        at = @At(value = "INVOKE", target = "Lorg/crypticdev/stextras/utils/TextUtil;toSmallCaps(Ljava/lang/String;)Ljava/lang/String;"),
        remap = false,
        require = 0
    )
    private String tensuraTno$localizePrestigeTitle(String input) {
        Language lang = Language.getInstance();
        if ("Prestige Requirements".equals(input)) {
            String key = "tensura_tno.prestige.title";
            String translated = lang.has(key) ? lang.getOrDefault(key) : input;
            return isAscii(translated) ? tensuraTno$toSmallCaps(translated) : translated;
        }
        if ("Ready to Prestige".equals(input)) {
            String key = "tensura_tno.prestige.ready";
            String translated = lang.has(key) ? lang.getOrDefault(key) : input;
            return isAscii(translated) ? tensuraTno$toSmallCaps(translated) : translated;
        }
        return input;
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) return false;
        }
        return true;
    }

    /** Inlined copy of TextUtil.toSmallCaps to avoid compile-time dependency on stextras. */
    private static final java.util.Map<Character, Character> SMALL_CAPS_MAP = java.util.Map.ofEntries(
        java.util.Map.entry('a', 'ᴀ'), java.util.Map.entry('b', 'ʙ'), java.util.Map.entry('c', 'ᴄ'),
        java.util.Map.entry('d', 'ᴅ'), java.util.Map.entry('e', 'ᴇ'), java.util.Map.entry('f', 'ꜰ'),
        java.util.Map.entry('g', 'ɢ'), java.util.Map.entry('h', 'ʜ'), java.util.Map.entry('i', 'ɪ'),
        java.util.Map.entry('j', 'ᴊ'), java.util.Map.entry('k', 'ᴋ'), java.util.Map.entry('l', 'ʟ'),
        java.util.Map.entry('m', 'ᴍ'), java.util.Map.entry('n', 'ɴ'), java.util.Map.entry('o', 'ᴏ'),
        java.util.Map.entry('p', 'ᴘ'), java.util.Map.entry('q', 'ǫ'), java.util.Map.entry('r', 'ʀ'),
        java.util.Map.entry('s', 'ѕ'), java.util.Map.entry('t', 'ᴛ'), java.util.Map.entry('u', 'ᴜ'),
        java.util.Map.entry('v', 'ᴠ'), java.util.Map.entry('w', 'ᴡ'), java.util.Map.entry('x', 'x'),
        java.util.Map.entry('y', 'ʏ'), java.util.Map.entry('z', 'ᴢ')
    );

    private static String tensuraTno$toSmallCaps(String input) {
        StringBuilder result = new StringBuilder();
        boolean skipNext = false;
        for (char c : input.toCharArray()) {
            if ((c == 167 || c == '&') && !skipNext) {
                skipNext = true;
                result.append(c);
            } else if (skipNext) {
                skipNext = false;
                result.append(c);
            } else {
                result.append(SMALL_CAPS_MAP.getOrDefault(Character.toLowerCase(c), c));
            }
        }
        return result.toString();
    }
}

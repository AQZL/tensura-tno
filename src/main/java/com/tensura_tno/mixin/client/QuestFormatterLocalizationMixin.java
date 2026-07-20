package com.tensura_tno.mixin.client;

import com.tensura_tno.QuestNameCache;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates all hardcoded English strings in STPrestigeQuestFormatter:
 *  - Quest description sentences ("You have to kill N X.")
 *  - Tooltip labels (Rarity, Type, Progress, Rewards)
 *  - Rarity enum names (NOVICE, ADEPT, …)
 *  - Type names via Language system (entity.namespace.path / item.namespace.path)
 *  - Race tooltip strings
 */
@Mixin(targets = "org.crypticdev.stextras.client.screen.prestige.STPrestigeQuestFormatter", remap = false)
public class QuestFormatterLocalizationMixin {

    private static final String FORMATTER_KEY_PREFIX = "tensura_tno.quest_formatter.";

    /**
     * Gate: returns true only when Minecraft has loaded our zh_cn.json
     * (i.e. the game language is zh_cn or another language that has these keys).
     * In English mode this key will not be in Language, so we skip all translations.
     */
    private static boolean isTranslationEnabled() {
        return Language.getInstance().has("stextras.quest.name.daily_novice_kill_slime");
    }

    private static String tr(String suffix, String fallback) {
        Language lang = Language.getInstance();
        String key = FORMATTER_KEY_PREFIX + suffix;
        return lang.has(key) ? lang.getOrDefault(key) : fallback;
    }

    private static String trf(String suffix, String fallback, Object... args) {
        return String.format(tr(suffix, fallback), args);
    }

    // -----------------------------------------------------------------------
    // Translate race names on the left panel buttons
    // -----------------------------------------------------------------------
    @Inject(method = "prettyRaceName", at = @At("RETURN"), remap = false, cancellable = true)
    private static void translateRaceName(ResourceLocation id,
                                          CallbackInfoReturnable<String> cir) {
        String localized = findRaceTranslation(id);
        if (localized != null) {
            cir.setReturnValue(localized);
        }
    }

    // -----------------------------------------------------------------------
    // Translate quest description sentence + embedded type names
    // -----------------------------------------------------------------------
    @Inject(method = "createQuestDescription", at = @At("RETURN"), remap = false, cancellable = true)
    private static void translateDescription(@Coerce Object quest, boolean shiftDown,
                                             CallbackInfoReturnable<String> cir) {
        if (!isTranslationEnabled()) return;
        String translated = translateDescriptionText(cir.getReturnValue());
        if (translated == null) return;

        // Replace auto-generated English type names with current-language names.
        // This also fixes the expanded valid-options list shown while holding Shift.
        try {
            List<?> types = (List<?>) quest.getClass().getField("types").get(quest);
            if (!types.isEmpty()) {
                for (Object rl : types) {
                    String ns = (String) rl.getClass().getMethod("getNamespace").invoke(rl);
                    String path = (String) rl.getClass().getMethod("getPath").invoke(rl);
                    String localizedName = tryTranslateRL(ns, path);
                    if (localizedName == null) {
                        continue;
                    }

                    String englishName = autoFormatName(path);
                    String pluralEn = englishName.endsWith("s") ? englishName : englishName + "s";
                    translated = translated.replace(pluralEn, localizedName);
                    translated = translated.replace(englishName, localizedName);
                }

                if (types.size() > 1) {
                    String localizedValidOptions = tr("description.valid_options", "valid options");
                    translated = translated.replace(" valid options", " " + localizedValidOptions);
                    translated = translated.replace(" valid option", " " + localizedValidOptions);
                    translated = translated.replace("valid options", localizedValidOptions);
                    translated = translated.replace("valid option", localizedValidOptions);
                }
            }
        } catch (Exception ignored) {}

        cir.setReturnValue(translated);
    }

    // -----------------------------------------------------------------------
    // Translate "Type:" summary value via Language system
    // -----------------------------------------------------------------------
    @Inject(method = "typeSummary", at = @At("RETURN"), remap = false, cancellable = true)
    private static void translateTypeSummary(@Coerce Object quest,
                                             CallbackInfoReturnable<String> cir) {
        if (!isTranslationEnabled()) return;
        String translated = buildTranslatedTypeSummary(quest);
        if (translated != null) cir.setReturnValue(translated);
    }

    // -----------------------------------------------------------------------
    // Translate tooltip labels and rarity names
    // -----------------------------------------------------------------------
    @Inject(method = "buildQuestTooltip", at = @At("RETURN"), remap = false, cancellable = true)
    private static void translateTooltipLabels(@Coerce Object quest, ResourceLocation id,
                                               boolean completed, int progress, int amount,
                                               boolean showProgress, String progressTextOverride,
                                               int resetCounter, boolean shiftDown,
                                               CallbackInfoReturnable<MutableComponent> cir) {
        MutableComponent tooltip = cir.getReturnValue();
        List<net.minecraft.network.chat.Component> siblings = tooltip.getSiblings();

        // Always fix quest title at render time to respect the current language setting.
        if (!siblings.isEmpty()) {
            Language lang = Language.getInstance();
            String idPath = id.getPath();
            String resolved = null;

            String langKey = QuestNameCache.LANG_KEYS.get(idPath);
            if (langKey != null && lang.has(langKey)) {
                resolved = lang.getOrDefault(langKey);
            } else {
                String questKey = "stextras.quest.name." + idPath;
                if (lang.has(questKey)) {
                    resolved = lang.getOrDefault(questKey);
                } else {
                    resolved = QuestNameCache.ORIGINAL_NAMES.get(idPath);
                }
            }

            if (resolved != null) {
                siblings.set(0, Component.literal(resolved)
                        .setStyle(siblings.get(0).getStyle()));
            }
        }

        if (!isTranslationEnabled()) return;
        translateComponentTree(tooltip);
    }

    // -----------------------------------------------------------------------
    // Translate race tooltip labels
    // -----------------------------------------------------------------------
    @Inject(method = "buildRaceTooltip", at = @At("RETURN"), remap = false, cancellable = true)
    private static void translateRaceTooltip(ResourceLocation raceId, boolean completed,
                                             List<?> raceQuests, Set<?> completedIds,
                                             CallbackInfoReturnable<MutableComponent> cir) {
        cir.setReturnValue(rebuildRaceTooltip(raceId, completed, raceQuests, completedIds));
    }

    private static MutableComponent rebuildRaceTooltip(ResourceLocation raceId, boolean completed,
                                                       List<?> raceQuests, Set<?> completedIds) {
        MutableComponent tooltip = Component.literal("");

        tooltip.append(Component.literal(completed
                ? tr("status.completed", "Completed")
                : tr("status.in_progress", "In progress"))
                .withColor(completed ? 5635925 : 16777045));
        tooltip.append(Component.literal("\n"));
        tooltip.append(Component.literal(tr("label.race", "Race: ")).withColor(11184810));
        tooltip.append(Component.literal(localizeRaceName(raceId)).withColor(16777215));
        tooltip.append(Component.literal("\n\n"));

        if (!raceQuests.isEmpty()) {
            tooltip.append(Component.literal(tr("label.race_quests", "Race Quests:\n")).withColor(16755200));

            for (Object quest : raceQuests) {
                ResourceLocation questId = getQuestId(quest);
                boolean questCompleted = questId != null && completedIds.contains(questId);
                String questName = resolveQuestDisplayName(quest, questId);

                tooltip.append(Component.literal(questCompleted ? "✓ " : "○ ")
                                .withColor(questCompleted ? 5635925 : 11184810))
                        .append(Component.literal(questName).withColor(16777215))
                        .append(Component.literal("\n"));
            }
        } else {
            tooltip.append(Component.literal(tr("label.race_none", "No quests found for this race"))
                    .withColor(11184810));
        }

        return tooltip;
    }

    // -----------------------------------------------------------------------
    // Component tree walker – replaces known English labels in-place
    // -----------------------------------------------------------------------
    private static void translateComponentTree(MutableComponent component) {
        List<net.minecraft.network.chat.Component> siblings = component.getSiblings();
        for (int i = 0; i < siblings.size(); i++) {
            net.minecraft.network.chat.Component sib = siblings.get(i);
            String litText = getLiteralText(sib);
            if (litText != null) {
                String cn = translateLabel(litText);
                if (cn != null) {
                    siblings.set(i, Component.literal(cn).setStyle(sib.getStyle()));
                }
            }
            if (sib instanceof MutableComponent mc && !mc.getSiblings().isEmpty()) {
                translateComponentTree(mc);
            }
        }
    }

    /** Reflectively extracts text from a LiteralContents component, or null for other types. */
    private static String getLiteralText(net.minecraft.network.chat.Component c) {
        try {
            Object contents = c.getContents();
            return (String) contents.getClass().getMethod("text").invoke(contents);
        } catch (Exception e) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Static label translation table
    // -----------------------------------------------------------------------
    private static String translateLabel(String text) {
        return switch (text) {
            case "\nRarity: "        -> tr("label.rarity", "\nRarity: ");
            case "\nType: "          -> tr("label.type", "\nType: ");
            case "\nProgress: "      -> tr("label.progress", "\nProgress: ");
            case "\n\nRewards:\n"    -> tr("label.rewards", "\n\nRewards:\n");
            case "Completed"         -> tr("status.completed", "Completed");
            case "In progress"       -> tr("status.in_progress", "In progress");
            case "NOVICE"            -> tr("rarity.novice", "NOVICE");
            case "INTERMEDIATE"      -> tr("rarity.intermediate", "INTERMEDIATE");
            case "ADEPT"             -> tr("rarity.adept", "ADEPT");
            case "EXPERT"            -> tr("rarity.expert", "EXPERT");
            case "MASTER"            -> tr("rarity.master", "MASTER");
            case "None"              -> tr("reward.none", "None");
            case "Various"           -> tr("type.various", "Various");
            case "Race: "            -> tr("label.race", "Race: ");
            case "Race Quests:\n"    -> tr("label.race_quests", "Race Quests:\n");
            case "No quests found for this race" -> tr("label.race_none", "No quests found for this race");
            case "\n\n[Shift to see valid options]" -> tr("description.shift_hint", "\n\n[Shift to see valid options]");
            default -> null;
        };
    }

    private static String localizeRaceName(ResourceLocation id) {
        String localized = findRaceTranslation(id);
        return localized != null ? localized : autoFormatName(id.getPath());
    }

    private static String findRaceTranslation(ResourceLocation id) {
        Language lang = Language.getInstance();
        String directKey = id.getNamespace() + ".race." + id.getPath();
        if (lang.has(directKey)) {
            return lang.getOrDefault(directKey);
        }

        String legacyKey = "stextras.race." + id.getPath();
        if (lang.has(legacyKey)) {
            return lang.getOrDefault(legacyKey);
        }

        return null;
    }

    private static ResourceLocation getQuestId(Object quest) {
        try {
            return (ResourceLocation) quest.getClass().getField("id").get(quest);
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolveQuestDisplayName(Object quest, ResourceLocation questId) {
        Language lang = Language.getInstance();
        if (questId != null) {
            String idPath = questId.getPath();

            String langKey = QuestNameCache.LANG_KEYS.get(idPath);
            if (langKey != null && lang.has(langKey)) {
                return lang.getOrDefault(langKey);
            }

            String key = "stextras.quest.name." + idPath;
            if (lang.has(key)) {
                return lang.getOrDefault(key);
            }

            String original = QuestNameCache.ORIGINAL_NAMES.get(idPath);
            if (original != null && !original.isBlank()) {
                return original;
            }
        }

        try {
            String name = (String) quest.getClass().getField("name").get(quest);
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (Exception ignored) {}

        return questId != null ? autoFormatName(questId.getPath()) : tr("misc.unknown", "Unknown");
    }

    // -----------------------------------------------------------------------
    // Language-based type name lookup
    // -----------------------------------------------------------------------

    /**
     * Tries entity → item → block translation keys in the current game Language.
     * Returns the localized name, or null if none found.
     */
    private static String tryTranslateRL(String ns, String path) {
        String specialType = tryTranslateTypeIdentifier(path);
        if (specialType != null) {
            return specialType;
        }

        Language lang = Language.getInstance();
        for (String prefix : new String[]{"entity", "item", "block"}) {
            String key = prefix + "." + ns + "." + path;
            if (lang.has(key)) return lang.getOrDefault(key);
        }
        return null;
    }

    private static String tryTranslateTypeIdentifier(String path) {
        Language lang = Language.getInstance();
        String key = FORMATTER_KEY_PREFIX + "type." + path;
        if (lang.has(key)) {
            return lang.getOrDefault(key);
        }
        return null;
    }

    /**
     * Gets translated type summary for a quest. Uses reflection to read quest.types.
     * Returns null if translation is unavailable (caller keeps original value).
     */
    private static String buildTranslatedTypeSummary(Object quest) {
        try {
            Field typesField = quest.getClass().getField("types");
            List<?> types = (List<?>) typesField.get(quest);
            if (types.isEmpty()) return null;
            if (types.size() > 2)  return tr("type.various", "Various");

            List<String> names = new ArrayList<>();
            for (Object rl : types) {
                Method getNs   = rl.getClass().getMethod("getNamespace");
                Method getPath = rl.getClass().getMethod("getPath");
                String ns   = (String) getNs.invoke(rl);
                String path = (String) getPath.invoke(rl);
                String localized = tryTranslateRL(ns, path);
                if (localized == null) return null; // any miss → fall back
                names.add(localized);
            }
            return String.join(", ", names);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Mirrors STPrestigeQuestFormatter.formatTypeName – used to find the English
     * auto-generated name inside description sentences so we can replace it.
     */
    private static String autoFormatName(String path) {
        if (path == null || path.isEmpty()) return "Unknown";
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("_")) {
            if (!word.isEmpty())
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    // -----------------------------------------------------------------------
    // Description sentence translator
    // -----------------------------------------------------------------------
    private static String translateDescriptionText(String text) {
        if (text == null || text.isEmpty()) return text;

        String suffix = "";
        String main   = text;
        int voIdx = text.indexOf("\n\nValid options:\n");
        if (voIdx >= 0) {
            main   = text.substring(0, voIdx);
            suffix = tr("description.valid_options_header", "\n\nValid options:\n")
                    + text.substring(voIdx + "\n\nValid options:\n".length());
        } else if (text.contains("\n\n[Shift to see valid options]")) {
            main   = text.replace("\n\n[Shift to see valid options]", "");
            suffix = tr("description.shift_hint", "\n\n[Shift to see valid options]");
        }

        Matcher m;

        m = p("You have to kill (\\d+) (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.kill", "You have to kill %s %s.", m.group(1), m.group(2)) + suffix;

        m = p("You have to cook (\\d+) (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.cook", "You have to cook %s %s.", m.group(1), m.group(2)) + suffix;

        m = p("You have to craft (\\d+) (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.craft", "You have to craft %s %s.", m.group(1), m.group(2)) + suffix;

        m = p("You have to mine (\\d+) (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.mine", "You have to mine %s %s.", m.group(1), m.group(2)) + suffix;

        m = p("You have to breed (\\d+) (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.breed", "You have to breed %s %s.", m.group(1), m.group(2)) + suffix;

        m = p("You have to consume (\\d+) (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.consume", "You have to consume %s %s.", m.group(1), m.group(2)) + suffix;

        m = p("You have to tensura smith (\\d+) (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.tensura_smith", "You have to tensura smith %s %s.", m.group(1), m.group(2)) + suffix;

        m = p("You have to minecraft smith (\\d+) (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.minecraft_smith", "You have to minecraft smith %s %s.", m.group(1), m.group(2)) + suffix;

        m = p("You have to obtain (\\d+) (.+) from trading with a villager\\.").matcher(main);
        if (m.matches()) return trf("description.obtain_trade", "You have to obtain %s %s from trading with a villager.", m.group(1), m.group(2)) + suffix;

        m = p("You have to deal (\\d+) spiritual damage to (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.spiritual_damage", "You have to deal %1$s spiritual damage to %2$s.", m.group(1), m.group(2)) + suffix;

        m = p("You have to farm (\\d+) (.+?)( at stage \\d+| between stages \\d+ and \\d+" +
              "| from stage \\d+ onward| up to stage \\d+| when fully grown)?\\.").matcher(main);
        if (m.matches()) {
            String stageCn = m.group(3) != null ? translateStageSuffix(m.group(3)) : "";
            return trf("description.farm", "You have to farm %s %s%s.", m.group(1), m.group(2), stageCn) + suffix;
        }

        m = p("You have to master (\\d+) (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.master_count", "You have to master %s %s.", m.group(1), m.group(2)) + suffix;

        m = p("You have to master (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.master", "You have to master %s.", m.group(1)) + suffix;

        m = p("You have to name (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.name", "You have to name %s.", m.group(1)) + suffix;

        m = p("Complete the quest: (.+)\\.").matcher(main);
        if (m.matches()) return trf("description.complete", "Complete the quest: %s.", m.group(1)) + suffix;

        return text;
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.DOTALL);
    }

    private static String translateStageSuffix(String s) {
        if (s == null) return "";
        s = s.trim();
        Matcher m;
        m = Pattern.compile("at stage (\\d+)").matcher(s);
        if (m.find()) return trf("stage.at", " at stage %s", m.group(1));
        m = Pattern.compile("between stages (\\d+) and (\\d+)").matcher(s);
        if (m.find()) return trf("stage.between", " between stages %s and %s", m.group(1), m.group(2));
        m = Pattern.compile("from stage (\\d+) onward").matcher(s);
        if (m.find()) return trf("stage.from", " from stage %s onward", m.group(1));
        m = Pattern.compile("up to stage (\\d+)").matcher(s);
        if (m.find()) return trf("stage.up_to", " up to stage %s", m.group(1));
        if (s.contains("when fully grown")) return tr("stage.fully_grown", " when fully grown");
        return s;
    }
}

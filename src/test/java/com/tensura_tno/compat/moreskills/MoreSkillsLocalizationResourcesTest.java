package com.tensura_tno.compat.moreskills;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoreSkillsLocalizationResourcesTest {

    private static final Path ROOT = Path.of(
            "src/main/resources/assets/tensura_tno_moreskills_compat");
    private static final Pattern PRESERVED_TOKEN = Pattern.compile(
            "%(?:\\d+\\$)?[sdf]|§.|[◆■✦|]|\\d+(?:\\.\\d+)?");

    @Test
    void everyCompatibilityIndexEntryHasAnEnglishLangValue() throws Exception {
        Map<String, String> index = readMap(ROOT.resolve("moreskills_hardcoded_index.json"));
        Map<String, String> lang = readMap(ROOT.resolve("lang/en_us.json"));

        assertTrue(index.size() >= 650, "Expected the complete player-visible MoreSkills text inventory");
        assertTrue(index.values().stream().allMatch(lang::containsKey));
    }

    @Test
    void internalSearchAndConfigStringsAreExcluded() throws Exception {
        Map<String, String> index = readMap(ROOT.resolve("moreskills_hardcoded_index.json"));

        assertFalse(index.containsKey("spatial motion"));
        assertFalse(index.containsKey("lord of sloth"));
        assertFalse(index.containsKey("[^a-zA-Z0-9,'_\\-’]"));
        assertFalse(index.containsKey("Maximum attack attribute gained from Black Blood Growth."));
    }

    @Test
    void exactAndDynamicLegacyMessagesCanBecomeTranslationComponents() {
        assertNotNull(MoreSkillsHardcodedLocalization.createComponent(
                "Istaroth isolates your timeline."));
        assertNotNull(MoreSkillsHardcodedLocalization.createComponent(
                "Eden of the Life Deity needs 500 MP."));
    }

    @Test
    void simplifiedChineseCoversAllKeysAndPreservesDynamicTextBoundaries() throws Exception {
        Map<String, String> english = readMap(ROOT.resolve("lang/en_us.json"));
        Map<String, String> chinese = readMap(ROOT.resolve("lang/zh_cn.json"));

        assertTrue(chinese.keySet().equals(english.keySet()),
                "zh_cn must contain exactly the same compatibility keys as en_us");

        for (Map.Entry<String, String> entry : english.entrySet()) {
            String translated = chinese.get(entry.getKey());
            assertTrue(translated != null && !translated.isEmpty(), "Missing translation: " + entry.getKey());
            assertTrue(leadingWhitespace(entry.getValue()).equals(leadingWhitespace(translated)),
                    "Leading whitespace changed: " + entry.getKey());
            assertTrue(trailingWhitespace(entry.getValue()).equals(trailingWhitespace(translated)),
                    "Trailing whitespace changed: " + entry.getKey());
            assertTrue(tokens(entry.getValue()).equals(tokens(translated)),
                    "Formatting token changed: " + entry.getKey());
        }
    }

    private static Map<String, String> readMap(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, new TypeToken<Map<String, String>>() { }.getType());
        }
    }

    private static String leadingWhitespace(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        return value.substring(0, index);
    }

    private static String trailingWhitespace(String value) {
        int index = value.length();
        while (index > 0 && Character.isWhitespace(value.charAt(index - 1))) index--;
        return value.substring(index);
    }

    private static java.util.List<String> tokens(String value) {
        java.util.List<String> result = new java.util.ArrayList<>();
        Matcher matcher = PRESERVED_TOKEN.matcher(value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
}

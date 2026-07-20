package com.tensura_tno.client.browser;



import java.io.BufferedReader;

import java.io.IOException;

import java.lang.reflect.Field;

import java.lang.reflect.Method;

import java.net.URI;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.Paths;

import java.util.Properties;

import net.minecraft.client.Minecraft;



public final class BrowserClientConfig {

    private static final Path FILE = Paths.get("config", "tensura_tno-browser.properties");

    private static final Path ELEMENT_RULES_FILE = Paths.get("config", "tensura_tno-browser-elements.txt");

    private static final String CHINESE_WIKI_URL = "https://docs.qq.com/aio/p/scjg6678qvr4qq3";

    private static final String GLOBAL_WIKI_URL = "https://tensura.wiki.gg";



    private static volatile boolean loaded;

    private static volatile String homepage = CHINESE_WIKI_URL;

    private static volatile boolean blockNewWindows = true;

    private static volatile boolean removeWorkbenchTitlebar = true;

    private static volatile boolean removeTransitionSpace = true;

    private static volatile boolean forceFirstOpenUrl = true;

    private static volatile String firstOpenUrl = CHINESE_WIKI_URL;



    private BrowserClientConfig() {

    }



    public static String homepage() {

        ensureLoaded();

        return resolveLandingUrl(homepage);

    }



    public static String defaultWikiUrl() {

        ensureLoaded();

        return currentLanguageDefaultUrl();

    }



    public static String chineseWikiUrl() {

        return CHINESE_WIKI_URL;

    }



    public static String globalWikiUrl() {

        return GLOBAL_WIKI_URL;

    }



    public static boolean blockNewWindows() {

        ensureLoaded();

        return blockNewWindows;

    }



    public static boolean removeWorkbenchTitlebar() {

        ensureLoaded();

        return removeWorkbenchTitlebar;

    }



    public static boolean removeTransitionSpace() {

        ensureLoaded();

        return removeTransitionSpace;

    }



    public static boolean forceFirstOpenUrl() {

        ensureLoaded();

        return forceFirstOpenUrl;

    }



    public static String firstOpenUrl() {

        ensureLoaded();

        return resolveLandingUrl(firstOpenUrl);

    }



    public static boolean isManagedWikiUrl(String url) {

        return isChineseWikiUrl(url) || isGlobalWikiUrl(url);

    }



    public static boolean isChineseWikiUrl(String url) {

        if (url == null || url.isBlank()) {

            return false;

        }



        String host = extractHost(url);

        if (!host.isEmpty()) {

            return host.equals("docs.qq.com");

        }



        return normalizeWikiUrl(url).equalsIgnoreCase(normalizeWikiUrl(CHINESE_WIKI_URL));

    }



    public static boolean isGlobalWikiUrl(String url) {

        if (url == null || url.isBlank()) {

            return false;

        }



        String host = extractHost(url);

        if (!host.isEmpty()) {

            return host.equals("tensura.wiki.gg");

        }



        return normalizeWikiUrl(url).equalsIgnoreCase(normalizeWikiUrl(GLOBAL_WIKI_URL));

    }



    private static synchronized void ensureLoaded() {

        if (loaded) {

            return;

        }



        String defaultUrl = currentLanguageDefaultUrl();

        homepage = defaultUrl;

        firstOpenUrl = defaultUrl;



        Properties props = new Properties();

        try {

            Path parent = FILE.getParent();

            if (parent != null) {

                Files.createDirectories(parent);

            }



            if (Files.exists(FILE)) {

                try (BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {

                    props.load(reader);

                }



                homepage = readString(props, "homepage", homepage);

                blockNewWindows = readBoolean(props, "webview2.block_new_windows", blockNewWindows);

                removeWorkbenchTitlebar = readBoolean(props, "webview2.remove_workbench_titlebar", removeWorkbenchTitlebar);

                removeTransitionSpace = readBoolean(props, "webview2.remove_transition_space", removeTransitionSpace);

                forceFirstOpenUrl = readBoolean(props, "startup.force_first_open_url", forceFirstOpenUrl);

                firstOpenUrl = readString(props, "startup.first_open_url", firstOpenUrl);

            }



            ensureDefaultElementRulesFile();

            writeFile();

        } catch (IOException ignored) {

        }



        loaded = true;

    }



    private static String readString(Properties props, String key, String defaultValue) {

        String value = props.getProperty(key);

        if (value == null) {

            return defaultValue;

        }



        String trimmed = value.trim();

        return trimmed.isEmpty() ? defaultValue : trimmed;

    }



    private static boolean readBoolean(Properties props, String key, boolean defaultValue) {

        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(defaultValue)));

    }



    private static String resolveLandingUrl(String configuredUrl) {

        String defaultUrl = currentLanguageDefaultUrl();

        if (configuredUrl == null || configuredUrl.isBlank()) {

            return defaultUrl;

        }



        String trimmed = configuredUrl.trim();

        if (isManagedWikiUrl(trimmed)) {

            return defaultUrl;

        }

        return trimmed;

    }



    private static String normalizeWikiUrl(String url) {

        String normalized = url.trim();

        while (normalized.endsWith("/")) {

            normalized = normalized.substring(0, normalized.length() - 1);

        }

        return normalized;

    }



    private static String extractHost(String url) {

        try {

            URI uri = URI.create(url.trim());

            String host = uri.getHost();

            return host == null ? "" : host.toLowerCase();

        } catch (IllegalArgumentException ignored) {

            return "";

        }

    }



    private static String currentLanguageDefaultUrl() {

        return isChineseWikiLanguage(currentLanguageCode()) ? CHINESE_WIKI_URL : GLOBAL_WIKI_URL;

    }



    private static boolean isChineseWikiLanguage(String languageCode) {

        String normalized = normalizeLanguageCode(languageCode);

        return normalized.equals("zh_cn")

            || normalized.equals("zh_tw")

            || normalized.equals("zh_hk")

            || normalized.equals("zh_mo")

            || normalized.startsWith("zh_hant");

    }



    private static String currentLanguageCode() {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft == null) {

            return "en_us";

        }



        try {

            Object languageManager = minecraft.getLanguageManager();

            if (languageManager != null) {

                String selected = invokeLanguageCode(languageManager);

                if (!selected.isBlank()) {

                    return selected;

                }

            }

        } catch (Throwable ignored) {

        }



        try {

            Field field = minecraft.options.getClass().getDeclaredField("languageCode");

            field.setAccessible(true);

            Object value = field.get(minecraft.options);

            if (value instanceof String string && !string.isBlank()) {

                return normalizeLanguageCode(string);

            }

        } catch (ReflectiveOperationException ignored) {

        }



        return "en_us";

    }



    private static String invokeLanguageCode(Object languageManager) {

        try {

            Method getSelected = languageManager.getClass().getMethod("getSelected");

            Object selected = getSelected.invoke(languageManager);

            if (selected instanceof String string && !string.isBlank()) {

                return normalizeLanguageCode(string);

            }

            if (selected != null) {

                try {

                    Method getCode = selected.getClass().getMethod("getCode");

                    Object code = getCode.invoke(selected);

                    if (code instanceof String string && !string.isBlank()) {

                        return normalizeLanguageCode(string);

                    }

                } catch (ReflectiveOperationException ignored) {

                }

            }

        } catch (ReflectiveOperationException ignored) {

        }

        return "";

    }



    private static String normalizeLanguageCode(String languageCode) {

        return languageCode == null ? "en_us" : languageCode.trim().toLowerCase().replace('-', '_');

    }



    private static void ensureDefaultElementRulesFile() throws IOException {

        Path parent = ELEMENT_RULES_FILE.getParent();

        if (parent != null) {

            Files.createDirectories(parent);

        }



        if (!Files.exists(ELEMENT_RULES_FILE)) {

            String defaults = "# Tensura TNO browser element removal rules\n"

                + "# One CSS selector per line. Lines starting with # are comments.\n"

                + "#workbench-titlebar\n"

                + "#transition-space\n";

            Files.writeString(ELEMENT_RULES_FILE, defaults, StandardCharsets.UTF_8);

        }

    }



    private static void writeFile() throws IOException {

        String out = "# Browser client config (Win64 WebView2 preferred)\n"

            + "homepage=" + homepage + "\n"

            + "webview2.block_new_windows=" + blockNewWindows + "\n"

            + "webview2.remove_workbench_titlebar=" + removeWorkbenchTitlebar + "\n"

            + "webview2.remove_transition_space=" + removeTransitionSpace + "\n"

            + "startup.force_first_open_url=" + forceFirstOpenUrl + "\n"

            + "startup.first_open_url=" + firstOpenUrl + "\n";

        Files.writeString(FILE, out, StandardCharsets.UTF_8);

    }

}
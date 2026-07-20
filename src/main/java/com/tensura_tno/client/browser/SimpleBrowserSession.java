package com.tensura_tno.client.browser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SimpleBrowserSession {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tensura_tno-browser");
        thread.setDaemon(true);
        return thread;
    });
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern STYLE = Pattern.compile("(?is)<style[^>]*>.*?</style>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern LINK = Pattern.compile("(?is)<a\\s+[^>]*href\\s*=\\s*(['\"])(.*?)\\1[^>]*>(.*?)</a>");

    private static final List<String> HISTORY = new ArrayList<>();
    private static int historyIndex = -1;
    private static String currentUrl = "";
    private static String pageTitle = "";
    private static String pageText = "";
    private static String status = "";
    private static int bodyTextColor = 0xFFFFFF;
    private static int bodyBackgroundColor = 0x102030;
    private static int linkTextColor = 0x7FC4FF;
    private static int scroll;
    private static boolean loading;
    private static Future<?> inFlight;
    private static List<LinkEntry> links = new ArrayList<>();

    private SimpleBrowserSession() {
    }

    public static synchronized void navigate(String rawUrl, boolean pushHistory) {
        String normalized = normalizeUrl(rawUrl);
        if (normalized == null) {
            status = "无效地址";
            return;
        }

        if (pushHistory) {
            while (HISTORY.size() > historyIndex + 1) {
                HISTORY.remove(HISTORY.size() - 1);
            }
            HISTORY.add(normalized);
            historyIndex = HISTORY.size() - 1;
        }

        currentUrl = normalized;
        scroll = 0;
        startFetch(normalized);
    }

    public static synchronized void reload() {
        if (!currentUrl.isEmpty()) {
            startFetch(currentUrl);
        }
    }

    public static synchronized void back() {
        if (historyIndex > 0) {
            historyIndex--;
            currentUrl = HISTORY.get(historyIndex);
            scroll = 0;
            startFetch(currentUrl);
        }
    }

    public static synchronized void forward() {
        if (historyIndex >= 0 && historyIndex < HISTORY.size() - 1) {
            historyIndex++;
            currentUrl = HISTORY.get(historyIndex);
            scroll = 0;
            startFetch(currentUrl);
        }
    }

    public static synchronized boolean canBack() {
        return historyIndex > 0;
    }

    public static synchronized boolean canForward() {
        return historyIndex >= 0 && historyIndex < HISTORY.size() - 1;
    }

    public static synchronized String currentUrl() {
        return currentUrl;
    }

    public static synchronized String pageTitle() {
        return pageTitle;
    }

    public static synchronized String pageText() {
        return pageText;
    }

    public static synchronized List<LinkEntry> links() {
        return new ArrayList<>(links);
    }

    public static synchronized String status() {
        return status;
    }

    public static synchronized int bodyTextColor() {
        return bodyTextColor;
    }

    public static synchronized int bodyBackgroundColor() {
        return bodyBackgroundColor;
    }

    public static synchronized int linkTextColor() {
        return linkTextColor;
    }

    public static synchronized boolean loading() {
        return loading;
    }

    public static synchronized int scroll() {
        return scroll;
    }

    public static synchronized void changeScroll(int delta) {
        scroll = Math.max(0, scroll + delta);
    }

    public static synchronized void onScreenClosed() {
        if (inFlight != null) {
            inFlight.cancel(true);
            inFlight = null;
        }
        loading = false;
    }

    private static void startFetch(String url) {
        if (inFlight != null) {
            inFlight.cancel(true);
            inFlight = null;
        }

        loading = true;
        status = "加载中...";
        inFlight = EXECUTOR.submit(() -> fetch(url));
    }

    private static void fetch(String url) {
        String html;
        try {
            html = readUrl(url);
        } catch (Exception exception) {
            synchronized (SimpleBrowserSession.class) {
                loading = false;
                status = "加载失败: " + exception.getClass().getSimpleName();
                pageTitle = "错误";
                pageText = exception.getMessage() == null ? "无法打开页面。" : exception.getMessage();
                links = Collections.emptyList();
            }
            return;
        }

        String title = findTitle(html);
        String cleaned = cleanupText(html);
        List<LinkEntry> parsedLinks = parseLinks(url, html);
        synchronized (SimpleBrowserSession.class) {
            loading = false;
            pageTitle = title.isEmpty() ? url : title;
            pageText = cleaned;
            links = parsedLinks;
            status = "完成";
        }
    }

    private static String readUrl(String url) throws IOException {
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(6000);
        connection.setRequestProperty("User-Agent", "tensura_tno_simple_browser/1.0");

        try (InputStream in = connection.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length > 512000) {
                byte[] limited = new byte[512000];
                System.arraycopy(bytes, 0, limited, 0, limited.length);
                return new String(limited, StandardCharsets.UTF_8);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String findTitle(String html) {
        Matcher matcher = TITLE.matcher(html);
        return matcher.find() ? decodeHtml(matcher.group(1)).trim() : "";
    }

    private static List<LinkEntry> parseLinks(String current, String html) {
        List<LinkEntry> out = new ArrayList<>();
        Matcher matcher = LINK.matcher(html);

        URI base;
        try {
            base = URI.create(current);
        } catch (Exception exception) {
            base = null;
        }

        while (matcher.find() && out.size() < 24) {
            String href = matcher.group(2);
            String text = decodeHtml(TAG.matcher(Objects.toString(matcher.group(3), "")).replaceAll(" ")).trim();
            if (href == null || href.isBlank()) {
                continue;
            }

            String resolved;
            try {
                URI uri = URI.create(href.trim());
                if (base != null && !uri.isAbsolute()) {
                    uri = base.resolve(uri);
                }
                resolved = uri.toString();
            } catch (Exception exception) {
                continue;
            }

            if (text.isEmpty()) {
                text = resolved;
            }
            out.add(new LinkEntry(text, resolved));
        }

        return out;
    }

    private static String cleanupText(String html) {
        String text = SCRIPT.matcher(html).replaceAll(" ");
        text = STYLE.matcher(text).replaceAll(" ");
        text = TAG.matcher(text).replaceAll(" ");
        text = decodeHtml(text);
        text = text.replace('\r', ' ').replace('\n', ' ');
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() > 30000) {
            text = text.substring(0, 30000);
        }
        return text;
    }

    private static String decodeHtml(String input) {
        return input
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'");
    }

    private static String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return "https://" + value;
        }
        return value;
    }

    public record LinkEntry(String text, String url) {
    }
}
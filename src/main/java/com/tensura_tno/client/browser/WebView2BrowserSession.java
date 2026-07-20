package com.tensura_tno.client.browser;

/**
 * 内置浏览器的公开 API 入口，统一代理到 JNI 绑定类。
 *
 * tensura_patch_webview2.dll 的 JNI 导出函数名是按
 * com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession
 * 的全限定名编码的，因此实际 native 方法必须保留在该类中。
 * 本类作为对外的稳定接口层，所有调用转发过去即可。
 */
public final class WebView2BrowserSession {
    private WebView2BrowserSession() {
    }

    public static boolean isAvailable() {
        return com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.isAvailable();
    }

    public static String diagnostics() {
        return com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.diagnostics();
    }

    public static void ensureBrowser(String url, int width, int height) {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.ensureBrowser(url, width, height);
    }

    public static void ensureBrowser(String url, int x, int y, int width, int height) {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.ensureBrowser(url, x, y, width, height);
    }

    public static void prepareForScreenOpen() {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.prepareForScreenOpen();
    }

    public static void closeBrowserKeepState() {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.closeBrowserKeepState();
    }

    public static void clientTickCleanup() {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.clientTickCleanup();
    }

    public static String getLastOrCurrentUrl() {
        return com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.getLastOrCurrentUrl();
    }

    public static void draw(double x1, double y1, double x2, double y2) {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.draw(x1, y1, x2, y2);
    }

    public static void resize(int width, int height) {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.resize(width, height);
    }

    public static void loadUrl(String url) {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.loadUrl(url);
    }

    public static void back() {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.back();
    }

    public static void forward() {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.forward();
    }

    public static void reload() {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.reload();
    }

    public static void injectMouseMove(int x, int y, boolean outside) {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.injectMouseMove(x, y, outside);
    }

    public static void injectMouseButton(int x, int y, int button, boolean pressed) {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.injectMouseButton(x, y, button, pressed);
    }

    public static void injectMouseWheel(int x, int y, int rotation) {
        com.sjyueluo.tensura_patch.client.browser.WebView2BrowserSession.injectMouseWheel(x, y, rotation);
    }
}

package com.sjyueluo.tensura_patch.client.browser;



import com.mojang.blaze3d.platform.Window;

import com.tensura_tno.client.browser.BrowserClientConfig;

import java.io.InputStream;

import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.Paths;

import java.nio.file.StandardCopyOption;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFWNativeWin32;



/**

 * Win64 WebView2 原生浏览器后端。

 *

 * 此类必须保留在 com.sjyueluo.tensura_patch.client.browser 包下，

 * 因为 tensura_patch_webview2.dll 的 JNI 导出函数名是基于该包路径编码的。

 * 对外统一通过 com.tensura_tno.client.browser.WebView2BrowserSession 访问。

 */

public final class WebView2BrowserSession {

    private static final String NATIVE_RESOURCE_PATH = "/META-INF/native/win64/tensura_patch_webview2.dll";

    private static final int CLOSE_CLEANUP_TICKS = 20;



    private static boolean checked;

    private static boolean available;

    private static boolean browserCreated;

    private static String lastUrl = "";

    private static String diagnostics = "Not initialized";

    private static int lastPixelWidth = -1;

    private static int lastPixelHeight = -1;

    private static int lastPixelX = -1;

    private static int lastPixelY = -1;

    private static int closeCleanupTicks;



    private WebView2BrowserSession() {

    }



    public static synchronized boolean isAvailable() {

        ensureNativeLoaded();

        return available;

    }



    public static synchronized String diagnostics() {

        ensureNativeLoaded();

        return diagnostics;

    }



    public static synchronized void ensureBrowser(String url, int width, int height) {

        ensureBrowser(url, 0, 0, width, height);

    }



    public static synchronized void ensureBrowser(String url, int x, int y, int width, int height) {

        ensureNativeLoaded();

        if (!available) {

            return;

        }

        if (closeCleanupTicks > 0) {

            return;

        }



        try {

            nativeConfigure(

                BrowserClientConfig.blockNewWindows(),

                BrowserClientConfig.removeWorkbenchTitlebar(),

                BrowserClientConfig.removeTransitionSpace()

            );

        } catch (Throwable throwable) {

            diagnostics = "nativeConfigure exception: " + throwable.getClass().getSimpleName();

        }



        int pixelX = toPixelCoordinate(x);

        int pixelY = toPixelCoordinate(y);

        int pixelWidth = toPixel(width);

        int pixelHeight = toPixel(height);

        String target = normalizeUrl(url);



        if (!browserCreated) {

            try {

                long hwnd = resolveHwnd();

                if (hwnd == 0L) {

                    available = false;

                    diagnostics = "GLFW HWND resolve failed";

                    return;

                }



                browserCreated = nativeCreate(target, hwnd, pixelX, pixelY, pixelWidth, pixelHeight);

                if (!browserCreated) {

                    available = false;

                    diagnostics = "nativeCreate returned false: " + nativeGetLastError();

                    return;

                }



                lastUrl = target;

                lastPixelWidth = pixelWidth;

                lastPixelHeight = pixelHeight;

                lastPixelX = pixelX;

                lastPixelY = pixelY;

            } catch (Throwable throwable) {

                browserCreated = false;

                available = false;

                diagnostics = "nativeCreate exception: " + throwable.getClass().getSimpleName();

                return;

            }

        }



        try {

            boolean sizeChanged = pixelWidth != lastPixelWidth || pixelHeight != lastPixelHeight;

            boolean positionChanged = pixelX != lastPixelX || pixelY != lastPixelY;

            if (sizeChanged) {

                nativeResize(pixelWidth, pixelHeight);

            }

            if (sizeChanged || positionChanged) {

                nativeSetBounds(pixelX, pixelY, pixelWidth, pixelHeight);

                lastPixelX = pixelX;

                lastPixelY = pixelY;

                lastPixelWidth = pixelWidth;

                lastPixelHeight = pixelHeight;

            }

        } catch (Throwable throwable) {

            available = false;

            browserCreated = false;

            diagnostics = "nativeResize exception: " + throwable.getClass().getSimpleName();

        }

    }



    public static synchronized void prepareForScreenOpen() {

        closeCleanupTicks = 0;

        if (checked && available) {

            try {

                nativeClose();

            } catch (Throwable ignored) {

            }

        }

        browserCreated = false;

        resetBounds();

    }



    public static synchronized void closeBrowserKeepState() {

        if (browserCreated) {

            try {

                String current = nativeGetCurrentUrl();

                if (current != null && !current.isBlank()) {

                    lastUrl = current;

                }

            } catch (Throwable ignored) {

            }

        }

        forceNativeClose();

        closeCleanupTicks = CLOSE_CLEANUP_TICKS;

    }



    public static synchronized void clientTickCleanup() {

        if (closeCleanupTicks <= 0) {

            return;

        }

        forceNativeClose();

        closeCleanupTicks--;

    }



    private static void forceNativeClose() {

        if (checked && available) {

            try {

                nativeClose();

            } catch (Throwable ignored) {

            }

        }

        browserCreated = false;

        resetBounds();

    }



    private static void resetBounds() {

        lastPixelWidth = -1;

        lastPixelHeight = -1;

        lastPixelX = -1;

        lastPixelY = -1;

    }



    public static synchronized String getLastOrCurrentUrl() {

        if (browserCreated) {

            try {

                String current = nativeGetCurrentUrl();

                if (current != null && !current.isBlank()) {

                    lastUrl = current;

                }

            } catch (Throwable ignored) {

            }

        }

        return normalizeUrl(lastUrl);

    }



    public static synchronized void draw(double x1, double y1, double x2, double y2) {

        if (!browserCreated) {

            return;

        }



        try {

            Window window = Minecraft.getInstance().getWindow();

            double scale = window.getGuiScale();

            int pixelX = (int) Math.round(x1 * scale);

            int pixelY = (int) Math.round(y1 * scale);

            int pixelWidth = Math.max(1, (int) Math.round((x2 - x1) * scale));

            int pixelHeight = Math.max(1, (int) Math.round((y2 - y1) * scale));

            nativeSetBounds(pixelX, pixelY, pixelWidth, pixelHeight);

            lastPixelX = pixelX;

            lastPixelY = pixelY;

            lastPixelWidth = pixelWidth;

            lastPixelHeight = pixelHeight;

            nativeTick();

        } catch (Throwable throwable) {

            available = false;

            browserCreated = false;

            diagnostics = "nativeSetBounds/nativeTick exception: " + throwable.getClass().getSimpleName();

        }

    }



    public static synchronized void resize(int width, int height) {

        if (!browserCreated) {

            return;

        }



        try {

            int pixelWidth = toPixel(width);

            int pixelHeight = toPixel(height);

            if (pixelWidth != lastPixelWidth || pixelHeight != lastPixelHeight) {

                nativeResize(pixelWidth, pixelHeight);

                nativeSetBounds(Math.max(0, lastPixelX), Math.max(0, lastPixelY), pixelWidth, pixelHeight);

                lastPixelWidth = pixelWidth;

                lastPixelHeight = pixelHeight;

            }

        } catch (Throwable throwable) {

            available = false;

            browserCreated = false;

            diagnostics = "nativeResize exception: " + throwable.getClass().getSimpleName();

        }

    }



    public static synchronized void loadUrl(String url) {

        if (!browserCreated) {

            return;

        }



        String target = normalizeUrl(url);

        try {

            nativeLoadUrl(target);

            lastUrl = target;

        } catch (Throwable throwable) {

            available = false;

            browserCreated = false;

            diagnostics = "nativeLoadUrl exception: " + throwable.getClass().getSimpleName();

        }

    }



    public static synchronized void back() {

        if (!browserCreated) {

            return;

        }



        try {

            nativeGoBack();

        } catch (Throwable throwable) {

            available = false;

            browserCreated = false;

            diagnostics = "nativeGoBack exception: " + throwable.getClass().getSimpleName();

        }

    }



    public static synchronized void forward() {

        if (!browserCreated) {

            return;

        }



        try {

            nativeGoForward();

        } catch (Throwable throwable) {

            available = false;

            browserCreated = false;

            diagnostics = "nativeGoForward exception: " + throwable.getClass().getSimpleName();

        }

    }



    public static synchronized void reload() {

        if (!browserCreated) {

            return;

        }



        try {

            nativeReload();

        } catch (Throwable throwable) {

            available = false;

            browserCreated = false;

            diagnostics = "nativeReload exception: " + throwable.getClass().getSimpleName();

        }

    }



    public static synchronized void injectMouseMove(int x, int y, boolean outside) {

        if (!browserCreated) {

            return;

        }



        try {

            nativeMouseMove(x, y, outside);

        } catch (Throwable throwable) {

            available = false;

            browserCreated = false;

            diagnostics = "nativeMouseMove exception: " + throwable.getClass().getSimpleName();

        }

    }



    public static synchronized void injectMouseButton(int x, int y, int button, boolean pressed) {

        if (!browserCreated) {

            return;

        }



        try {

            nativeMouseButton(x, y, button, pressed);

        } catch (Throwable throwable) {

            available = false;

            browserCreated = false;

            diagnostics = "nativeMouseButton exception: " + throwable.getClass().getSimpleName();

        }

    }



    public static synchronized void injectMouseWheel(int x, int y, int rotation) {

        if (!browserCreated) {

            return;

        }



        try {

            nativeMouseWheel(x, y, rotation);

        } catch (Throwable throwable) {

            available = false;

            browserCreated = false;

            diagnostics = "nativeMouseWheel exception: " + throwable.getClass().getSimpleName();

        }

    }



    private static void ensureNativeLoaded() {

        if (checked) {

            return;

        }



        checked = true;

        if (!isWin64()) {

            available = false;

            diagnostics = "Unsupported OS/arch";

            return;

        }



        try {

            System.loadLibrary("tensura_patch_webview2");

            available = nativeIsSupported();

            diagnostics = available ? "Loaded via java.library.path" : "nativeIsSupported=false";

        } catch (Throwable throwable) {

            available = loadNativeFromJarOrKnownPaths();

        }

    }



    private static boolean isWin64() {

        String os = System.getProperty("os.name", "").toLowerCase();

        String arch = System.getProperty("os.arch", "").toLowerCase();

        return os.contains("win") && (arch.contains("64") || arch.contains("amd64") || arch.contains("x86_64"));

    }



    private static long resolveHwnd() {

        try {

            long glfwWindow = Minecraft.getInstance().getWindow().getWindow();

            return GLFWNativeWin32.glfwGetWin32Window(glfwWindow);

        } catch (Throwable throwable) {

            return 0L;

        }

    }



    private static int toPixel(int guiSize) {

        try {

            Window window = Minecraft.getInstance().getWindow();

            return Math.max(1, (int) Math.round(guiSize * window.getGuiScale()));

        } catch (Throwable throwable) {

            return Math.max(1, guiSize);

        }

    }



    private static String normalizeUrl(String raw) {

        String defaultUrl = BrowserClientConfig.homepage();

        if (raw == null || raw.isBlank()) {

            return defaultUrl;

        }



        String value = raw.trim();

        if (!value.startsWith("http://") && !value.startsWith("https://")) {

            return "https://" + value;

        }

        return value;

    }



    private static boolean loadNativeFromJarOrKnownPaths() {

        Path extracted = extractBundledNativeIfPresent();

        if (extracted != null) {

            try {

                System.load(extracted.toAbsolutePath().toString());

                boolean supported = nativeIsSupported();

                diagnostics = supported ? "Loaded bundled DLL: " + extracted.toAbsolutePath() : "Bundled DLL loaded but nativeIsSupported=false: " + extracted.toAbsolutePath();

                return supported;

            } catch (Throwable throwable) {

                diagnostics = "Bundled DLL load failed " + extracted.toAbsolutePath() + " : " + throwable.getClass().getSimpleName();

            }

        }



        Path[] candidates = new Path[] {

            Paths.get("tensura_patch_webview2.dll"),

            Paths.get("native", "webview2", "build", "Release", "tensura_patch_webview2.dll"),

            Paths.get("run", "tensura_patch_webview2.dll"),

            Paths.get("..", "tensura_patch_webview2.dll")

        };



        for (Path candidate : candidates) {

            try {

                if (!Files.exists(candidate)) {

                    continue;

                }



                System.load(candidate.toAbsolutePath().toString());

                boolean supported = nativeIsSupported();

                diagnostics = supported ? "Loaded: " + candidate.toAbsolutePath() : "Loaded but nativeIsSupported=false: " + candidate.toAbsolutePath();

                return supported;

            } catch (Throwable throwable) {

                diagnostics = "Load failed " + candidate.toAbsolutePath() + " : " + throwable.getClass().getSimpleName();

            }

        }



        diagnostics = "DLL not found";

        return false;

    }



    private static Path extractBundledNativeIfPresent() {

        try (InputStream in = WebView2BrowserSession.class.getResourceAsStream(NATIVE_RESOURCE_PATH)) {

            if (in == null) {

                return null;

            }



            Path outDir = Paths.get(".tensura_tno", "native", "win64");

            Files.createDirectories(outDir);

            Path outFile = outDir.resolve("tensura_patch_webview2.dll");

            Files.copy(in, outFile, StandardCopyOption.REPLACE_EXISTING);

            return outFile;

        } catch (Throwable throwable) {

            return null;

        }

    }



    private static int toPixelCoordinate(int guiCoordinate) {

        try {

            Window window = Minecraft.getInstance().getWindow();

            return Math.max(0, (int) Math.round(guiCoordinate * window.getGuiScale()));

        } catch (Throwable throwable) {

            return Math.max(0, guiCoordinate);

        }

    }



    private static native boolean nativeIsSupported();

    private static native boolean nativeCreate(String url, long hwnd, int x, int y, int width, int height);

    private static native void nativeResize(int width, int height);

    private static native void nativeSetBounds(int x, int y, int width, int height);

    private static native void nativeTick();

    private static native void nativeLoadUrl(String url);

    private static native void nativeGoBack();

    private static native void nativeGoForward();

    private static native void nativeReload();

    private static native String nativeGetCurrentUrl();

    private static native void nativeMouseMove(int x, int y, boolean outside);

    private static native void nativeMouseButton(int x, int y, int button, boolean pressed);

    private static native void nativeMouseWheel(int x, int y, int rotation);

    private static native void nativeClose();

    private static native String nativeGetLastError();

    private static native void nativeConfigure(boolean blockNewWindows, boolean removeWorkbenchTitlebar, boolean removeTransitionSpace);

}


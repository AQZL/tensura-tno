package com.tensura_tno.client.browser;

import com.mojang.blaze3d.platform.Window;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

public final class BrowserDisplayMode {
    private static final int FULLSCREEN_TOLERANCE_PX = 8;

    private BrowserDisplayMode() {
    }

    public static boolean isFullscreenLike(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }

        Window window = minecraft.getWindow();
        if (window == null) {
            return false;
        }
        if (window.isFullscreen()) {
            return true;
        }

        return isBorderlessFullscreenWindow(window);
    }

    private static boolean isBorderlessFullscreenWindow(Window window) {
        try {
            long handle = window.getWindow();
            if (handle == 0L) {
                return false;
            }

            WindowRect windowRect = readWindowRect(handle);
            PointerBuffer monitors = GLFW.glfwGetMonitors();
            if (monitors == null) {
                return false;
            }

            for (int i = 0; i < monitors.limit(); i++) {
                long monitor = monitors.get(i);
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                if (mode == null) {
                    continue;
                }

                IntBuffer monitorX = BufferUtils.createIntBuffer(1);
                IntBuffer monitorY = BufferUtils.createIntBuffer(1);
                GLFW.glfwGetMonitorPos(monitor, monitorX, monitorY);
                WindowRect fullMonitor = new WindowRect(
                        monitorX.get(0),
                        monitorY.get(0),
                        mode.width(),
                        mode.height());

                if (sameRect(windowRect, fullMonitor) || sameRect(windowRect, readWorkArea(monitor))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }

        return false;
    }

    private static WindowRect readWindowRect(long handle) {
        IntBuffer x = BufferUtils.createIntBuffer(1);
        IntBuffer y = BufferUtils.createIntBuffer(1);
        IntBuffer width = BufferUtils.createIntBuffer(1);
        IntBuffer height = BufferUtils.createIntBuffer(1);
        GLFW.glfwGetWindowPos(handle, x, y);
        GLFW.glfwGetWindowSize(handle, width, height);
        return new WindowRect(x.get(0), y.get(0), width.get(0), height.get(0));
    }

    private static WindowRect readWorkArea(long monitor) {
        IntBuffer x = BufferUtils.createIntBuffer(1);
        IntBuffer y = BufferUtils.createIntBuffer(1);
        IntBuffer width = BufferUtils.createIntBuffer(1);
        IntBuffer height = BufferUtils.createIntBuffer(1);
        GLFW.glfwGetMonitorWorkarea(monitor, x, y, width, height);
        return new WindowRect(x.get(0), y.get(0), width.get(0), height.get(0));
    }

    private static boolean sameRect(WindowRect a, WindowRect b) {
        return close(a.x(), b.x())
                && close(a.y(), b.y())
                && close(a.width(), b.width())
                && close(a.height(), b.height());
    }

    private static boolean close(int a, int b) {
        return Math.abs(a - b) <= FULLSCREEN_TOLERANCE_PX;
    }

    private record WindowRect(int x, int y, int width, int height) {
    }
}

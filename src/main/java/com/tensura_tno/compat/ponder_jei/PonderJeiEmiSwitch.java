package com.tensura_tno.compat.ponder_jei;

import com.tensura_tno.util.PondererScreenIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 在 Ponder / Ponderer 主界面时: 让 EMI 不占用侧边栏, 并把 JEI 运行时代码里
 * 被 EMI 替换掉的 {@code Jemi*} 桥接回真正的 JEI overlay 实例。
 */
public final class PonderJeiEmiSwitch {

    private static volatile Object realIngredientListOverlay;
    private static volatile Object realBookmarkOverlay;

    private PonderJeiEmiSwitch() {
    }

    public static void setRealIngredientListOverlay(Object overlay) {
        if (overlay == null) {
            return;
        }
        String name = overlay.getClass().getName();
        if (name.equals("mezz.jei.gui.overlay.IngredientListOverlay")) {
            realIngredientListOverlay = overlay;
        }
    }

    public static void setRealBookmarkOverlay(Object overlay) {
        if (overlay == null) {
            return;
        }
        String name = overlay.getClass().getName();
        if (name.equals("mezz.jei.gui.overlay.bookmarks.BookmarkOverlay")) {
            realBookmarkOverlay = overlay;
        }
    }

    public static boolean isPonderMainScreen(Screen screen) {
        return PondererScreenIds.isPonderUI(screen);
    }

    /** 当前顶层 GUI 为思索/ponderer 时视为 Ponder 上下文 (含镜像容器界面). */
    public static boolean inPonderGuiContext() {
        Minecraft mc = Minecraft.getInstance();
        return isPonderMainScreen(mc.screen);
    }

    public static Object getRealIngredientListOverlay() {
        return realIngredientListOverlay;
    }

    public static Object getRealBookmarkOverlay() {
        return realBookmarkOverlay;
    }
}

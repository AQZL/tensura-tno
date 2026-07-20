package com.tensura_tno.util;

import net.minecraft.client.gui.screens.Screen;

/**
 * 集中判断"当前 Screen 是否属于 Create Ponder / Ponderer 系列界面"的工具类。
 *
 * <p>历史上同样的 startsWith 判断散落在三个文件里:
 * <ul>
 *   <li>{@code com.tensura_tno.compat.ponder_jei.PonderJeiEmiSwitch}</li>
 *   <li>{@code com.tensura_tno.mixin.client.FTBLibrarySidebarSuppressOnPonderUIMixin}</li>
 *   <li>{@code com.tensura_tno.mixin.client.FTBQuestsHudSuppressOnPonderUIMixin}</li>
 * </ul>
 * 现统一收口到本类的 {@link #isPonderUI(String)} / {@link #isPonderUI(Screen)},
 * 后续如需扩展 (例如 Ponder 系列再多一个包名前缀), 改这一处即可。</p>
 *
 * <p>本类不依赖任何第三方 mod 的类, 因此可以被普通工具类 / mixin 类共同安全引用,
 * 不会引入 mixin-only package 误用 (即"普通代码引用 mixin 包内的类导致
 * {@code IllegalClassLoadError}") 之类的风险。</p>
 */
public final class PondererScreenIds {

    /** Create Ponder 自身界面 ({@code PonderUI} / {@code PonderTagScreen} 等) 的包名前缀。 */
    private static final String PONDER_PACKAGE_PREFIX = "net.createmod.ponder.";

    /** Ponderer 扩展模组的包名前缀。 */
    private static final String PONDERER_PACKAGE_PREFIX = "com.nododiiiii.ponderer.";

    private PondererScreenIds() {
    }

    /**
     * @param className Screen 实例的全限定类名
     * @return 是否属于 Create Ponder 或 Ponderer 系列界面
     */
    public static boolean isPonderUI(String className) {
        return className != null
            && (className.startsWith(PONDER_PACKAGE_PREFIX)
                || className.startsWith(PONDERER_PACKAGE_PREFIX));
    }

    /**
     * @param screen 当前屏幕实例, 允许为 null (返回 false)
     * @return 是否属于 Create Ponder 或 Ponderer 系列界面
     */
    public static boolean isPonderUI(Screen screen) {
        return screen != null && isPonderUI(screen.getClass().getName());
    }
}

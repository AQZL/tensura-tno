package com.tensura_tno.mixin.client;

import com.tensura_tno.TensuraTNOMod;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修掉 Create Ponder 的"思索 - Show Interface"镜像屏把 FTB Library 的侧栏按钮组
 * {@code SidebarGroupGuiButton} 一起渲染进 ponder 画面的问题。
 *
 * <p>链路: Ponderer 的 {@code ClientInputHandler.attachMirrorToPonder(mirrorScreen)}
 * 会调 {@code mirrorScreen.init(...)}, 这一步触发 Architectury 的
 * {@code ClientGuiEvent.INIT_POST} → FTB Library 的
 * {@code FTBLibraryClient#guiInit} → 把 {@code SidebarGroupGuiButton} 挂到
 * 镜像屏 widget 列表里。随后 Ponderer 只调了
 * {@code stripJeiWidgets(mirrorScreen)} 清 JEI 注入, 完全没动 FTB 的按钮,
 * 所以镜像屏后续渲染时, FTB 侧栏就作为镜像的一部分被画到思索画面左上角。</p>
 *
 * <p>本 Mixin 在 {@code attachMirrorToPonder} 的 TAIL 注入一段清理逻辑:
 * 通过反射读取 {@code Screen} 的 {@code children / renderables / narratables}
 * 三个内部列表, 把所有 class name 以 {@code dev.ftb.mods.} 开头的 widget
 * (涵盖 ftb-library / ftb-quests / ftb-teams 等任何 FTB 家族注入的 widget)
 * 移除。这样镜像屏画出来只剩它本身的槽位和容器 UI。</p>
 *
 * <p>{@code targets = "..."} 字符串 + {@code require = 0}:
 * Ponderer 没装或方法名变更时 Mixin 静默跳过, 不会阻止游戏启动。</p>
 */
@Mixin(
    targets = "com.nododiiiii.ponderer.neoforge.sticksnapshot.client.ClientInputHandler",
    remap = false
)
public class PondererEmbeddedMirrorStripFTBWidgetsMixin {

    @Inject(
        method = "attachMirrorToPonder(Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At("TAIL"),
        remap = false,
        require = 0
    )
    private static void tensuraTno$stripFtbWidgets(Screen mirrorScreen, CallbackInfo ci) {
        if (mirrorScreen == null) {
            return;
        }
        try {
            tensuraTno$removeFtbFromList(mirrorScreen.children());
            tensuraTno$removeFtbFromList(tensuraTno$readScreenListField(mirrorScreen, "renderables"));
            tensuraTno$removeFtbFromList(tensuraTno$readScreenListField(mirrorScreen, "narratables"));
        } catch (Throwable t) {
            TensuraTNOMod.LOGGER.debug("[tensura_tno] 镜像屏剥 FTB widget 失败: {}", t.toString());
        }
    }

    private static void tensuraTno$removeFtbFromList(List<?> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        list.removeIf(entry -> {
            if (entry == null) {
                return false;
            }
            String name = entry.getClass().getName();
            return name.startsWith("dev.ftb.mods.");
        });
    }

    private static List<?> tensuraTno$readScreenListField(Screen screen, String fieldName) {
        try {
            Field field = Screen.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(screen);
            if (value instanceof List<?> list) {
                return list;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}

package com.tensura_tno.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Ponderer 表单界面 ({@code CommandParamScreen} / {@code FunctionScreen} 等) 的两类已知崩溃:
 *
 * <h3>1) 编辑文本时的 StackOverflowError</h3>
 * <pre>
 *   handleTextChanged
 *     → rebuildListPreservingScroll
 *       → rebuildEntries
 *         → collectEntries
 *           → FieldSpecs.attachWidget
 *             → EditBox.setValue(初始值)
 *               → EditBox.onValueChange
 *                 → FieldBindings$SimpleFieldBinding.set
 *                   → handleTextChanged   ← 回到起点, 无限递归
 * </pre>
 * 原因: {@code CommandParamScreen} 用 {@code collectingEntries} 闭锁防递归, 但它在子类
 * {@code collectFormEntries} 的 finally 里就被置回 false, 而真正调用
 * {@code attachWidget → setValue} 是在基类
 * {@code AbstractDeclarativeFormScreen.collectEntries} 后续构造 widget 阶段, 此时守卫已失效。
 *
 * <h3>2) 打开页面时的 ConcurrentModificationException (本次崩溃)</h3>
 * <pre>
 *   FunctionScreen 按钮回调
 *     Minecraft.setScreen(new CommandParamScreen)
 *       AbstractDeclarativeListScreen.init
 *         rebuildEntries(null)                        ← 外层
 *           AbstractDeclarativeFormScreen.collectEntries
 *             for (DeclarativeFormEntry e : formEntries) {  ← 外层 ArrayList.Itr
 *                e.build(this)
 *                  attachWidget → EditBox.setValue
 *                    → handleTextChanged
 *                      → rebuildListPreservingScroll
 *                        → rebuildEntries(...)        ← 内层
 *                          → formEntries.clear()      ← CME
 *             }
 * </pre>
 * 原因: 外层入口是 {@code init() → rebuildEntries(null)}, 根本没经过
 * {@code rebuildListPreservingScroll}, 所以只在 {@code rebuildListPreservingScroll}
 * 上设防的旧版守卫不会生效。当 {@code item_id} / {@code nbt} 等带
 * {@code addFieldDisablesToggle(...)} 的字段在初始 {@code setValue} 时触发 responder,
 * {@code handleTextChanged} 调 {@code rebuildListPreservingScroll} → 内层 {@code rebuildEntries}
 * 清空并重填 {@code formEntries}, 外层迭代器随即 modCount 失配。
 *
 * <h3>修复策略</h3>
 * 把重入保护下沉到所有重建路径的汇聚点 {@code rebuildEntries(Ljava/lang/Double;)V}。
 * 任何形式的二次重建 (无论来自 {@code rebuildListPreservingScroll}、
 * {@code rebuildListWithRememberedScroll}、{@code rebuildListAtTop}, 还是直接 {@code rebuildEntries})
 * 在外层重建未结束前都会被静默吞掉, 同时切断递归与并发修改两条路径。
 *
 * <p>{@code targets = "..."} 字符串 + {@code require = 0}: Ponderer 没装时
 * Mixin 静默不加载, 不会影响启动。</p>
 */
@Mixin(
    targets = "com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen",
    remap = false
)
public abstract class PondererListRebuildReentryGuardMixin {

    @Unique
    private boolean tensuraTno$rebuildInProgress = false;

    @Inject(
        method = "rebuildEntries(Ljava/lang/Double;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void tensuraTno$skipIfAlreadyRebuilding(Double preservedScroll, CallbackInfo ci) {
        if (tensuraTno$rebuildInProgress) {
            ci.cancel();
            return;
        }
        tensuraTno$rebuildInProgress = true;
    }

    @Inject(
        method = "rebuildEntries(Ljava/lang/Double;)V",
        at = @At("RETURN"),
        remap = false,
        require = 0
    )
    private void tensuraTno$clearFlagOnReturn(Double preservedScroll, CallbackInfo ci) {
        tensuraTno$rebuildInProgress = false;
    }
}

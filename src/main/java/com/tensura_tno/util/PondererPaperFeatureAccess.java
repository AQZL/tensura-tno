package com.tensura_tno.util;

/**
 * 全局开关: 是否允许 Ponderer 把 "普通物品" (默认 minecraft:paper, 或客户端配置的其他 carrier 物品)
 * 接管为"圈结构 / 蓝图载体"和"思索教程载体"。
 *
 * <p>默认关闭。配套命令:
 * <pre>
 *   /tno admin ponderer_paper [true|false]
 * </pre>
 *
 * <p>对应的 mixin:
 * <ul>
 *   <li>{@code PondererBlueprintCarrierGuardMixin} - 切断圈结构功能的运行时入口
 *       (拦 {@code BlueprintFeature.matchesCarrierStack}); 切换后立即生效。</li>
 *   <li>{@code PondererBlueprintGuideSceneSuppressionMixin} - 阻止 paper 等 carrier
 *       被注册到 ponder 系统; 切换后需要执行一次 ponderer 的 reload (功能页面里的
 *       "重载"按钮) 才会重新生效。</li>
 * </ul>
 *
 * <p>注意: 当 server 启用了 ponderer 自带的 {@code ponderer:blueprint} 物品 (通过
 * {@code Config.ENABLE_BLUEPRINT_ITEM}) 并且客户端把 carrier 设为它时, 守卫不会拦截 ——
 * 那是用户主动启用了 ponderer 自家物品, 不应被本开关一并禁用。</p>
 */
public final class PondererPaperFeatureAccess {
    private static volatile boolean enabled = false;

    private PondererPaperFeatureAccess() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}

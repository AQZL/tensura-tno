package com.tensura_tno.client;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 桥接补丁：让"对局域网开放"按钮打开 LanServerProperties 的 GUI，
 * 同时在其中添加联机可见性 CycleButton（局域网 / 好友）。
 *
 * 问题根源：P2P 模组在 NORMAL 优先级的 ScreenEvent.Init.Post 中把暂停菜单的
 * "对局域网开放"按钮替换为打开自己的 MultiplayerOptionsScreen。
 * 本处理器以 LOW 优先级运行（在 P2P 之后），将该按钮改回打开原版 ShareToLanScreen，
 * 使 LanServerProperties 的 Mixin 能正常注入生效。
 */
@EventBusSubscriber(modid = "tensura_tno", value = Dist.CLIENT)
public final class LanP2PBridgeClient {

    private LanP2PBridgeClient() {}

    private static boolean p2pModeRequested = false;

    /**
     * 复用 P2P 模组自带的翻译键（已含中文），仅用作按钮值标签和 tooltip。
     * 对应 HostVisibility.Visibility 的两个有意义状态。
     */
    private enum P2PMode {
        LAN    ("gui.flistp2p.multiplayer_options.visibility.lan"),
        FRIENDS("gui.flistp2p.multiplayer_options.visibility.friends");

        final String key;
        P2PMode(String key) { this.key = key; }
        Component label()   { return Component.translatable(key); }
        Tooltip   tooltip() { return Tooltip.create(Component.translatable(key + ".tooltip")); }
    }

    // -----------------------------------------------------------------------
    // 1. 暂停菜单：将 P2P 模组替换掉的"对局域网开放"按钮改回打开 ShareToLanScreen
    // -----------------------------------------------------------------------
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPauseScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.hasSingleplayerServer()) return;

        // P2P 激活：若好友模式已请求且服务器现已发布
        if (p2pModeRequested) {
            var server = mc.getSingleplayerServer();
            if (server != null && server.isPublished()) {
                p2pModeRequested = false;
                activateP2P();
            }
        }

        // 移除 LSP 添加的"局域网服务器配置"图标按钮
        Button lanSettingsBtn = findButtonByKey(event.getListenersList(), "lanserverproperties.button.lan_server_options");
        if (lanSettingsBtn != null) event.removeListener(lanSettingsBtn);

        Button shareToLanBtn = findButtonByKey(event.getListenersList(), "menu.shareToLan");
        if (shareToLanBtn == null) return;

        int x = shareToLanBtn.getX(), y = shareToLanBtn.getY();
        int w = shareToLanBtn.getWidth(), h = shareToLanBtn.getHeight();
        event.removeListener(shareToLanBtn);
        PauseScreen pause = (PauseScreen) event.getScreen();
        event.addListener(
            Button.builder(Component.translatable("menu.shareToLan"),
                (b) -> mc.setScreen(new ShareToLanScreen(pause))
            ).bounds(x, y, w, h).build()
        );
    }

    // -----------------------------------------------------------------------
    // 2. ShareToLanScreen：注入联机可见性 CycleButton（LOW = 在 LSP 添加控件之后）
    // -----------------------------------------------------------------------
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onShareToLanScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ShareToLanScreen)) return;

        p2pModeRequested = false;

        var spServer = Minecraft.getInstance().getSingleplayerServer();
        boolean published = spServer != null && spServer.isPublished();

        if (published) {
            // 已发布：锁定除"游戏模式"和"允许命令"外的所有控件，将开始按钮换成"完成"
            configurePostPublishScreen(event);
        } else if (ModList.get().isLoaded("flistp2p")) {
            // 未发布且 P2P 模组存在：添加 LAN/FRIENDS 切换器（FRIENDS 强制正版+8人）
            configurePrePublishScreen(event);
        }
    }

    /**
     * 未发布状态：添加联机可见性 CycleButton。
     * 选择"好友"模式（P2P）时强制：
     *   - 在线模式 = 开（正版玩家校验）
     *   - 最大人数 = 8（固定）
     */
    private static void configurePrePublishScreen(ScreenEvent.Init.Post event) {
        CycleButton<?> onlineModeBtn = findOnlineModeButton(event.getListenersList());
        EditBox        maxPlayerBox  = findMaxPlayerEditBox(event.getListenersList());

        if (onlineModeBtn == null || maxPlayerBox == null) return;

        // 记录进入界面时的原始状态，供切回"局域网"模式时恢复
        final boolean[] origOnlineActive = { onlineModeBtn.active };
        final String[]  origMaxPlayer    = { maxPlayerBox.getValue() };

        // 位置锚定在"在线模式"按钮正下方（+24px 跨过 alwaysOfflines 输入框的高度），宽度与之一致
        int bx = onlineModeBtn.getX();
        int by = onlineModeBtn.getY() + onlineModeBtn.getHeight() + 24;
        int bw = onlineModeBtn.getWidth();
        Component label = Component.translatable("tensura_tno.gui.lan_p2p_mode");
        CycleButton<P2PMode> p2pToggle = CycleButton
            .<P2PMode>builder(P2PMode::label)
            .withValues(P2PMode.LAN, P2PMode.FRIENDS)
            .withInitialValue(P2PMode.LAN)
            .withTooltip(P2PMode::tooltip)
            .create(bx, by, bw, 20, label,
                (btn, mode) -> {
                    if (mode == P2PMode.FRIENDS) {
                        // P2P 前提：正版玩家 + 8 人固定人数
                        p2pModeRequested = true;
                        for (int i = 0; i < 3; i++) {
                            if (isOnlineModeEnabled(onlineModeBtn.getMessage())) break;
                            onlineModeBtn.onPress();
                        }
                        onlineModeBtn.active = false;
                        maxPlayerBox.setValue("8");
                        maxPlayerBox.setEditable(false);
                    } else {
                        // 切回局域网模式：恢复原始状态
                        p2pModeRequested = false;
                        onlineModeBtn.active = origOnlineActive[0];
                        maxPlayerBox.setEditable(true);
                        maxPlayerBox.setValue(origMaxPlayer[0]);
                    }
                }
            );
        event.addListener(p2pToggle);
    }

    /**
     * 已发布状态：仿照原版局域网联机的"已发布后"行为。
     * 仅"游戏模式"和"允许命令"两个 CycleButton 可改，其他全部锁定/禁用。
     * "开始局域网游戏"按钮替换为"完成"——点击后将仅游戏模式+允许命令应用到运行中服务器并关闭界面。
     */
    private static void configurePostPublishScreen(ScreenEvent.Init.Post event) {
        var list = event.getListenersList();
        var srv = Minecraft.getInstance().getSingleplayerServer();
        if (srv == null) return;

        // 1. 锁定所有 EditBox（端口、最大人数、alwaysOffline）
        for (GuiEventListener w : list) {
            if (w instanceof EditBox eb) {
                eb.setEditable(false);
            }
        }

        // 2. 锁定 LSP 添加的 CycleButton（在线模式、PvP、preference 启用开关）
        String[] cycleKeyPrefixes = {
            "lanserverproperties.options.online_mode",
            "lanserverproperties.gui.pvp_allowed",
            "lanserverproperties.options.preference_enabled"
        };
        for (GuiEventListener w : list) {
            if (w instanceof CycleButton<?> cb) {
                Component msg = cb.getMessage();
                if (msg.getContents() instanceof TranslatableContents tc) {
                    String key = tc.getKey();
                    for (String prefix : cycleKeyPrefixes) {
                        if (key.startsWith(prefix)) {
                            cb.active = false;
                            break;
                        }
                    }
                }
            }
        }

        // 3. 锁定 LSP 添加的 Button（保存/读取 preference、alwaysOffline 显示切换）
        String[] btnKeysToLock = {
            "lanserverproperties.button.preference_save",
            "lanserverproperties.button.preference_load",
            "lanserverproperties.gui.always_offline"
        };
        for (GuiEventListener w : list) {
            if (w instanceof Button btn) {
                Component msg = btn.getMessage();
                if (msg.getContents() instanceof TranslatableContents tc) {
                    String key = tc.getKey();
                    for (String lockKey : btnKeysToLock) {
                        if (key.equals(lockKey)) {
                            btn.active = false;
                            break;
                        }
                    }
                }
            }
        }

        // 4. 替换"开始局域网游戏"按钮为"完成"，点击后仅应用游戏模式+允许命令
        Button startBtn = findButtonByKey(list, "lanServer.start");
        if (startBtn == null) return;

        CycleButton<?> gameModeBtn  = findCycleButtonByKey(list, "selectWorld.gameMode");
        CycleButton<?> allowCmdsBtn = findCycleButtonByKey(list, "selectWorld.allowCommands.new");
        if (allowCmdsBtn == null) allowCmdsBtn = findCycleButtonByKey(list, "selectWorld.allowCommands");

        int sx = startBtn.getX(), sy = startBtn.getY(), sw = startBtn.getWidth(), sh = startBtn.getHeight();
        event.removeListener(startBtn);

        final CycleButton<?> finalGmBtn = gameModeBtn;
        final CycleButton<?> finalAcBtn = allowCmdsBtn;
        final var finalSrv = srv;
        final ShareToLanScreen capturedScreen = (ShareToLanScreen) event.getScreen();

        event.addListener(
            Button.builder(CommonComponents.GUI_DONE, (b) -> {
                // 应用游戏模式
                if (finalGmBtn != null && finalGmBtn.getValue() instanceof GameType gt) {
                    finalSrv.setDefaultGameType(gt);
                }
                // 应用允许命令
                if (finalAcBtn != null && finalAcBtn.getValue() instanceof Boolean ac) {
                    finalSrv.getPlayerList().setAllowCommandsForAllPlayers(ac);
                }
                capturedScreen.onClose();
            }).bounds(sx, sy, sw, sh).build()
        );
    }

    // -----------------------------------------------------------------------
    // 内部工具
    // -----------------------------------------------------------------------

    private static void activateP2P() {
        try {
            Class<?> cls = Class.forName("com.juanmuscaria.flistp2p.client.FlistP2PClient");
            Method m = cls.getMethod("setHostingP2P", boolean.class);
            m.invoke(null, true);
        } catch (Exception ignored) {
            // P2P 模组未安装，忽略
        }
    }

    private static Button findButtonByKey(List<? extends GuiEventListener> list, String key) {
        for (GuiEventListener w : list) {
            if (w instanceof Button btn) {
                Component msg = btn.getMessage();
                if (msg.getContents() instanceof TranslatableContents tc && tc.getKey().equals(key)) {
                    return btn;
                }
            }
        }
        return null;
    }

    private static CycleButton<?> findOnlineModeButton(List<? extends GuiEventListener> list) {
        for (GuiEventListener w : list) {
            if (w instanceof CycleButton<?> cb) {
                Component msg = cb.getMessage();
                if (msg.getContents() instanceof TranslatableContents tc
                        && tc.getKey().startsWith("lanserverproperties.options.online_mode")) {
                    return cb;
                }
            }
        }
        return null;
    }

    private static EditBox findMaxPlayerEditBox(List<? extends GuiEventListener> list) {
        for (GuiEventListener w : list) {
            // IntegerEditBox extends EditBox, 用类名区分（避免编译期依赖 LSP）
            if (w instanceof EditBox eb && eb.getClass().getSimpleName().equals("IntegerEditBox")) {
                return eb;
            }
        }
        return null;
    }

    private static CycleButton<?> findCycleButtonByKey(List<? extends GuiEventListener> list, String key) {
        for (GuiEventListener w : list) {
            if (w instanceof CycleButton<?> cb) {
                Component msg = cb.getMessage();
                if (msg.getContents() instanceof TranslatableContents tc && tc.getKey().equals(key)) {
                    return cb;
                }
            }
        }
        return null;
    }

    private static boolean isOnlineModeEnabled(Component msg) {
        return msg.getContents() instanceof TranslatableContents tc
                && tc.getKey().equals("lanserverproperties.options.online_mode.on");
    }
}

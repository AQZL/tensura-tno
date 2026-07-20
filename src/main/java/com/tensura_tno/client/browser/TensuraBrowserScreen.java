package com.tensura_tno.client.browser;



import java.util.List;

import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.components.Button;

import net.minecraft.client.gui.screens.Screen;

import net.minecraft.network.chat.Component;

import net.minecraft.util.FormattedCharSequence;



public class TensuraBrowserScreen extends Screen {

    private static final String TITLE_KEY = "tensura_tno.wiki.title";

    private static final int TOOLBAR_HEIGHT = 24;

    private static final int CLOSE_BUTTON_WIDTH = 44;

    private static final int CLOSE_BUTTON_HEIGHT = 16;

    private static final int CLOSE_BUTTON_RIGHT_MARGIN = 6;

    private static final int CLOSE_BUTTON_TOP = 4;



    private static boolean firstOpenThisLaunch = true;



    private final Screen parent;

    private boolean usedNativeBrowserLastFrame;

    private boolean nativeBrowserCreationArmed;

    private boolean nativeBrowserInitialLoadPending;

    private boolean closing;

    private String startupUrl = "";

    private Button backButton;

    private Button forwardButton;

    private Button reloadButton;

    private Button closeButton;



    public TensuraBrowserScreen(Screen parent) {

        super(Component.translatable(TITLE_KEY));

        this.parent = parent;

    }



    @Override

    protected void init() {

        this.backButton = this.addRenderableWidget(new SkinnedButton(6, 4, 20, 16, Component.literal("<"), button -> {

            if (this.useNativeBrowser()) {

                WebView2BrowserSession.back();

            } else {

                SimpleBrowserSession.back();

            }

            this.updateNavButtons();

        }));

        this.forwardButton = this.addRenderableWidget(new SkinnedButton(28, 4, 20, 16, Component.literal(">"), button -> {

            if (this.useNativeBrowser()) {

                WebView2BrowserSession.forward();

            } else {

                SimpleBrowserSession.forward();

            }

            this.updateNavButtons();

        }));

        this.reloadButton = this.addRenderableWidget(new SkinnedButton(50, 4, 30, 16, Component.translatable("tensura_tno.wiki.button.reload"), button -> {

            if (this.useNativeBrowser()) {

                WebView2BrowserSession.reload();

            } else {

                SimpleBrowserSession.reload();

            }

        }));

        this.closeButton = this.addRenderableWidget(new SkinnedButton(this.closeButtonLeft(), CLOSE_BUTTON_TOP, CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT, Component.translatable("tensura_tno.wiki.button.close"), button -> this.onClose()));



        boolean forceFirstOpen = firstOpenThisLaunch;

        String startupUrl = WebView2BrowserSession.getLastOrCurrentUrl();

        if (forceFirstOpen && BrowserClientConfig.forceFirstOpenUrl()) {

            startupUrl = BrowserClientConfig.firstOpenUrl();

        }

        if (startupUrl == null || startupUrl.isBlank() || BrowserClientConfig.isManagedWikiUrl(startupUrl)) {

            startupUrl = BrowserClientConfig.defaultWikiUrl();

        }

        if (startupUrl == null || startupUrl.isBlank()) {

            startupUrl = BrowserClientConfig.homepage();

        }



        if (forceFirstOpen) {

            firstOpenThisLaunch = false;

        }



        this.usedNativeBrowserLastFrame = this.useNativeBrowser();

        this.startupUrl = startupUrl;

        this.nativeBrowserCreationArmed = false;

        this.nativeBrowserInitialLoadPending = this.usedNativeBrowserLastFrame;

        if (this.usedNativeBrowserLastFrame) {

            WebView2BrowserSession.prepareForScreenOpen();

        } else {

            WebView2BrowserSession.closeBrowserKeepState();

            this.ensureFallbackSession(startupUrl, true);

        }



        this.updateNavButtons();

    }



    @Override

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        if (keyCode == 256) {

            this.onClose();

            return true;

        }

        return super.keyPressed(keyCode, scanCode, modifiers);

    }



    @Override

    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (button == 0 && this.overCloseButton(mouseX, mouseY)) {

            this.onClose();

            return true;

        }

        if (super.mouseClicked(mouseX, mouseY, button)) {

            return true;

        }

        if (!this.inContentArea(mouseX, mouseY)) {

            return false;

        }

        if (this.useNativeBrowser()) {

            int contentX = (int) mouseX;

            int contentY = (int) mouseY - TOOLBAR_HEIGHT;

            WebView2BrowserSession.injectMouseButton(contentX, contentY, button, true);

            WebView2BrowserSession.injectMouseButton(contentX, contentY, button, false);

            return true;

        }

        return false;

    }



    @Override

    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {

        if (!this.inContentArea(mouseX, mouseY)) {

            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);

        }

        if (this.useNativeBrowser()) {

            int contentX = (int) mouseX;

            int contentY = (int) mouseY - TOOLBAR_HEIGHT;

            WebView2BrowserSession.injectMouseWheel(contentX, contentY, deltaY > 0.0D ? -120 : 120);

        } else {

            SimpleBrowserSession.changeScroll(deltaY > 0.0D ? -1 : 1);

        }

        return true;

    }



    @Override

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

        this.syncBrowserBackend();



        graphics.fill(0, 0, this.width, TOOLBAR_HEIGHT, 0xD0101010);

        graphics.fill(0, TOOLBAR_HEIGHT, this.width, this.height, 0xFF000000 | SimpleBrowserSession.bodyBackgroundColor());

        graphics.drawString(this.font, this.title, 6, 28, 0xFFFFFF, false);



        Component status = this.statusMessage();

        graphics.drawString(this.font, status, 70, 28, 0x90DFFF, false);



        boolean useNativeBrowser = this.useNativeBrowser();

        if (useNativeBrowser && this.nativeBrowserCreationArmed) {

            this.ensureNativeBrowserReady();

            boolean outside = !this.inContentArea(mouseX, mouseY);

            WebView2BrowserSession.injectMouseMove(mouseX, mouseY - TOOLBAR_HEIGHT, outside);

            WebView2BrowserSession.draw(0.0D, TOOLBAR_HEIGHT, this.width, this.height);

        } else {

            this.renderFallback(graphics);

        }



        super.render(graphics, mouseX, mouseY, partialTick);

        if (useNativeBrowser && !this.nativeBrowserCreationArmed && !this.closing) {

            this.nativeBrowserCreationArmed = true;

        }

    }



    @Override

    public void resize(Minecraft minecraft, int width, int height) {

        super.resize(minecraft, width, height);

        if (this.useNativeBrowser()) {

            WebView2BrowserSession.resize(width, this.contentHeight(height));

        }

    }



    @Override

    public void removed() {

        this.closeNativeBrowser();

        SimpleBrowserSession.onScreenClosed();

        super.removed();

    }



    @Override

    public void onClose() {

        this.closeNativeBrowser();

        if (this.minecraft != null) {

            this.minecraft.setScreen(this.parent);

        } else {

            super.onClose();

        }

    }



    private void renderFallback(GuiGraphics graphics) {

        int x = 6;

        int y = 40;

        int textWidth = Math.max(40, this.width - 12);



        // WebView2 真正不可用时（DLL 缺失/系统不支持），先顺序显示诊断信息，

        // 再在其下方渲染页面内容，避免两者 Y 坐标重叠导致文字叠加发糊。

        if (!WebView2BrowserSession.isAvailable()) {

            String diag = WebView2BrowserSession.diagnostics();

            if (diag != null && !diag.isBlank()) {

                List<FormattedCharSequence> diagLines = this.font.split(

                        Component.literal(diag), textWidth);

                for (FormattedCharSequence line : diagLines) {

                    graphics.drawString(this.font, line, x, y, 0xD0D0D0, false);

                    y += 9;

                    if (y > 76) break;

                }

                y += 4; // 诊断和正文之间留一点间距

            }

        }



        String title = SimpleBrowserSession.pageTitle();

        if (!title.isEmpty()) {

            graphics.drawString(this.font, title, x, y, 0xFFD680, false);

            y += 12;

        }



        List<FormattedCharSequence> lines = this.font.split(Component.literal(SimpleBrowserSession.pageText()), textWidth);

        int maxLines = Math.max(1, (this.height - y - 36) / 9);

        int scroll = Math.min(SimpleBrowserSession.scroll(), Math.max(0, lines.size() - maxLines));

        for (int index = 0; index < maxLines && scroll + index < lines.size(); index++) {

            graphics.drawString(this.font, lines.get(scroll + index), x, y + index * 9, SimpleBrowserSession.bodyTextColor());

        }



        int linksY = this.height - TOOLBAR_HEIGHT;

        graphics.drawString(this.font, Component.translatable("tensura_tno.wiki.links"), x, linksY, 0x90CFFF, false);

        int currentY = linksY + 10;

        for (SimpleBrowserSession.LinkEntry entry : SimpleBrowserSession.links()) {

            if (currentY > this.height - 10) {

                break;

            }

            graphics.drawString(this.font, entry.text(), x, currentY, SimpleBrowserSession.linkTextColor(), false);

            currentY += 10;

        }

    }



    private void updateNavButtons() {

        if (this.backButton != null) {

            this.backButton.active = this.useNativeBrowser() || SimpleBrowserSession.canBack();

        }

        if (this.forwardButton != null) {

            this.forwardButton.active = this.useNativeBrowser() || SimpleBrowserSession.canForward();

        }

        if (this.reloadButton != null) {

            this.reloadButton.active = true;

        }

    }



    private boolean inContentArea(double mouseX, double mouseY) {

        return mouseX >= 0.0D && mouseX <= this.width && mouseY >= TOOLBAR_HEIGHT && mouseY <= this.height;

    }



    private boolean overCloseButton(double mouseX, double mouseY) {

        int left = this.closeButtonLeft();

        return mouseX >= left && mouseX < left + CLOSE_BUTTON_WIDTH

            && mouseY >= CLOSE_BUTTON_TOP && mouseY < CLOSE_BUTTON_TOP + CLOSE_BUTTON_HEIGHT;

    }



    private int closeButtonLeft() {

        return this.width - CLOSE_BUTTON_RIGHT_MARGIN - CLOSE_BUTTON_WIDTH;

    }



    private int contentHeight() {

        return this.contentHeight(this.height);

    }



    private int contentHeight(int screenHeight) {

        return Math.max(1, screenHeight - TOOLBAR_HEIGHT);

    }



    private void syncBrowserBackend() {

        boolean useNativeBrowser = this.useNativeBrowser();

        if (useNativeBrowser == this.usedNativeBrowserLastFrame) {

            if (!useNativeBrowser) {

                this.ensureFallbackSession(this.preferredUrl(), false);

            }

            return;

        }



        String preferredUrl = this.preferredUrl();

        if (useNativeBrowser) {

            WebView2BrowserSession.prepareForScreenOpen();

            this.startupUrl = preferredUrl;

            this.nativeBrowserInitialLoadPending = true;

            this.nativeBrowserCreationArmed = false;

        } else {

            WebView2BrowserSession.closeBrowserKeepState();

            this.ensureFallbackSession(preferredUrl, true);

        }



        this.usedNativeBrowserLastFrame = useNativeBrowser;

        this.updateNavButtons();

    }



    private void ensureNativeBrowserReady() {

        String targetUrl = this.nativeBrowserInitialLoadPending ? this.startupUrl : this.preferredUrl();

        WebView2BrowserSession.ensureBrowser(targetUrl, 0, TOOLBAR_HEIGHT, this.width, this.contentHeight());

        if (this.nativeBrowserInitialLoadPending) {

            WebView2BrowserSession.loadUrl(targetUrl);

            this.nativeBrowserInitialLoadPending = false;

        }

    }



    private void closeNativeBrowser() {

        if (this.closing) {

            return;

        }

        this.closing = true;

        this.nativeBrowserCreationArmed = false;

        this.nativeBrowserInitialLoadPending = false;

        WebView2BrowserSession.closeBrowserKeepState();

    }



    private void ensureFallbackSession(String preferredUrl, boolean force) {

        String targetUrl = preferredUrl == null || preferredUrl.isBlank() ? BrowserClientConfig.defaultWikiUrl() : preferredUrl;

        String currentUrl = SimpleBrowserSession.currentUrl();

        boolean missingContent = !SimpleBrowserSession.loading() && SimpleBrowserSession.pageText().isEmpty();

        if (force || currentUrl.isBlank() || missingContent) {

            SimpleBrowserSession.navigate(targetUrl, true);

            return;

        }



        if (!currentUrl.equals(targetUrl) && BrowserClientConfig.isManagedWikiUrl(currentUrl)) {

            SimpleBrowserSession.navigate(targetUrl, true);

        }

    }



    private Component statusMessage() {

        if (this.useNativeBrowser()) {

            return Component.translatable("tensura_tno.wiki.status.webview2");

        }



        if (this.isFullscreenMode()) {

            // 全屏时 WebView2 会抢焦点/不显示，强制使用文字模式并提示

            return Component.translatable("tensura_tno.wiki.status.fullscreen_text_mode");

        }



        return SimpleBrowserSession.loading()

            ? Component.translatable("tensura_tno.wiki.status.loading")

            : Component.translatable("tensura_tno.wiki.status.fallback", SimpleBrowserSession.status());

    }



    private String preferredUrl() {

        String fallbackUrl = SimpleBrowserSession.currentUrl();

        if (fallbackUrl != null && !fallbackUrl.isBlank()) {

            return fallbackUrl;

        }



        String nativeUrl = WebView2BrowserSession.getLastOrCurrentUrl();

        if (nativeUrl != null && !nativeUrl.isBlank()) {

            return nativeUrl;

        }



        String defaultUrl = BrowserClientConfig.defaultWikiUrl();

        return defaultUrl == null || defaultUrl.isBlank() ? BrowserClientConfig.homepage() : defaultUrl;

    }



    /**

     * WebView2 可用 且 非全屏模式 时才使用原生浏览器。

     *

     * 全屏模式下 WebView2 作为 Win32 子窗口嵌入但不渲染，

     * 点击区域会抢走 GLFW 窗口焦点导致游戏失焦退到桌面。

     * 因此全屏时强制使用 SimpleBrowserSession（纯 OpenGL 渲染，无子窗口）。

     */

    private boolean useNativeBrowser() {

        return WebView2BrowserSession.isAvailable() && !this.isFullscreenMode();

    }



    private boolean isFullscreenMode() {

        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();

        return BrowserDisplayMode.isFullscreenLike(mc);

    }

}


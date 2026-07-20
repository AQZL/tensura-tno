package com.tensura_tno.mixin.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensura_tno.network.RimuruModePackets;
import com.tensura_tno.race.SlimeRaceHelper;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.tensura.client.screen.ReincarnationScreen;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给转生 GUI 加一个「利姆露模式」勾选框：当玩家选中史莱姆家族时显示，勾上后选择
 * 该 slime 种族 → 服务端自动学习 RIMURU_SKILLS 列表（参见
 * {@link com.tensura_tno.event.RimuruModeReincarnationHandler}）。
 *
 * <p>仅当选中 4 个 slime 家族（slime / metal_slime / demon_slime / god_slime）
 * 时显示，其它种族不显示。
 *
 * <p>位置：相对 GUI 左上角 (162, 150)，尺寸 10×10 的方块。带边框；勾上时画一个
 * "✓"形状。点击切换状态并发包给服务端。
 *
 * <p>客户端状态保存在 {@link #tensuraTno$rimuruChecked}（per-screen 实例字段，
 * 通过 {@code @Unique} 注入）。每次重新打开 GUI 重置为 false —— 服务端 NBT
 * 标记也会因 packet 同步清掉。
 */
@Mixin(value = ReincarnationScreen.class, priority = 900)
public abstract class ReincarnationScreenRimuruCheckboxMixin {

    /** 勾选框相对 GUI 左上角的位置和尺寸。 */
    @Unique private static final int TENSURA_TNO$BOX_X = 162;
    @Unique private static final int TENSURA_TNO$BOX_Y = 150;
    @Unique private static final int TENSURA_TNO$BOX_SIZE = 10;

    /** 勾选状态（仅客户端，每次开 GUI 默认 false）。 */
    @Unique private boolean tensuraTno$rimuruChecked = false;

    /** 本次菜单是否由人物重置卷打开（init() 时从 RimuruModePackets.clientNextMenuFromCharacterReset 消费）。 */
    @Unique private boolean tensuraTno$fromCharacterReset = false;

    @Inject(
        method = "init",
        at = @At("TAIL"),
        require = 0,
        remap = false
    )
    private void tensuraTno$captureCharacterResetFlag(CallbackInfo ci) {
        // 从客户端临时 flag 拷过来 + 清掉，避免下次 GUI 误用
        tensuraTno$fromCharacterReset =
                com.tensura_tno.network.RimuruModePackets.clientNextMenuFromCharacterReset;
        com.tensura_tno.network.RimuruModePackets.clientNextMenuFromCharacterReset = false;
    }

    @Inject(
        method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
        at = @At("TAIL"),
        require = 0
    )
    private void tensuraTno$renderRimuruCheckbox(GuiGraphics graphics, float partialTick,
                                                   int mouseX, int mouseY, CallbackInfo ci) {
        if (!tensuraTno$shouldShowCheckbox()) return;

        int x = tensuraTno$boxScreenX();
        int y = tensuraTno$boxScreenY();

        // 边框（黑色），背景（深灰色）
        graphics.fill(x, y, x + TENSURA_TNO$BOX_SIZE, y + TENSURA_TNO$BOX_SIZE, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + TENSURA_TNO$BOX_SIZE - 1, y + TENSURA_TNO$BOX_SIZE - 1,
                0xFF555555);

        // 鼠标悬停高亮（淡青色边框）
        if (tensuraTno$mouseOverBox(mouseX, mouseY)) {
            graphics.renderOutline(x, y, TENSURA_TNO$BOX_SIZE, TENSURA_TNO$BOX_SIZE, 0xFF80E0FF);
        }

        // 勾上：画一个白色 "✓"，由两条短线段组成
        if (tensuraTno$rimuruChecked) {
            // 用 fill 画两条 1px 粗的线模拟勾号：左下→中下，中下→右上
            // 左下到中下（短）
            graphics.fill(x + 2, y + 5, x + 4, y + 7, 0xFFFFFFFF);
            // 中下到右上（长）
            graphics.fill(x + 4, y + 6, x + 8, y + 8, 0xFFFFFFFF);
            graphics.fill(x + 5, y + 4, x + 8, y + 6, 0xFFFFFFFF);
            graphics.fill(x + 6, y + 2, x + 8, y + 4, 0xFFFFFFFF);
        }

        // 鼠标悬停时画 tooltip
        if (tensuraTno$mouseOverBox(mouseX, mouseY)) {
            graphics.renderTooltip(
                    net.minecraft.client.Minecraft.getInstance().font,
                    Component.translatable("tensura_tno.reincarnation.rimuru_mode"),
                    mouseX, mouseY);
        }
    }

    @Inject(
        method = "mouseClicked",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void tensuraTno$onCheckboxClicked(double mouseX, double mouseY, int button,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        if (!tensuraTno$shouldShowCheckbox()) return;
        if (!tensuraTno$mouseOverBox((int) mouseX, (int) mouseY)) return;

        // toggle 状态
        tensuraTno$rimuruChecked = !tensuraTno$rimuruChecked;

        // 通知服务端
        try {
            NetworkManager.sendToServer(new RimuruModePackets.SetRimuruPendingPayload(
                    tensuraTno$rimuruChecked));
        } catch (Throwable t) {
            com.tensura_tno.TensuraTNOMod.LOGGER.warn(
                    "[TensuraTNO] Failed to send Rimuru-mode pending packet: {}", t.toString());
        }

        cir.setReturnValue(true);  // 阻止事件继续传播
    }

    /** 是否应该显示勾选框：服务器配置允许（单人/集成服无视 config）+ 选中 slime 家族 +
     *  changeRaceOnly=false（即首次登录或人物重置卷打开的菜单 — 不是种族重置卷/转生术）+
     *  世界规则 rimuruMode 关闭。 */
    @Unique
    private boolean tensuraTno$shouldShowCheckbox() {
        try {
            // 1) 排除：仅多人(连到专属服)时受 config 门控；单人/集成服(含 LAN host)绕过。
            //    判据：本端有正在跑的集成服务器 → 视为单人/集成服。
            net.minecraft.client.Minecraft mc0 = net.minecraft.client.Minecraft.getInstance();
            boolean integratedHost = mc0 != null && mc0.hasSingleplayerServer();
            if (!integratedHost
                    && !com.tensura_tno.network.RimuruModePackets.isClientCheckboxEnabled()) {
                return false;
            }

            ReincarnationScreen self = (ReincarnationScreen) (Object) this;
            ReincarnationMenu menu = self.getMenu();
            if (menu == null) return false;

            // 2) 排除：种族重置卷 / 转生术（changeRaceOnly = true）
            if (menu.isChangeRaceOnly()) return false;

            // 3) 排除：本次 menu 是人物重置卷打开的（init() 时从 client flag 捕获）
            if (tensuraTno$fromCharacterReset) return false;

            // 4) 排除：rimuruMode gamerule 已开
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return false;
            try {
                if (mc.level.getGameRules().getBoolean(
                        io.github.manasmods.tensura.world.TensuraGameRules.RIMURU_MODE)) {
                    return false;
                }
            } catch (Throwable ignored) {}

            // 5) 选中的种族是 slime 家族
            int index = menu.selectedManasRaceIndex.get();
            List<ManasRace> pool = menu.getRacePool();
            if (pool == null || index < 0 || index >= pool.size()) return false;

            ManasRace race = pool.get(index);
            if (race == null) return false;
            ResourceLocation id = race.getRegistryName();
            return id != null && SlimeRaceHelper.SLIME_RACE_IDS.contains(id);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 勾选框在屏幕坐标系的 X（leftPos + box_x）。 */
    @Unique
    private int tensuraTno$boxScreenX() {
        ReincarnationScreen self = (ReincarnationScreen) (Object) this;
        return self.getGuiLeft() + TENSURA_TNO$BOX_X;
    }

    @Unique
    private int tensuraTno$boxScreenY() {
        ReincarnationScreen self = (ReincarnationScreen) (Object) this;
        return self.getGuiTop() + TENSURA_TNO$BOX_Y;
    }

    @Unique
    private boolean tensuraTno$mouseOverBox(int mouseX, int mouseY) {
        int x = tensuraTno$boxScreenX();
        int y = tensuraTno$boxScreenY();
        return mouseX >= x && mouseX < x + TENSURA_TNO$BOX_SIZE
                && mouseY >= y && mouseY < y + TENSURA_TNO$BOX_SIZE;
    }
}

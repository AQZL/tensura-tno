package com.tensura_tno.client.screen;

import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.entity.spirit.FoxSpiritEntity;
import com.tensura_tno.network.ContractLittleFoxPackets;
import com.tensura_tno.registry.TensuraTNOEntities;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.client.RenderHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 契约小狐召唤面板。
 *
 * <p>使用自定义 contract_little_fox.png（与 skill_creation.png 等尺寸 233×140）背景：
 * <ul>
 *   <li>右侧下方：单个切换按钮（唤出 / 收回），点击后 3 秒冷却（按钮变暗）；</li>
 *   <li>右侧：实时状态面板——"HP / EP / MP" 三条进度条 + 数字。</li>
 * </ul>
 *
 * <p>实时数据来源：
 * <ul>
 *   <li>若小狐灵在世界里（{@code state==ALIVE}）：每帧从本地 ClientLevel 取得对应实体并读取属性；</li>
 *   <li>若被收回（{@code state==RECALLED}）：用打开面板时收到的快照值；</li>
 *   <li>若未召唤（{@code state==NONE}）：用"玩家当前 50%"预览值（这一项也每帧从本地玩家
 *       重新计算，以保证预览随玩家自身 EP/MP 实时变化）。</li>
 * </ul>
 */
public class ContractLittleFoxScreen extends Screen {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "textures/gui/contract_little_fox.png");
    private static final ResourceLocation ABILITY_BAR =
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/gui/ability_button.png");

    private static final int IMG_W = 233;
    private static final int IMG_H = 140;

    /** 切换按钮位置与尺寸（与 SkillCreationScreen 的确认按钮一致） */
    private static final int BUTTON_SIZE = 20;
    /** 每次点击后的客户端冷却（3 秒 = 60 tick） */
    private static final int CLICK_COOLDOWN_TICKS = 60;

    /** 状态：参考 OpenPanelPayload。 */
    private static final int STATE_NONE = 0;
    private static final int STATE_ALIVE = 1;
    private static final int STATE_RECALLED = 2;

    private ContractLittleFoxPackets.OpenPanelPayload initial;

    private int leftPos;
    private int topPos;

    /** 打开面板时客户端关卡游戏刻，用于本地倒计时冷却秒数。 */
    private long cooldownBaselineGameTime;

    /** 左侧列表条目（当前固定只有一条"狐灵"）。 */
    private record ListEntry(String id, Component name) {}
    private final java.util.List<ListEntry> entries = java.util.List.of(
            new ListEntry("tensura_tno:fox_spirit",
                    Component.translatable("entity.tensura_tno.fox_spirit"))
    );
    /** 当前选中的索引，默认 0（狐灵）。 */
    private int selectedIndex = 0;

    /** GUI 内用于"模型预览"的狐灵实体——只创建一次并复用，避免每帧分配。 */
    private FoxSpiritEntity previewFox;

    /** 面板打开后客户端是否曾成功找到过活体狐灵实体（用于区分"从未可见"与"消失"）。 */
    private boolean foxPreviouslyFound = false;
    /** 客户端检测到狐灵实体消失（疑似死亡）的游戏刻；-1 表示尚未检测到。 */
    private long foxDeathDetectedGameTime = -1L;
    /** 客户端本地推算的死亡冷却秒数（与服务端 SUMMON_COOLDOWN_AFTER_DEATH_TICKS 保持一致）。 */
    private static final int CLIENT_DEATH_COOLDOWN_SECONDS = 60;

    /** 切换按钮最后一次被点击的游戏刻；-1 表示未在冷却中。静态以保证关屏重开不重置。 */
    private static long buttonClickGameTime = -1L;

    /** 收到 payload 更新后信任 payload 数值的截止游戏刻（避免实体属性未同步时闪烁默认值）。 */
    private long payloadTrustUntilGameTime = -1L;
    private static final int PAYLOAD_TRUST_TICKS = 10;

    public ContractLittleFoxScreen(ContractLittleFoxPackets.OpenPanelPayload initial) {
        super(Component.translatable("tensura_tno.contract_little_fox.title"));
        this.initial = initial;
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        this.payloadTrustUntilGameTime = now + PAYLOAD_TRUST_TICKS;
    }

    /**
     * 服务端推送新状态时原地刷新，避免关屏 / 重建导致的闪烁与鼠标居中。
     */
    public void updatePayload(ContractLittleFoxPackets.OpenPanelPayload newPayload) {
        this.initial = newPayload;
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        this.cooldownBaselineGameTime = now;
        this.foxPreviouslyFound = false;
        this.foxDeathDetectedGameTime = -1L;
        this.payloadTrustUntilGameTime = now + PAYLOAD_TRUST_TICKS;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - IMG_W) / 2;
        this.topPos = (this.height - IMG_H) / 2;
        Minecraft mc = Minecraft.getInstance();
        this.cooldownBaselineGameTime = mc.level != null ? mc.level.getGameTime() : 0L;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        gfx.blit(BACKGROUND, x, y, 0, 0, IMG_W, IMG_H);
        RenderHelper.drawCenteredText(gfx, this.font, this.title,
                this.leftPos + 56, this.topPos + 5, 0xFFFFFF, false);

        renderListRows(gfx, mouseX, mouseY);
        renderToggleButton(gfx, mouseX, mouseY);
        renderFoxPreview(gfx);
        renderStatPanel(gfx);
    }

    // ─── 左侧列表（与 SpiritSummonScreen 相同布局） ───

    private void renderListRows(GuiGraphics gfx, int mX, int mY) {
        for (int i = 0; i < entries.size(); i++) {
            int rx = this.leftPos + 6;
            int ry = this.topPos + 43 + i * 13;
            boolean hovered = mX >= rx && mY >= ry && mX < rx + 89 && mY < ry + 13;
            boolean selected = i == this.selectedIndex;
            int vOff = (hovered || selected) ? 13 : 0;
            gfx.blit(ABILITY_BAR, rx, ry, 0.0F, (float) vOff, 89, 13, 89, 26);

            ListEntry entry = entries.get(i);
            MutableComponent name = (MutableComponent) entry.name;
            int color = selected ? 0xFFFF00 : 0xFFFFFF;
            RenderHelper.drawShortenedTextWithTooltip(gfx, this.font, name, name,
                    rx, ry, 3, 3, 88, 13, mX, mY, color, true, this);
        }
    }

    /**
     * 在右侧顶部的小方框里渲染一只狐灵预览。位置坐标与 EdoTenseiScreen
     * 渲染目标实体的区域一致，便于和原 GUI 风格保持一致。
     */
    private void renderFoxPreview(GuiGraphics gfx) {
        LivingEntity entity = getOrCreatePreviewFox();
        if (entity == null) return;

        // 稍微向左旋转狐灵，露出更多身体
        entity.setYBodyRot(150.0F);
        entity.setYRot(150.0F);
        entity.setXRot(0.0F);

        float scale = 20.0F / Math.max(entity.getBbWidth(), entity.getBbHeight());
        scale = Math.min(scale, 18.0F);
        RenderHelper.renderEntityInInventoryFollowsMouse(gfx,
                (float) (leftPos + 146), (float) (topPos + 4),
                (float) (leftPos + 196), (float) (topPos + 42),
                scale,
                (float) (leftPos + 168), (float) (topPos + 23),
                false, entity);
    }

    private LivingEntity getOrCreatePreviewFox() {
        if (this.previewFox != null) return this.previewFox;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        try {
            this.previewFox = TensuraTNOEntities.FOX_SPIRIT.get().create(mc.level);
        } catch (Exception ignored) {
            this.previewFox = null;
        }
        return this.previewFox;
    }

    // ─── 切换按钮（右下角，与 SkillCreationScreen 确认按钮位置一致） ───

    private int getButtonX() { return this.leftPos + 162; }
    private int getButtonY() { return this.topPos + 116; }

    private void renderToggleButton(GuiGraphics gfx, int mX, int mY) {
        int bx = getButtonX();
        int by = getButtonY();
        boolean hovered = mX >= bx && mX < bx + BUTTON_SIZE
                       && mY >= by && mY < by + BUTTON_SIZE;

        boolean onCooldown = isButtonOnCooldown();

        // 从背景纹理底部取按钮图形（与 SkillCreationScreen 一致）
        if (hovered && !onCooldown) {
            gfx.blit(BACKGROUND, bx, by, 0, IMG_H, BUTTON_SIZE, BUTTON_SIZE);
        }

        // 冷却变暗覆盖
        if (onCooldown) {
            gfx.fill(bx, by, bx + BUTTON_SIZE, by + BUTTON_SIZE, 0x80000000);
        }

        // Tooltip
        if (hovered) {
            Component tooltip = isFoxAlive()
                    ? Component.translatable("tensura_tno.contract_little_fox.recall")
                    : Component.translatable("tensura_tno.contract_little_fox.summon");
            this.setTooltipForNextRenderPass(tooltip);
        }
    }

    /** 按钮是否在 3 秒客户端冷却中 */
    private boolean isButtonOnCooldown() {
        if (this.buttonClickGameTime < 0L) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        long elapsed = mc.level.getGameTime() - this.buttonClickGameTime;
        return elapsed < CLICK_COOLDOWN_TICKS;
    }



    /** 当前狐灵是否活在世界中 */
    private boolean isFoxAlive() {
        return getCurrentState() == STATE_ALIVE;
    }

    private boolean canSummon() {
        return getCurrentState() != STATE_ALIVE && getLiveCooldownSeconds() <= 0;
    }

    private boolean canRecall() {
        // 只有当狐灵活在世界里时可以收回
        return getCurrentState() == STATE_ALIVE && findLocalFox() != null;
    }

    private int getCurrentState() {
        // 客户端无法可靠地"重新读取"服务端的状态——它依赖于打开面板时的快照。
        // 但若我们看到本地世界里 fox 已不存在（比如刚被另一个客户端事件移除），
        // 也能识别为非 ALIVE。
        if (this.initial.state() == STATE_ALIVE) {
            FoxSpiritEntity fox = findLocalFox();
            if (fox != null) {
                this.foxPreviouslyFound = true;
                return STATE_ALIVE;
            }
            // 狐灵实体在客户端世界中找不到了
            if (this.foxPreviouslyFound && this.foxDeathDetectedGameTime < 0L) {
                // 之前可见、现在消失——大概率死亡，记录时刻并开始本地冷却倒计时
                Minecraft mc = Minecraft.getInstance();
                this.foxDeathDetectedGameTime = mc.level != null
                        ? mc.level.getGameTime() : this.cooldownBaselineGameTime;
            }
            return STATE_NONE;
        }
        return this.initial.state();
    }

    // ─── 状态面板（右侧 HP / EP / MP） ───

    private void renderStatPanel(GuiGraphics gfx) {
        // 右半边布局：顶部 +4~+42 是模型框；状态条从 +48 开始，各栏间距 14px。
        int px = this.leftPos + 125;
        int py = this.topPos + 48;

        // 标题：死亡召唤冷却优先显示为「冷却：秒数」（与原状态行同一位置）
        Component title;
        int cd = getLiveCooldownSeconds();
        if (cd > 0) {
            title = Component.translatable("tensura_tno.contract_little_fox.status.cooldown", cd)
                    .withStyle(ChatFormatting.RED);
        } else {
            title = switch (getCurrentState()) {
                case STATE_ALIVE -> Component.translatable("tensura_tno.contract_little_fox.status.alive")
                        .withStyle(ChatFormatting.GREEN);
                case STATE_RECALLED -> Component.translatable("tensura_tno.contract_little_fox.status.recalled")
                        .withStyle(ChatFormatting.AQUA);
                default -> Component.translatable("tensura_tno.contract_little_fox.status.none")
                        .withStyle(ChatFormatting.GRAY);
            };
        }
        gfx.drawString(this.font, title, px, py, 0xFFFFFF, false);

        // 拉取实时（若 ALIVE）或快照（其他状态）数值
        Stats s = readDisplayStats();

        drawStatBar(gfx, px, py + 12, 90, "HP",  s.hp,    s.maxHp,    0xFFFF5050);
        drawStatBar(gfx, px, py + 26, 90, "EP",  s.ep(),  s.maxEp(),  0xFFFFE066);
        drawStatBar(gfx, px, py + 40, 90, "MP",  s.mp,    s.maxMp,    0xFF6699FF);
        // SHP（精神 HP）—— 只在 ALIVE 时实时显示，其他状态不展示
        if (getCurrentState() == STATE_ALIVE) {
            FoxSpiritEntity fox = findLocalFox();
            if (fox != null) {
                IExistence ex = TensuraStorages.getExistenceFrom(fox);
                drawStatBar(gfx, px, py + 54, 90, "SHP",
                        (float) ex.getSpiritualHealth(),
                        (float) fox.getAttributeValue(io.github.manasmods.tensura.registry.attribute.TensuraAttributes.MAX_SPIRITUAL_HEALTH),
                        0xFFA875FF);
            }
        }
    }

    /** 实时数值——根据 state 选择数据源。 */
    private Stats readDisplayStats() {
        if (getCurrentState() == STATE_ALIVE) {
            // 若仍在 payload 信任窗口内，直接用 payload 值（实体属性可能还没同步到客户端）
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getGameTime() < this.payloadTrustUntilGameTime) {
                return new Stats(
                        initial.foxHp(), initial.foxMaxHp(),
                        initial.foxAura(), initial.foxMaxAura(),
                        initial.foxMagic(), initial.foxMaxMagic()
                );
            }
            FoxSpiritEntity fox = findLocalFox();
            if (fox != null) {
                IExistence ex = TensuraStorages.getExistenceFrom(fox);
                return new Stats(
                        fox.getHealth(), fox.getMaxHealth(),
                        (float) ex.getAura(), (float) EnergyHelper.getBaseMaxAura(fox),
                        (float) ex.getMagicule(), (float) EnergyHelper.getBaseMaxMagicule(fox)
                );
            }
            // 实体没找到——退回快照
        }
        if (getCurrentState() == STATE_RECALLED) {
            // 客户端实时计算收回期间的被动恢复（HP / EP / MP，每秒 2%）
            float hp = initial.foxHp();
            float maxHp = initial.foxMaxHp();
            float aura = initial.foxAura();
            float maxAura = initial.foxMaxAura();
            float magic = initial.foxMagic();
            float maxMagic = initial.foxMaxMagic();
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                long elapsed = mc.level.getGameTime() - this.cooldownBaselineGameTime;
                float secondsSinceOpen = elapsed / 20.0F;
                float regenRate = 0.02F * secondsSinceOpen;
                if (hp < maxHp) hp = Math.min(maxHp, hp + maxHp * regenRate);
                if (aura < maxAura) aura = Math.min(maxAura, aura + maxAura * regenRate);
                if (magic < maxMagic) magic = Math.min(maxMagic, magic + maxMagic * regenRate);
            }
            return new Stats(hp, maxHp, aura, maxAura, magic, maxMagic);
        }
        // STATE_NONE —— 用本地玩家的 50% 预览
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return new Stats(
                    initial.foxHp(), initial.foxMaxHp(),
                    initial.foxAura(), initial.foxMaxAura(),
                    initial.foxMagic(), initial.foxMaxMagic()
            );
        }
        IExistence pe = TensuraStorages.getExistenceFrom(p);
        return new Stats(
                p.getHealth() * 0.5F, p.getMaxHealth() * 0.5F,
                (float) pe.getAura() * 0.5F, (float) EnergyHelper.getBaseMaxAura(p) * 0.5F,
                (float) pe.getMagicule() * 0.5F, (float) EnergyHelper.getBaseMaxMagicule(p) * 0.5F
        );
    }

    private void drawStatBar(GuiGraphics gfx, int x, int y, int width,
                             String label, float value, float max, int barColorARGB) {
        gfx.drawString(this.font, label + ":", x, y, 0xFFFFFFFF, false);
        int barX = x + 22;
        int barY = y;
        int barW = width - 22;

        // 背景条
        gfx.fill(barX, barY, barX + barW, barY + 7, 0xFF333333);

        if (max > 0) {
            int filled = (int) Math.round(barW * Math.min(1.0F, value / max));
            if (filled > 0) {
                gfx.fill(barX, barY, barX + filled, barY + 7, barColorARGB);
            }
        }

        // 文字（数值）
        String text = String.format("%s / %s", fmt(value), fmt(max));
        gfx.drawString(this.font, text, barX + 1, barY - 1, 0xFFFFFFFF, true);

        RenderSystem.disableBlend();
    }

    /**
     * 数值缩写格式化，与 EPRequirementFormatMixin 一致：
     * >=1G → xG, >=1M → xM, >=1k → xk，否则保留一位小数。
     */
    private static String fmt(float v) {
        if (v >= 1_000_000_000.0F) return stripTrailingZero(v / 1_000_000_000.0F) + "G";
        if (v >= 1_000_000.0F)      return stripTrailingZero(v / 1_000_000.0F) + "M";
        if (v >= 1_000.0F)          return stripTrailingZero(v / 1_000.0F) + "k";
        long lv = (long) v;
        return lv == v ? String.valueOf(lv) : String.format("%.1f", v);
    }

    private static String stripTrailingZero(double v) {
        long lv = (long) v;
        return lv == v ? String.valueOf(lv) : String.format("%.1f", v);
    }

    // ─── 输入 ───

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        int bx = getButtonX();
        int by = getButtonY();

        if (button == 0
                && x >= bx && y >= by
                && x < bx + BUTTON_SIZE && y < by + BUTTON_SIZE
                && !isButtonOnCooldown()) {
            if (isFoxAlive() && canRecall()) {
                sendAction(ContractLittleFoxPackets.ActionPayload.RECALL);
                startButtonCooldown();
                return true;
            } else if (!isFoxAlive() && canSummon()) {
                sendAction(ContractLittleFoxPackets.ActionPayload.SUMMON);
                startButtonCooldown();
                return true;
            }
        }

        // 左侧列表点击
        for (int i = 0; i < entries.size(); i++) {
            int rx = this.leftPos + 6;
            int ry = this.topPos + 43 + i * 13;
            if (x >= rx && y >= ry && x < rx + 89 && y < ry + 13) {
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.selectedIndex = i;
                return true;
            }
        }

        return super.mouseClicked(x, y, button);
    }

    private void startButtonCooldown() {
        Minecraft mc = Minecraft.getInstance();
        this.buttonClickGameTime = mc.level != null ? mc.level.getGameTime() : 0L;
    }

    private void sendAction(int action) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        NetworkManager.sendToServer(new ContractLittleFoxPackets.ActionPayload(action));
        // 不关闭——服务端会立刻推送新的 OpenPanelPayload，由 updatePayload() 原地刷新
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ─── 工具：在本地客户端世界中按 UUID 找狐灵 ───

    /** 面板打开后随客户端游戏刻递减的剩余冷却秒（与服务端发包时的值对齐）。 */
    private int getLiveCooldownSeconds() {
        // 若客户端实时检测到狐灵消失（死亡），使用本地推算的冷却倒计时
        if (this.foxDeathDetectedGameTime >= 0L) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return CLIENT_DEATH_COOLDOWN_SECONDS;
            long elapsed = mc.level.getGameTime() - this.foxDeathDetectedGameTime;
            int elapsedSec = (int) (elapsed / 20L);
            return Math.max(0, CLIENT_DEATH_COOLDOWN_SECONDS - elapsedSec);
        }
        int start = this.initial.summonCooldownSeconds();
        if (start <= 0) {
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return start;
        }
        long elapsedTicks = mc.level.getGameTime() - this.cooldownBaselineGameTime;
        int elapsedSec = (int) (elapsedTicks / 20L);
        return Math.max(0, start - elapsedSec);
    }

    private FoxSpiritEntity findLocalFox() {
        UUID id = initial.foxUuid();
        if (id == null || (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0)) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof FoxSpiritEntity fox && fox.getUUID().equals(id)) return fox;
        }
        return null;
    }

    /** 一个简陋的"展示用"组合：HP + Aura + Magicule，全部是 float。 */
    private record Stats(float hp, float maxHp,
                         float aura, float maxAura,
                         float mp, float maxMp) {
        float ep() { return aura + mp; }
        float maxEp() { return maxAura + maxMp; }
    }
}

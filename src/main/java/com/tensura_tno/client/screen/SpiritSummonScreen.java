package com.tensura_tno.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tensura_tno.network.SpiritSummonPackets;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.tensura.client.screen.templates.IScrollBar;
import io.github.manasmods.tensura.util.client.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * 灵之召唤 —— 召唤选择 GUI。
 * 复用 EdoTenseiScreen 的布局风格（skill_creation.png 背景 + 实体列表 + 3D 预览）。
 */
public class SpiritSummonScreen extends Screen implements IScrollBar {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/gui/skill_creation/skill_creation.png");
    private static final ResourceLocation ABILITY_BAR =
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/gui/ability_button.png");

    private static final int IMG_W = 233;
    private static final int IMG_H = 140;

    private record EntityEntry(String id, String shortId, Component name, double bonusEP, float hp, float ep, float mp) {}

    private final List<EntityEntry> allEntries = new ArrayList<>();
    private List<EntityEntry> filtered = new ArrayList<>();

    private float scrollOffset;
    private boolean scrolling;
    private int listStartIndex;

    private int selectedIndex = -1;
    private EditBox searchField;
    private String nameFilter = "";

    private int leftPos;
    private int topPos;

    private LivingEntity cachedEntity;
    private String cachedEntityId;

    /** 放生确认状态：-1=无，>=0=等待确认放生的 filtered 列表索引 */
    private int releaseConfirmIndex = -1;

    public SpiritSummonScreen(String absorbedCsv) {
        super(Component.translatable("tensura_tno.spirit_summon.title"));
        if (absorbedCsv == null || absorbedCsv.isBlank()) return;
        for (String entry : List.of(absorbedCsv.split(","))) {
            if (entry.isBlank()) continue;
            // 格式："id:bonusEP:hp:aura:magicule"
            // entityId 含冒号（如 minecraft:zombie），用 lastIndexOf 策略解析
            String entityId;
            double bonusEP = 0.0;
            float hp = 20, aura = 50, magicule = 50;
            // 从右往左解析，找到5个段
            String[] parts = entry.split(":");
            if (parts.length >= 5) {
                // 重新组合：前两个部分是 entityId 的 namespace:path
                try {
                    magicule = Float.parseFloat(parts[parts.length - 1]);
                    aura = Float.parseFloat(parts[parts.length - 2]);
                    hp = Float.parseFloat(parts[parts.length - 3]);
                    bonusEP = Double.parseDouble(parts[parts.length - 4]);
                    // entityId 是前面剩余的部分，用冒号重新拼接
                    StringBuilder idBuilder = new StringBuilder();
                    for (int i = 0; i < parts.length - 4; i++) {
                        if (i > 0) idBuilder.append(":");
                        idBuilder.append(parts[i]);
                    }
                    entityId = idBuilder.toString();
                } catch (NumberFormatException e) {
                    // 回退：按旧格式解析
                    entityId = entry;
                }
            } else if (parts.length == 3) {
                // 旧格式 "namespace:path:bonusEP"
                try {
                    bonusEP = Double.parseDouble(parts[2]);
                    entityId = parts[0] + ":" + parts[1];
                } catch (NumberFormatException e) {
                    entityId = entry;
                }
            } else {
                entityId = entry;
            }
            String shortId = entityId.substring(entityId.indexOf(':') + 1);
            Component displayName = Component.literal(shortId);
            try {
                ResourceLocation id = ResourceLocation.parse(entityId);
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
                if (type != null) {
                    displayName = type.getDescription();
                }
            } catch (Exception ignored) {}
            float ep = aura + magicule + (float) bonusEP;
            allEntries.add(new EntityEntry(entityId, shortId, displayName, bonusEP, hp, ep, magicule));
        }
        filtered = new ArrayList<>(allEntries);
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - IMG_W) / 2;
        this.topPos = (this.height - IMG_H) / 2;
        this.scrollOffset = 0.0F;
        this.listStartIndex = 0;

        this.searchField = new EditBox(this.font, this.leftPos + 19, this.topPos + 27, 79, 9, Component.empty());
        this.searchField.setBordered(false);
        this.searchField.setResponder(s -> {
            this.nameFilter = s;
            this.setScrollOffset(0.0F);
            this.setListStartIndex(0);
            this.updateFiltered();
        });
        this.addRenderableWidget(this.searchField);
        if (this.nameFilter != null && !this.nameFilter.isEmpty()) {
            this.searchField.setValue(this.nameFilter);
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        gfx.blit(BACKGROUND, x, y, 0, 0, IMG_W, IMG_H);
        RenderHelper.drawCenteredText(gfx, this.font, this.title, this.leftPos + 56, this.topPos + 5, 0xFFFFFF, false);
        this.searchField.render(gfx, mouseX, mouseY, partialTick);

        int lastVisible = Math.min(this.listStartIndex + getScrollBarRenderCount(), filtered.size());
        renderListRows(gfx, mouseX, mouseY, lastVisible);
        this.renderScrollBar(gfx, mouseX, mouseY);
        renderRightPanel(gfx, mouseX, mouseY, partialTick);
        renderConfirmButton(gfx, mouseX, mouseY);
    }

    private void renderListRows(GuiGraphics gfx, int mX, int mY, int lastVisible) {
        for (int i = this.listStartIndex; i < lastVisible && i < filtered.size(); i++) {
            int rx = this.leftPos + 6;
            int ry = this.topPos + 43 + (i - this.listStartIndex) * 13;
            boolean hovered = mX >= rx && mY >= ry && mX < rx + 89 && mY < ry + 13;
            boolean selected = i == this.selectedIndex;
            int vOff = (hovered || selected) ? 13 : 0;
            gfx.blit(ABILITY_BAR, rx, ry, 0.0F, (float) vOff, 89, 13, 89, 26);

            EntityEntry entry = filtered.get(i);
            MutableComponent name = (MutableComponent) entry.name;
            int color = selected ? 0xFFFF00 : 0xFFFFFF;
            RenderHelper.drawShortenedTextWithTooltip(gfx, this.font, name, name,
                    rx, ry, 3, 3, 88, 13, mX, mY, color, true, this);
        }
    }

    private void renderRightPanel(GuiGraphics gfx, int mX, int mY, float partialTick) {
        if (selectedIndex < 0 || selectedIndex >= filtered.size()) return;
        EntityEntry sel = filtered.get(selectedIndex);
        LivingEntity entity = getOrCreateEntity(sel.id);
        if (entity == null) return;

        // 实体 3D 预览
        float scale = 20.0F / Math.max(entity.getBbWidth(), entity.getBbHeight());
        scale = Math.min(scale, 18.0F);
        RenderHelper.renderEntityInInventoryFollowsMouse(gfx,
                (float) (leftPos + 146), (float) (topPos + 4),
                (float) (leftPos + 196), (float) (topPos + 42),
                scale, (float) (leftPos + 171), (float) (topPos + 23), false, entity);

        // 属性展示（使用服务端提供的固定值，不依赖临时实体的属性初始化）
        int px = leftPos + 125;
        int py = topPos + 48;

        // HP（从客户端临时实体获取，Minecraft 的 getMaxHealth 即使客户端也正确）
        float maxHp = entity.getMaxHealth();
        drawStatBar(gfx, px, py, 90, "HP", maxHp, maxHp, 0xFFFF5050);
        py += 14;

        // EP（灵压 = Aura + Magicule + 累计加成）
        float ep = sel.ep();
        float maxEp = ep - (float) sel.bonusEP(); // 不含加成的原始EP
        drawStatBar(gfx, px, py, 90, "EP", ep, maxEp, 0xFFFFE066);
        py += 14;

        // MP（魔素）
        drawStatBar(gfx, px, py, 90, "MP", sel.mp(), sel.mp(), 0xFF6699FF);
    }

    /**
     * 绘制一条属性条（与 ContractLittleFoxScreen.drawStatBar 完全一致）。
     * 格式："标签: [====    ] 当前值 / 最大值"
     */
    private void drawStatBar(GuiGraphics gfx, int x, int y, int width,
                             String label, float value, float max, int barColorARGB) {
        gfx.drawString(this.font, label + ":", x, y, 0xFFFFFFFF, false);
        int barX = x + 22;
        int barW = width - 22;

        // 背景条
        gfx.fill(barX, y, barX + barW, y + 7, 0xFF333333);

        if (max > 0) {
            int filled = (int) Math.round(barW * Math.min(1.0F, value / max));
            if (filled > 0) {
                gfx.fill(barX, y, barX + filled, y + 7, barColorARGB);
            }
        }

        // 文字（仅显示数值）
        String text = fmt(value);
        gfx.drawString(this.font, text, barX + 1, y - 1, 0xFFFFFFFF, true);

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

    private void renderConfirmButton(GuiGraphics gfx, int mX, int mY) {
        if (selectedIndex < 0) return;
        boolean hovered = mX >= leftPos + 162 && mX < leftPos + 182
                && mY >= topPos + 116 && mY < topPos + 136;
        if (hovered) {
            gfx.blit(BACKGROUND, leftPos + 162, topPos + 116, 0, IMG_H, 20, 20);
        }
        // 放生确认提示
        if (releaseConfirmIndex == selectedIndex) {
            Component warn = Component.translatable("tensura_tno.spirit_summon.release_confirm")
                    .withStyle(s -> s.withColor(0xFF5555));
            gfx.drawString(this.font, warn, leftPos + 125, topPos + 104, 0xFFFF5555, true);
        }
        if (hovered) {
            if (Screen.hasShiftDown()) {
                this.setTooltipForNextRenderPass(
                        releaseConfirmIndex == selectedIndex
                                ? Component.translatable("tensura_tno.spirit_summon.release_confirm_tooltip")
                                : Component.translatable("tensura_tno.spirit_summon.release_tooltip"));
            } else {
                this.setTooltipForNextRenderPass(
                        Component.translatable("tensura_tno.spirit_summon.summon"));
            }
        }
    }

    private LivingEntity getOrCreateEntity(String entityId) {
        if (entityId.equals(cachedEntityId) && cachedEntity != null) return cachedEntity;
        try {
            ResourceLocation rl = ResourceLocation.parse(entityId);
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) return null;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
            Entity e = type.create(Minecraft.getInstance().level);
            if (e instanceof LivingEntity le) {
                cachedEntity = le;
                cachedEntityId = entityId;
                return le;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (this.clickedScrollBar(x, y)) return true;
        if (!this.searchField.isHovered()) this.searchField.setFocused(false);

        if (x >= leftPos + 162 && x < leftPos + 182
                && y >= topPos + 116 && y < topPos + 136 && selectedIndex >= 0) {
            if (Screen.hasShiftDown()) {
                handleShiftClickRelease();
            } else {
                releaseConfirmIndex = -1;
                confirmSelection();
            }
            return true;
        } else {
            // 点击其他区域取消放生确认
            releaseConfirmIndex = -1;
        }

        int lastVisible = Math.min(this.listStartIndex + getScrollBarRenderCount(), filtered.size());
        for (int i = this.listStartIndex; i < lastVisible; i++) {
            int rx = this.leftPos + 6;
            int ry = this.topPos + 43 + (i - this.listStartIndex) * 13;
            if (x >= rx && y >= ry && x < rx + 89 && y < ry + 13) {
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.selectedIndex = i;
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double mX, double mY, double deltaX, double deltaY) {
        this.scrolledScrollBar(deltaY);
        return super.mouseScrolled(mX, mY, deltaX, deltaY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.draggedScrollBar(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (this.searchField.isFocused() && this.searchField.isVisible() && keyCode != 256) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateFiltered() {
        if (nameFilter != null && !nameFilter.isEmpty() && !nameFilter.isBlank()) {
            Predicate<EntityEntry> pred = e -> e.name.getString().toLowerCase().contains(nameFilter.toLowerCase());
            filtered = new ArrayList<>(allEntries.stream().filter(pred).toList());
        } else {
            filtered = new ArrayList<>(allEntries);
        }
        selectedIndex = -1;
        this.scrolledScrollBar(0.0);
    }

    private void confirmSelection() {
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            NetworkManager.sendToServer(
                    new SpiritSummonPackets.SelectEntityPayload(filtered.get(selectedIndex).id));
            this.onClose();
        }
    }

    private void handleShiftClickRelease() {
        if (selectedIndex < 0 || selectedIndex >= filtered.size()) return;
        if (releaseConfirmIndex == selectedIndex) {
            // 二次确认 → 执行放生
            String entityId = filtered.get(selectedIndex).id;
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 0.8F));
            NetworkManager.sendToServer(new SpiritSummonPackets.ReleaseEntityPayload(entityId));
            // 从本地列表移除
            allEntries.removeIf(e -> e.id.equals(entityId));
            updateFiltered();
            releaseConfirmIndex = -1;
        } else {
            // 首次 Shift+点击 → 进入确认状态
            releaseConfirmIndex = selectedIndex;
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.6F));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── IScrollBar ──
    @Override public int getScrollBarX() { return this.leftPos + 98; }
    @Override public int getScrollBarY() { return this.topPos + 43; }
    @Override public int getScrollBarTotalSpace() { return 91; }
    @Override public int getScrollBarListSize() { return this.filtered.size(); }
    @Override public int getScrollBarRenderCount() { return 7; }
    @Override public float getScrollOffset() { return this.scrollOffset; }
    @Override public void setScrollOffset(float v) { this.scrollOffset = v; }
    @Override public boolean isScrolling() { return this.scrolling; }
    @Override public void setScrolling(boolean v) { this.scrolling = v; }
    public int getListStartIndex() { return this.listStartIndex; }
    @Override public void setListStartIndex(int v) { this.listStartIndex = v; }
}

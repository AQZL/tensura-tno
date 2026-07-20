package com.tensura_tno.client.screen;

import com.tensura_tno.network.EdoTenseiPackets;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Entity selection screen for 禁忌召唤術, styled exactly after Tensura's SkillCreationScreen.
 * Uses skill_creation.png background, ability_button.png list rows, scroll_bar.png scrollbar,
 * functional search field, and renders the selected entity's 3D model on the right panel.
 */
public class EdoTenseiScreen extends Screen implements IScrollBar {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/gui/skill_creation/skill_creation.png");
    private static final ResourceLocation ABILITY_BAR =
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/gui/ability_button.png");

    private static final int IMG_W = 233;
    private static final int IMG_H = 140;

    private record EntityEntry(String id, String shortId, Component name) {}

    private final List<EntityEntry> allEntries = new ArrayList<>();
    private List<EntityEntry> filtered = new ArrayList<>();

    private float scrollOffset;
    private boolean scrolling;
    private int listStartIndex;

    private int selectedIndex = -1;
    private int textStartIndex;
    private EditBox searchField;
    private String nameFilter = "";

    private int leftPos;
    private int topPos;

    private LivingEntity cachedEntity;
    private String cachedEntityId;

    public EdoTenseiScreen(List<String> unlockedIds, boolean mastered) {
        super(Component.translatable("tensura_tno.edo_tensei.title"));
        for (String entityId : unlockedIds.stream().sorted().toList()) {
            if (!EdoTenseiPackets.ALL_ENTITIES.contains(entityId)) continue;
            String shortId = entityId.substring(entityId.indexOf(':') + 1);
            Component displayName = Component.literal(shortId);
            try {
                ResourceLocation id = ResourceLocation.parse(entityId);
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
                if (type != null) {
                    displayName = type.getDescription();
                }
            } catch (Exception ignored) {}
            allEntries.add(new EntityEntry(entityId, shortId,
                    displayName));
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

    // ── Rendering ──

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        gfx.blit(BACKGROUND, x, y, 0, 0, IMG_W, IMG_H);

        // Match SkillCreationScreen: title X is relative to panel left (leftPos + 56).
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
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            EntityEntry sel = filtered.get(selectedIndex);

            LivingEntity entity = getOrCreateEntity(sel.id);
            if (entity != null) {
                float scale = 20.0F / Math.max(entity.getBbWidth(), entity.getBbHeight());
                scale = Math.min(scale, 18.0F);
                // Shift model a little to the right and keep a fixed facing angle.
                RenderHelper.renderEntityInInventoryFollowsMouse(gfx,
                        (float) (leftPos + 146), (float) (topPos + 4),
                        (float) (leftPos + 196), (float) (topPos + 42),
                        scale, (float) (leftPos + 171), (float) (topPos + 23), false, entity);
            }

            Component desc = Component.translatable("tensura_tno.skill.edo_tensei.description");
            List<FormattedCharSequence> sequences = this.font.split(desc, 94);
            int descStart = Math.max(0, Math.min(this.textStartIndex, Math.max(0, sequences.size() - 7)));
            int descEnd = Math.min(descStart + 7, sequences.size());
            this.textStartIndex = descStart;
            boolean hoveringDesc = RenderHelper.mouseOver(mX, mY,
                    leftPos + 125, leftPos + 219, topPos + 48, topPos + 108);
            RenderHelper.drawScrollableTextInAreaSetHighlight(gfx, this.font, sequences,
                    leftPos + 125, topPos + 48, 94, 60, 0,
                    descStart, descEnd, hoveringDesc, 8750469, 863042);
        }
    }

    private void renderConfirmButton(GuiGraphics gfx, int mX, int mY) {
        if (selectedIndex >= 0 && mX >= leftPos + 162 && mX < leftPos + 182
                && mY >= topPos + 116 && mY < topPos + 136) {
            gfx.blit(BACKGROUND, leftPos + 162, topPos + 116, 0, IMG_H, 20, 20);
            this.setTooltipForNextRenderPass(
                    Component.translatable("tensura_tno.edo_tensei.summon"));
        }
    }

    // ── Entity model cache ──

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

    // ── Input handling ──

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (this.clickedScrollBar(x, y)) return true;

        if (!this.searchField.isHovered()) {
            this.searchField.setFocused(false);
        }

        if (x >= leftPos + 162 && x < leftPos + 182
                && y >= topPos + 116 && y < topPos + 136 && selectedIndex >= 0) {
            confirmSelection();
            return true;
        }

        int lastVisible = Math.min(this.listStartIndex + getScrollBarRenderCount(), filtered.size());
        for (int i = this.listStartIndex; i < lastVisible; i++) {
            int rx = this.leftPos + 6;
            int ry = this.topPos + 43 + (i - this.listStartIndex) * 13;
            if (x >= rx && y >= ry && x < rx + 89 && y < ry + 13) {
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.selectedIndex = i;
                this.textStartIndex = 0;
                return true;
            }
        }

        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double mX, double mY, double deltaX, double deltaY) {
        if (RenderHelper.mouseOver(mX, mY, leftPos + 125, leftPos + 219, topPos + 43, topPos + 109)) {
            this.textStartIndex = Math.max(this.textStartIndex + (int) (-deltaY), 0);
        } else {
            this.scrolledScrollBar(deltaY);
        }
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
                    new EdoTenseiPackets.SelectEntityPayload(filtered.get(selectedIndex).id));
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── IScrollBar implementation (matches SkillCreationScreen exactly) ──

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

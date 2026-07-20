package com.tensura_tno.client.screen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.network.ReincarnationRulesPackets;

import dev.architectury.networking.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

public class ReincarnationRulesScreen extends Screen {
    public static final ResourceLocation RULES_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TensuraTNOMod.MOD_ID, "textures/gui/reincarnation/rules_panel.png");

    private static final ResourceLocation REINCARNATION_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "tensura", "textures/gui/reincarnation/gui.png");

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 166;
    private static final int BACK_X = 7;
    private static final int BACK_Y = 5;
    private static final int BACK_W = 18;
    private static final int BACK_H = 10;
    private static final int CONTENT_RIGHT_X = 168;
    private static final int BOOLEAN_BUTTON_W = 40;
    private static final int BOOLEAN_BUTTON_H = 12;
    private static final int BOOLEAN_BUTTON_X = CONTENT_RIGHT_X - BOOLEAN_BUTTON_W - 8;
    private static final int EDIT_BOX_W = 40;
    private static final int EDIT_BOX_H = 16;
    private static final int EDIT_BOX_X = BOOLEAN_BUTTON_X;
    private static final int RULE_TEXT_X = 16;
    private static final int RULE_TEXT_W = BOOLEAN_BUTTON_X - RULE_TEXT_X - 4;
    private static final int BOOLEAN_BUTTON_U = 38;
    private static final int BOOLEAN_BUTTON_V = 170;
    private static final int BOOLEAN_BUTTON_TEXTURE_W = 63;
    private static final int BOOLEAN_BUTTON_EDGE_W = 4;
    private static final int RULE_X = 16;
    private static final int RULE_W = 152;
    private static final int UNIQUE_SE_COST_Y = 124;
    private static final int RULE_ROW_H = 28;
    private static final float TEXT_SCALE = 0.82F;

    private static boolean suppressNextReincarnationRemoved;

    private final Screen parent;
    private int leftPos;
    private int topPos;
    private Button hardcoreRaceButton;
    private Button keepInventoryButton;
    private Button uniqueSECostButton;
    private EditBox epDeathPenaltyBox;
    private String lastEpBoxValue = "";

    public ReincarnationRulesScreen(Screen parent) {
        super(Component.empty());
        this.parent = parent;
    }

    public static void prepareOpenFromReincarnationScreen() {
        suppressNextReincarnationRemoved = true;
    }

    public static boolean consumeSuppressReincarnationRemoved(Screen screen) {
        if (suppressNextReincarnationRemoved
                && screen instanceof io.github.manasmods.tensura.client.screen.ReincarnationScreen) {
            suppressNextReincarnationRemoved = false;
            return true;
        }
        return false;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - PANEL_W) / 2;
        this.topPos = (this.height - PANEL_H) / 2;
        ReincarnationRulesPackets.requestSync();
        initWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        graphics.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, 0xFF101826);
        graphics.blit(RULES_TEXTURE, leftPos, topPos, 0, 0, PANEL_W, PANEL_H);

        int backU = mouseOverBack(mouseX, mouseY) ? 25 : 2;
        graphics.blit(REINCARNATION_TEXTURE, leftPos + BACK_X, topPos + BACK_Y, backU, 181, BACK_W, BACK_H);

        updateWidgets();
        renderRules(graphics, mouseX, mouseY);
    }

    private void renderRules(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = topPos + 28;
        renderBooleanRule(graphics, mouseX, mouseY, leftPos + 16, y,
                "gamerule.hardcoreRace", "gamerule.hardcoreRace.description", hardcoreRaceButton);

        y += 32;
        renderBooleanRule(graphics, mouseX, mouseY, leftPos + 16, y,
                "gamerule.keepInventory", "gamerule.keepInventory.description", keepInventoryButton);

        y += 32;
        renderIntegerRule(graphics, mouseX, mouseY, leftPos + 16, y,
                "gamerule.epDeathPenalty", "gamerule.epDeathPenalty.description");

        if (ReincarnationRulesPackets.clientUniqueSECostAvailable) {
            y += 32;
            renderBooleanRule(graphics, mouseX, mouseY, leftPos + 16, y,
                    "gamerule.uniqueSECost", "gamerule.uniqueSECost.description", uniqueSECostButton);
            if (mouseOverUniqueSECost(mouseX, mouseY)) {
                this.setTooltipForNextRenderPass(Component.translatable("gamerule.uniqueSECost.tooltip"));
            }
        }
    }

    private void renderBooleanRule(GuiGraphics graphics, int mouseX, int mouseY, int x, int y,
                                   String nameKey, String descriptionKey, Button button) {
        drawScaledString(graphics, Component.translatable(nameKey).withStyle(ChatFormatting.WHITE),
                x, y, Color.WHITE.getRGB());
        drawScaledWrapped(graphics, Component.translatable(descriptionKey), x, y + 10, RULE_TEXT_W, 0xFFDCDCDC);
        button.render(graphics, mouseX, mouseY, 0.0F);
    }

    private void renderIntegerRule(GuiGraphics graphics, int mouseX, int mouseY, int x, int y,
                                   String nameKey, String descriptionKey) {
        drawScaledString(graphics, Component.translatable(nameKey).withStyle(ChatFormatting.WHITE),
                x, y, Color.WHITE.getRGB());
        drawScaledWrapped(graphics, Component.translatable(descriptionKey), x, y + 10, RULE_TEXT_W, 0xFFDCDCDC);
        epDeathPenaltyBox.render(graphics, mouseX, mouseY, 0.0F);
    }

    private void drawScaledString(GuiGraphics graphics, Component component, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        graphics.drawString(this.font, component, Math.round(x / TEXT_SCALE), Math.round(y / TEXT_SCALE),
                color, false);
        graphics.pose().popPose();
    }

    private void drawScaledWrapped(GuiGraphics graphics, Component component, int x, int y, int width, int color) {
        int splitWidth = Math.round(width / TEXT_SCALE);
        List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(component, splitWidth));
        graphics.pose().pushPose();
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawString(this.font, lines.get(i), Math.round(x / TEXT_SCALE),
                    Math.round((y + i * 8) / TEXT_SCALE), color, false);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseOverBack((int) mouseX, (int) mouseY)) {
            this.setFocused(null);
            playClick();
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        if (epDeathPenaltyBox.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(epDeathPenaltyBox);
            return true;
        }
        if (hardcoreRaceButton.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(null);
            return true;
        }
        if (keepInventoryButton.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(null);
            return true;
        }
        if (ReincarnationRulesPackets.clientUniqueSECostAvailable
                && uniqueSECostButton.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(null);
            return true;
        }
        this.setFocused(null);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (epDeathPenaltyBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (epDeathPenaltyBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void initWidgets() {
        Font font = Minecraft.getInstance().font;

        hardcoreRaceButton = rulesButton(
                        Component.literal(Boolean.toString(ReincarnationRulesPackets.clientHardcoreRace)),
                        button -> {
                            ReincarnationRulesPackets.clientHardcoreRace =
                                    !ReincarnationRulesPackets.clientHardcoreRace;
                            sendBooleanRule(ReincarnationRulesPackets.RULE_HARDCORE_RACE,
                                    ReincarnationRulesPackets.clientHardcoreRace);
                            button.setMessage(Component.literal(
                                    Boolean.toString(ReincarnationRulesPackets.clientHardcoreRace)));
                        },
                        leftPos + BOOLEAN_BUTTON_X, topPos + 30);
        this.addWidget(hardcoreRaceButton);

        keepInventoryButton = rulesButton(
                        Component.literal(Boolean.toString(ReincarnationRulesPackets.clientKeepInventory)),
                        button -> {
                            ReincarnationRulesPackets.clientKeepInventory =
                                    !ReincarnationRulesPackets.clientKeepInventory;
                            sendBooleanRule(ReincarnationRulesPackets.RULE_KEEP_INVENTORY,
                                    ReincarnationRulesPackets.clientKeepInventory);
                            button.setMessage(Component.literal(
                                    Boolean.toString(ReincarnationRulesPackets.clientKeepInventory)));
                        },
                        leftPos + BOOLEAN_BUTTON_X, topPos + 62);
        this.addWidget(keepInventoryButton);

        uniqueSECostButton = rulesButton(
                        Component.literal(Boolean.toString(ReincarnationRulesPackets.clientUniqueSECost)),
                        button -> {
                            ReincarnationRulesPackets.clientUniqueSECost =
                                    !ReincarnationRulesPackets.clientUniqueSECost;
                            sendBooleanRule(ReincarnationRulesPackets.RULE_UNIQUE_SE_COST,
                                    ReincarnationRulesPackets.clientUniqueSECost);
                            button.setMessage(Component.literal(
                                    Boolean.toString(ReincarnationRulesPackets.clientUniqueSECost)));
                        },
                        leftPos + BOOLEAN_BUTTON_X, topPos + 126);
        this.addWidget(uniqueSECostButton);

        epDeathPenaltyBox = new EditBox(font, leftPos + EDIT_BOX_X, topPos + 94, EDIT_BOX_W, EDIT_BOX_H,
                Component.translatable("gamerule.epDeathPenalty"));
        epDeathPenaltyBox.setFilter(value -> value.isEmpty() || value.matches("\\d{0,6}"));
        epDeathPenaltyBox.setMaxLength(6);
        epDeathPenaltyBox.setValue(Integer.toString(ReincarnationRulesPackets.clientEpDeathPenalty));
        lastEpBoxValue = epDeathPenaltyBox.getValue();
        epDeathPenaltyBox.setResponder(value -> {
            if (value.isEmpty() || value.equals(lastEpBoxValue)) {
                return;
            }
            try {
                int next = Math.max(0, Integer.parseInt(value));
                lastEpBoxValue = value;
                ReincarnationRulesPackets.clientEpDeathPenalty = next;
                sendIntegerRule(ReincarnationRulesPackets.RULE_EP_DEATH_PENALTY, next);
            } catch (NumberFormatException ignored) {
            }
        });
        this.addWidget(epDeathPenaltyBox);
    }

    private void updateWidgets() {
        hardcoreRaceButton.setMessage(Component.literal(Boolean.toString(ReincarnationRulesPackets.clientHardcoreRace)));
        keepInventoryButton.setMessage(Component.literal(Boolean.toString(ReincarnationRulesPackets.clientKeepInventory)));
        uniqueSECostButton.visible = ReincarnationRulesPackets.clientUniqueSECostAvailable;
        uniqueSECostButton.active = ReincarnationRulesPackets.clientUniqueSECostAvailable;
        uniqueSECostButton.setMessage(Component.literal(Boolean.toString(ReincarnationRulesPackets.clientUniqueSECost)));

        if (!epDeathPenaltyBox.isFocused()) {
            String synced = Integer.toString(ReincarnationRulesPackets.clientEpDeathPenalty);
            if (!synced.equals(epDeathPenaltyBox.getValue())) {
                lastEpBoxValue = synced;
                epDeathPenaltyBox.setValue(synced);
            }
        }
    }

    private boolean mouseOverBack(int mouseX, int mouseY) {
        return mouseX >= leftPos + BACK_X && mouseX < leftPos + BACK_X + BACK_W
                && mouseY >= topPos + BACK_Y && mouseY < topPos + BACK_Y + BACK_H;
    }

    private boolean mouseOverUniqueSECost(int mouseX, int mouseY) {
        return mouseX >= leftPos + RULE_X && mouseX < leftPos + RULE_X + RULE_W
                && mouseY >= topPos + UNIQUE_SE_COST_Y && mouseY < topPos + UNIQUE_SE_COST_Y + RULE_ROW_H;
    }

    private void sendBooleanRule(String ruleId, boolean value) {
        NetworkManager.sendToServer(new ReincarnationRulesPackets.SetRulePayload(ruleId, Boolean.toString(value)));
    }

    private void sendIntegerRule(String ruleId, int value) {
        NetworkManager.sendToServer(new ReincarnationRulesPackets.SetRulePayload(ruleId, Integer.toString(value)));
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private Button rulesButton(Component message, Button.OnPress onPress, int x, int y) {
        return Button.builder(message, onPress)
                .bounds(x, y, BOOLEAN_BUTTON_W, BOOLEAN_BUTTON_H)
                .build(TexturedRuleButton::new);
    }

    private static class TexturedRuleButton extends Button {
        TexturedRuleButton(Button.Builder builder) {
            super(builder);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            blitButtonTexture(graphics, this.getX(), this.getY(), this.width, this.height);
            if (this.isHovered() && Minecraft.getInstance().mouseHandler.isLeftPressed()) {
                graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height,
                        0x66000000);
            }
            Font font = Minecraft.getInstance().font;
            int textColor = this.active ? Color.WHITE.getRGB() : 0xFFA0A0A0;
            int textX = this.getX() + (this.width - font.width(this.getMessage())) / 2;
            int textY = this.getY() + 2;
            graphics.drawString(font, this.getMessage(), textX, textY, textColor, false);
        }

        private void blitButtonTexture(GuiGraphics graphics, int x, int y, int width, int height) {
            int centerWidth = Math.max(0, width - BOOLEAN_BUTTON_EDGE_W * 2);
            graphics.blit(RULES_TEXTURE, x, y, BOOLEAN_BUTTON_U, BOOLEAN_BUTTON_V,
                    BOOLEAN_BUTTON_EDGE_W, height);
            graphics.blit(RULES_TEXTURE, x + BOOLEAN_BUTTON_EDGE_W, y,
                    BOOLEAN_BUTTON_U + BOOLEAN_BUTTON_EDGE_W, BOOLEAN_BUTTON_V, centerWidth, height);
            graphics.blit(RULES_TEXTURE, x + width - BOOLEAN_BUTTON_EDGE_W, y,
                    BOOLEAN_BUTTON_U + BOOLEAN_BUTTON_TEXTURE_W - BOOLEAN_BUTTON_EDGE_W, BOOLEAN_BUTTON_V,
                    BOOLEAN_BUTTON_EDGE_W, height);
        }
    }
}

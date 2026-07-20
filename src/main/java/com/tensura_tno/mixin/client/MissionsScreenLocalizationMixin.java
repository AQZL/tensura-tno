package com.tensura_tno.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Pseudo
@Mixin(targets = "com.trbeyond.gui.tensura.MissionsScreen", remap = false)
public abstract class MissionsScreenLocalizationMixin {
    private static final String KEY_PREFIX = "tensura_tno.beyond_gacha.missions.";
    private static final ResourceLocation DEFAULT_FONT = ResourceLocation.withDefaultNamespace("default");
    private static final Pattern HMS_PATTERN = Pattern.compile("\\s*(\\d+)h\\s+(\\d+)m\\s+(\\d+)s\\s*");

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"
            )
    )
    private int tensura_tno$translateMissionTabs(GuiGraphics guiGraphics, Font font, Component text, int x, int y, int color, boolean shadow) {
        return guiGraphics.drawString(font, localizeTabLabel(text), x, y, color, shadow);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/trbeyond/utils/FormatingUtils;getTimeFormattedHMS(J)Lnet/minecraft/network/chat/Component;"
            )
    )
    private Component tensura_tno$translateRenderTime(long timeLeft) {
        return localizeTime(timeLeft);
    }

    @Redirect(
            method = "renderQuestEntry",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"
            )
    )
    private int tensura_tno$translateObjectiveAndCooldown(GuiGraphics guiGraphics, Font font, Component text, int x, int y, int color) {
        Component replacement = text;
        if (isLocalizationEnabled()) {
            String literal = getLiteralText(text);
            if ("objective".equalsIgnoreCase(literal)) {
                replacement = copyLiteralNormalized(text, tr("label.objective", "目标"));
            }
        }
        return guiGraphics.drawString(font, replacement, x, y, color);
    }

    @Redirect(
            method = "renderQuestEntry",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"
            )
    )
    private int tensura_tno$translateQuestTitle(
            GuiGraphics guiGraphics,
            Font font,
            Component text,
            int drawX,
            int drawY,
            int color,
            boolean shadow,
            GuiGraphics originalGraphics,
            int x,
            int y,
            @Coerce Object entry,
            int mouseX,
            int mouseY
    ) {
        Component replacement = text;
        if (isLocalizationEnabled()) {
            String localizedTitle = localizeQuestTitle(entry);
            if (localizedTitle != null && !localizedTitle.isBlank()) {
                MutableComponent line = Component.literal(localizedTitle + " ").setStyle(normalizeStyle(text.getStyle()));
                for (Component sibling : text.getSiblings()) {
                    line.append(copyAsDefaultFont(sibling));
                }
                replacement = line;
            }
        }
        return guiGraphics.drawString(font, replacement, drawX, drawY, color, shadow);
    }

    @Redirect(
            method = "renderQuestEntry",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/trbeyond/utils/FormatingUtils;getTimeFormattedHMS(J)Lnet/minecraft/network/chat/Component;"
            )
    )
    private Component tensura_tno$translateCooldownTime(long timeLeft) {
        return localizeTime(timeLeft);
    }

    @Redirect(
            method = "renderQuestEntry",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
            )
    )
    private void tensura_tno$translateRewardAmount(GuiGraphics guiGraphics, Font font, Component text, int centerX, int y, int color) {
        Component replacement = text;
        if (isLocalizationEnabled()) {
            String literal = getLiteralText(text);
            if ("sub".equalsIgnoreCase(literal)) {
                replacement = copyLiteralNormalized(text, tr("reward.sub", "部下"));
            }
        }
        guiGraphics.drawCenteredString(font, replacement, centerX, y, color);
    }

    private static Component localizeTabLabel(Component original) {
        if (!isLocalizationEnabled()) {
            return original;
        }

        String literal = getLiteralText(original);
        if (literal == null) {
            return original;
        }

        return switch (literal.toLowerCase()) {
            case "daily" -> copyLiteralNormalized(original, tr("category.daily", "每日"));
            case "weekly" -> copyLiteralNormalized(original, tr("category.weekly", "每周"));
            case "repeatable" -> copyLiteralNormalized(original, tr("category.repeatable", "循环"));
            case "you" -> copyLiteralNormalized(original, tr("tab.you", "自身"));
            case "sub" -> copyLiteralNormalized(original, tr("tab.sub", "部下"));
            default -> original;
        };
    }

    private static Component localizeTime(long timeLeft) {
        return originalTime(timeLeft);
    }

    private static Component originalTime(long timeLeft) {
        long hours = timeLeft / 72000L;
        long minutes = timeLeft % 72000L / 1200L;
        long seconds = timeLeft % 1200L / 20L;
        return Component.literal(String.valueOf(hours)).withStyle(Style.EMPTY.withFont(fontId("tensura_blue")))
                .append(Component.literal("h ").withStyle(Style.EMPTY.withFont(fontId("tensura_light_gray"))))
                .append(Component.literal(String.valueOf(minutes)).withStyle(Style.EMPTY.withFont(fontId("tensura_blue"))))
                .append(Component.literal("m ").withStyle(Style.EMPTY.withFont(fontId("tensura_light_gray"))))
                .append(Component.literal(String.valueOf(seconds)).withStyle(Style.EMPTY.withFont(fontId("tensura_blue"))))
                .append(Component.literal("s").withStyle(Style.EMPTY.withFont(fontId("tensura_light_gray"))));
    }

    private static ResourceLocation fontId(String path) {
        return ResourceLocation.fromNamespaceAndPath("beyond_gacha_c", path);
    }

    private static String localizeQuestTitle(Object entry) {
        try {
            Object quest = invoke(entry, "quest");
            if (quest == null) {
                return null;
            }

            ResourceLocation questId = (ResourceLocation) invoke(quest, "id");
            if (questId != null) {
                String overrideKey = KEY_PREFIX + "quest." + questId.getPath();
                Language language = Language.getInstance();
                if (language.has(overrideKey)) {
                    return language.getOrDefault(overrideKey);
                }
            }

            List<?> tasks = (List<?>) invoke(quest, "tasks");
            if (tasks == null || tasks.isEmpty()) {
                return questId != null ? autoFormat(questId.getPath()) : tr("misc.quest", "任务");
            }

            Object task = tasks.get(0);
            String taskType = (String) invoke(task, "getType");
            int amount = getTaskAmount(task);
            return switch (taskType) {
                case "player_kill" -> trf("task.player_kill", "击杀%s", resolveTargetName(getTaskTarget(task)));
                case "subordinate_kill" -> trf("task.subordinate_kill", "部下击杀%s", resolveTargetName(getTaskTarget(task)));
                case "player_mxp" -> trf("task.player_mxp", "获得%s MXP", String.valueOf(amount));
                case "subordinate_ep" -> trf("task.subordinate_ep", "部下获得%s EP", String.valueOf(amount));
                default -> fallbackQuestName(quest, questId);
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String fallbackQuestName(Object quest, ResourceLocation questId) {
        try {
            Optional<?> name = (Optional<?>) invoke(quest, "name");
            if (name != null && name.isPresent()) {
                return String.valueOf(name.get());
            }
        } catch (Exception ignored) {
        }
        return questId != null ? autoFormat(questId.getPath()) : tr("misc.quest", "任务");
    }

    private static String resolveTargetName(ResourceLocation targetId) {
        if (targetId == null) {
            return tr("misc.quest", "任务");
        }

        Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(targetId);
        if (entityType.isPresent()) {
            return entityType.get().getDescription().getString();
        }

        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(targetId);
        if (item.isPresent()) {
            return item.get().getDescription().getString();
        }

        return autoFormat(targetId.getPath());
    }

    private static ResourceLocation getTaskTarget(Object task) {
        try {
            Field field = findField(task.getClass(), "target");
            return field != null ? (ResourceLocation) field.get(task) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int getTaskAmount(Object task) {
        try {
            Method method = task.getClass().getMethod("getAmount");
            return ((Number) method.invoke(task)).intValue();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static MutableComponent copyLiteralNormalized(Component original, String text) {
        MutableComponent replacement = Component.literal(text).setStyle(normalizeStyle(original.getStyle()));
        for (Component sibling : original.getSiblings()) {
            replacement.append(copyAsDefaultFont(sibling));
        }
        return replacement;
    }

    private static MutableComponent copyAsDefaultFont(Component original) {
        String literal = getLiteralText(original);
        MutableComponent replacement = Component.literal(literal == null ? "" : literal).setStyle(normalizeStyle(original.getStyle()));
        for (Component sibling : original.getSiblings()) {
            replacement.append(copyAsDefaultFont(sibling));
        }
        return replacement;
    }

    private static Style normalizeStyle(Style original) {
        Style normalized = original.withFont(DEFAULT_FONT);
        Integer mappedColor = mapFontColor(original.getFont());
        if (mappedColor != null && original.getColor() == null) {
            normalized = normalized.withColor(mappedColor);
        }
        return normalized;
    }

    private static Style numberStyle() {
        return Style.EMPTY.withFont(DEFAULT_FONT).withColor(mapFontColor(fontId("tensura_blue")));
    }

    private static Style unitStyle() {
        return Style.EMPTY.withFont(DEFAULT_FONT).withColor(mapFontColor(fontId("tensura_light_gray")));
    }

    private static Integer mapFontColor(ResourceLocation fontId) {
        if (fontId == null) {
            return null;
        }

        return switch (fontId.getPath()) {
            case "tensura_light_blue" -> 0x84d6ff;
            case "tensura_blue" -> 0x5cb0ff;
            case "tensura_pastel_blue" -> 0xb9deff;
            case "tensura_gray" -> 0x9ba6b2;
            case "tensura_light_gray" -> 0xd7dde5;
            case "tensura_red" -> 0xff7a7a;
            case "tensura_light_green" -> 0x8ce890;
            default -> null;
        };
    }

    private static boolean isLocalizationEnabled() {
        return Language.getInstance().has(KEY_PREFIX + "category.daily");
    }

    private static String tr(String suffix, String fallback) {
        Language language = Language.getInstance();
        String key = KEY_PREFIX + suffix;
        return language.has(key) ? language.getOrDefault(key) : fallback;
    }

    private static String trf(String suffix, String fallback, Object... args) {
        return String.format(tr(suffix, fallback), args);
    }

    private static String autoFormat(String path) {
        String[] parts = path.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? path : builder.toString();
    }

    private static String getLiteralText(Component component) {
        try {
            Object contents = component.getContents();
            return (String) contents.getClass().getMethod("text").invoke(contents);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
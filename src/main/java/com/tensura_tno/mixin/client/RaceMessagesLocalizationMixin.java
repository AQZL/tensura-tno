package com.tensura_tno.mixin.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(targets = "org.crypticdev.stextras.quest.race.RaceMessages", remap = false)
public class RaceMessagesLocalizationMixin {

    private static final String LANG_PREFIX = "@lang:";
    private static final Set<String> SUPPORTED_RACES = Set.of(
        "stextras:divine_kobold",
        "stextras:divine_fox",
        "stextras:divine_wisp"
    );

    @Inject(
        method = "send(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/ResourceLocation;Lorg/crypticdev/stextras/quest/race/RaceMessages$Kind;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void tensura_tno$sendLocalizedByKind(ServerPlayer player, ResourceLocation raceId,
                            @Coerce Object kind, CallbackInfo ci) {
    String suffix = kindSuffix(kind);
    if (suffix == null || !SUPPORTED_RACES.contains(raceId.toString())) {
        return;
    }

    String key = "tensura_tno.race_prestige." + raceId.getNamespace() + "_" + raceId.getPath() + "." + suffix + ".chat";
    Component component = "start".equals(suffix)
        ? Component.translatable(key, resolveRaceComponent(raceId))
        : Component.translatable(key);
    sendChatIfPresent(player, component);
    ci.cancel();
    }

    @Inject(
            method = "send(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/ResourceLocation;Lorg/crypticdev/stextras/quest/race/RacePrestigeText;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void tensura_tno$sendLocalized(ServerPlayer player, ResourceLocation raceId,
                                                  @Coerce Object text, CallbackInfo ci) {
        if (text == null || !usesLangKey(text)) {
            return;
        }

        sendTitleIfPresent(player, resolveComponent(readStringField(text, "title"), raceId));
        sendSubtitleIfPresent(player, resolveComponent(readStringField(text, "subtitle"), raceId));
        sendChatIfPresent(player, resolveComponent(readStringField(text, "chat"), raceId));
        ci.cancel();
    }

    private static boolean usesLangKey(Object text) {
        return isLangKey(readStringField(text, "title"))
                || isLangKey(readStringField(text, "subtitle"))
                || isLangKey(readStringField(text, "chat"));
    }

    private static boolean isLangKey(@Nullable String value) {
        return value != null && value.startsWith(LANG_PREFIX) && value.length() > LANG_PREFIX.length();
    }

    private static void sendTitleIfPresent(ServerPlayer player, Component component) {
        if (!component.getString().isEmpty()) {
            player.connection.send(new ClientboundSetTitleTextPacket(component));
        }
    }

    private static void sendSubtitleIfPresent(ServerPlayer player, Component component) {
        if (!component.getString().isEmpty()) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(component));
        }
    }

    private static void sendChatIfPresent(ServerPlayer player, Component component) {
        if (!component.getString().isEmpty()) {
            player.sendSystemMessage(component);
        }
    }

    private static Component resolveComponent(@Nullable String raw, ResourceLocation raceId) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }

        if (!isLangKey(raw)) {
            return Component.literal(processLiteral(raw, raceId));
        }

        String key = raw.substring(LANG_PREFIX.length());
        return Component.translatable(key, resolveRaceComponent(raceId));
    }

    private static Component resolveRaceComponent(ResourceLocation raceId) {
        return Component.translatable(raceId.getNamespace() + ".race." + raceId.getPath());
    }

    private static String processLiteral(String input, ResourceLocation raceId) {
        return input.replace("%race%", formatRaceName(raceId));
    }

    private static String readStringField(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getField(fieldName);
            Object value = field.get(instance);
            return value instanceof String string ? string : "";
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static String formatRaceName(ResourceLocation raceId) {
        return Arrays.stream(raceId.getPath().split("_"))
                .filter(part -> !part.isEmpty())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String kindSuffix(@Nullable Object kind) {
        if (kind == null) {
            return null;
        }

        String name = kind.toString();
        return switch (name) {
            case "LORE" -> "lore";
            case "START" -> "start";
            case "COMPLETE" -> "complete";
            default -> null;
        };
    }
}
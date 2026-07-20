package com.tensura_tno.event;

import com.tensura_tno.TensuraTNOMod;

import dev.architectury.event.EventResult;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「真王降诞」成就处理器。
 *
 * <p>玩家进入收获祭（觉醒前的 3 分钟仪式）第一阶段时颁发成就。
 * 收获祭只会触发一次（觉醒前），因此该成就也只会颁发一次。
 *
 * <p>背景：{@link TensuraEntityEvents#ENTER_HARVEST_FESTIVAL_EVENT} 在
 * {@code ExistenceStorage#enterHarvestFestival} 进入仪式启动流程时触发。
 * 事件参数包含 {@code time}（仪式总时长 tick）和 {@code soul}（觉醒所需灵魂数）。
 * 我们只需要在事件触发时识别 {@link ServerPlayer} 并颁发成就，不需要修改这两个参数。
 *
 * <p>幂等：可重复 {@link #register()}，事件只会注册一次。
 */
public final class HarvestFestivalAchievementHandler {

    private static final ResourceLocation ADVANCEMENT_ID =
            ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "true_demon_lord_birth");

    /** 与 advancement JSON 中 criteria 的键一致。 */
    private static final String CRITERION_NAME = "trigger";

    private static volatile boolean registered = false;

    private HarvestFestivalAchievementHandler() {
    }

    public static void register() {
        if (registered) return;
        synchronized (HarvestFestivalAchievementHandler.class) {
            if (registered) return;
            TensuraEntityEvents.ENTER_HARVEST_FESTIVAL_EVENT.register((entity, time, soul) -> {
                if (entity instanceof ServerPlayer player) {
                    grant(player);
                }
                return EventResult.pass();
            });
            registered = true;
        }
    }

    private static void grant(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerAdvancementManager manager = server.getAdvancements();
        AdvancementHolder holder = manager.get(ADVANCEMENT_ID);
        if (holder == null) {
            TensuraTNOMod.LOGGER.warn(
                    "[TensuraTNO] Advancement {} not found, cannot grant 真王降诞 to {}",
                    ADVANCEMENT_ID, player.getGameProfile().getName());
            return;
        }
        PlayerAdvancements progress = player.getAdvancements();
        if (!progress.getOrStartProgress(holder).isDone()) {
            progress.award(holder, CRITERION_NAME);
        }
    }
}

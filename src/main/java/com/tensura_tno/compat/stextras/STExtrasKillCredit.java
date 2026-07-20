package com.tensura_tno.compat.stextras;

import com.tensura_tno.TensuraTNOMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * 通过反射桥接 STExtras 的击杀任务追踪系统，
 * 使召唤物击杀也能计入主人的任务进度。
 */
public final class STExtrasKillCredit {

    private static boolean initialized = false;
    private static boolean available = false;
    private static Method incrementMatchingMethod;
    private static Object killType; // QuestObjectiveType.KILL

    private STExtrasKillCredit() {}

    @SuppressWarnings("unchecked")
    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> listenerClass = Class.forName(
                    "org.crypticdev.stextras.handler.HandlePrestigeListeners");
            Class<?> questObjTypeClass = Class.forName(
                    "org.crypticdev.stextras.quest.definition.QuestObjectiveType");

            // QuestObjectiveType.KILL
            killType = Enum.valueOf((Class<Enum>) questObjTypeClass, "KILL");

            // private static void incrementMatching(ServerPlayer, QuestObjectiveType, ResourceLocation, int, LivingEntity)
            incrementMatchingMethod = listenerClass.getDeclaredMethod(
                    "incrementMatching",
                    ServerPlayer.class,
                    questObjTypeClass,
                    ResourceLocation.class,
                    int.class,
                    LivingEntity.class);
            incrementMatchingMethod.setAccessible(true);

            available = true;
            TensuraTNOMod.LOGGER.info("[TensuraTNO] STExtras kill credit bridge initialized");
        } catch (Exception e) {
            TensuraTNOMod.LOGGER.warn("[TensuraTNO] STExtras kill credit unavailable: {}", e.getMessage());
            available = false;
        }
    }

    /**
     * 将召唤物的击杀计入主人玩家的 STExtras 击杀任务进度。
     *
     * @param owner    召唤物的主人
     * @param entityId 被击杀实体的注册 ID
     * @param killed   被击杀的实体
     */
    public static void creditKill(ServerPlayer owner, ResourceLocation entityId, LivingEntity killed) {
        init();
        if (!available) return;
        try {
            incrementMatchingMethod.invoke(null, owner, killType, entityId, 1, killed);
        } catch (Exception e) {
            TensuraTNOMod.LOGGER.error("[TensuraTNO] Failed to credit summon kill to STExtras: {}", e.getMessage());
        }
    }
}

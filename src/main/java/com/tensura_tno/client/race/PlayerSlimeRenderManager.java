package com.tensura_tno.client.race;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensura_tno.ability.skill.HumanFormSkill;
import com.tensura_tno.race.SlimeRaceHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 客户端玩家 slime 渲染状态管理器：
 * <ul>
 *     <li>每玩家一个 {@link PlayerSlimeAnimatable}（用于独立 GeckoLib 动画状态）</li>
 *     <li>共享一个 {@link PlayerSlimeRenderer}（GeoObjectRenderer 是无状态的，仅持模型）</li>
 *     <li>由 {@link SlimeRacePlayerRenderHandler} 调用</li>
 * </ul>
 */
public final class PlayerSlimeRenderManager {

    /**
     * 4 个 slime 种族 ID。命中其一即触发 slime 替换渲染。
     * 与 {@code data/tensura/tags/manascore_race/races/slime.json} 保持一致。
     */
    public static final java.util.Set<ResourceLocation> SLIME_RACE_IDS = SlimeRaceHelper.SLIME_RACE_IDS;

    private static final PlayerSlimeRenderer RENDERER = new PlayerSlimeRenderer();
    private static final ConcurrentMap<UUID, PlayerSlimeAnimatable> ANIMATABLES = new ConcurrentHashMap<>();

    private PlayerSlimeRenderManager() {}

    /**
     * 是否该用 slime 模型渲染该玩家。综合两个条件：
     * <ol>
     *   <li>种族属于 4 个 slime 之一</li>
     *   <li>玩家身上没有 {@link HumanFormSkill#HUMAN_FORM_SCALE} modifier</li>
     * </ol>
     * 第二个条件同时覆盖了：
     * <ul>
     *   <li>主动按了人形状态技能</li>
     *   <li>用捕食者/暴食者吞噬了异世界人/玩家（由
     *       {@link com.tensura_tno.event.SlimePredationHumanFormHandler} 注入永久 modifier）</li>
     * </ul>
     */
    public static boolean shouldRenderAsSlime(Player player) {
        if (player == null) return false;
        if (player.isSpectator()) return false;
        if (isHumanFormActive(player)) return false;
        return isSlimeRace(player);
    }

    /** 仅检查种族（不考虑人形 modifier），供其它逻辑使用。 */
    public static boolean isSlimeRace(LivingEntity entity) {
        return SlimeRaceHelper.isSlimeRace(entity);
    }

    /** 玩家身上是否有 {@link HumanFormSkill#HUMAN_FORM_SCALE} 修饰器（人形状态）。 */
    private static boolean isHumanFormActive(Player player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null) return false;
        return scale.getModifier(HumanFormSkill.HUMAN_FORM_SCALE) != null;
    }

    /** 获取玩家对应的 GeoAnimatable（不存在则创建，存在则刷新 player 引用）。 */
    public static PlayerSlimeAnimatable getOrCreate(Player player) {
        return ANIMATABLES.compute(player.getUUID(), (uuid, existing) -> {
            if (existing == null) return new PlayerSlimeAnimatable(player);
            existing.setPlayer(player);
            return existing;
        });
    }

    /** 玩家退出 / 切维度时清理。 */
    public static void forget(UUID uuid) {
        ANIMATABLES.remove(uuid);
    }

    /**
     * 在指定 PoseStack 当前位置（已平移到玩家脚下）渲染 slime。
     * 调用方负责 push/pop。
     */
    public static void render(Player player, PoseStack poseStack, MultiBufferSource bufferSource,
                              int packedLight, float partialTick) {
        PlayerSlimeAnimatable animatable = getOrCreate(player);
        // GeoObjectRenderer.render(poseStack, animatable, bufferSource, renderType, buffer, light, partialTick)
        // renderType / buffer 都传 null，让渲染器走默认 entityCutout 路径
        RENDERER.render(poseStack, animatable, bufferSource, null, null, packedLight, partialTick);
    }
}

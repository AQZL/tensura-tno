package com.tensura_tno.ability.skill;

import com.tensura_tno.ability.skill.SpiritSummonSkill.SpiritSummonPockets;
import com.tensura_tno.world.TensuraTNOGameRules;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 灵之召唤的服务器端收纳规则。
 *
 * <p>规则由以下游戏规则（{@code /gamerule}）驱动：
 * <ul>
 *   <li>{@code spiritSummonVanillaOnly}（bool，默认 true）：仅原版生物可被收纳</li>
 *   <li>{@code spiritSummonMaxHealth}（int，默认 100）：可收纳生物的最大生命值上限</li>
 * </ul>
 *
 * <p>规则同时在两处生效：
 * <ol>
 *   <li>收纳尝试时（{@link SpiritSummonSkill#onPressed} 路径）：违规直接拒绝。</li>
 *   <li>玩家登录时（{@link #onPlayerLoggedIn}）：扫描收纳口袋，自动移除违规条目。</li>
 * </ol>
 */
public final class SpiritSummonLimits {

    /** 原版生物所在的命名空间。 */
    private static final String VANILLA_NAMESPACE = "minecraft";

    private SpiritSummonLimits() {}

    /** 当前世界是否启用 {@code spiritSummonVanillaOnly} 规则。 */
    public static boolean isVanillaOnlyEnforced(Level level) {
        return level.getGameRules().getBoolean(TensuraTNOGameRules.SPIRIT_SUMMON_VANILLA_ONLY);
    }

    /** 读取当前世界的 {@code spiritSummonMaxHealth} 上限。 */
    public static int getMaxHealthLimit(Level level) {
        return level.getGameRules().getInt(TensuraTNOGameRules.SPIRIT_SUMMON_MAX_HEALTH);
    }

    /** 实体 ID 是否属于原版命名空间。 */
    public static boolean isVanilla(ResourceLocation entityId) {
        return entityId != null && VANILLA_NAMESPACE.equals(entityId.getNamespace());
    }

    /**
     * 实体当前最大生命值是否在 {@code spiritSummonMaxHealth} 允许范围内。
     *
     * <p><b>取值口径</b>：使用 {@link Attributes#MAX_HEALTH} 的 <em>基础值</em>
     * （{@link AttributeInstance#getBaseValue()}），<b>不</b>使用
     * {@link LivingEntity#getMaxHealth()} 或 {@link AttributeInstance#getValue()}。
     *
     * <p>原因：Tensura 的 {@code CookSkill}（"厨"独有技能）会通过添加负值
     * {@link AttributeModifier}（identifier = {@code tensura:cook}，
     * {@link AttributeModifier.Operation#ADD_VALUE ADD_VALUE}）削弱目标的
     * {@code MAX_HEALTH}。如果按 {@code getMaxHealth()} 比较，本规则就会被
     * Cook 临时削弱后绕过：原本超过上限的高血量怪会被「煮」到上限以下，
     * 然后被收纳。改用 {@code getBaseValue()} 后，比较的是该生物的「默认
     * 总血量」（属性 base 值），不受 modifier 影响。
     *
     * <p>对于没有 {@code MAX_HEALTH} 属性的实体（不应发生于 LivingEntity，
     * 但作为防御性兜底），返回 {@code false}（视为不允许收纳）。
     */
    public static boolean isMaxHealthAllowed(Level level, LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return false;
        return attr.getBaseValue() <= getMaxHealthLimit(level);
    }

    /**
     * 玩家登录监听器：扫描收纳口袋，移除不符合当前游戏规则的实体条目。
     *
     * <p>清理策略（仅当对应 gamerule 启用时才生效）：
     * <ul>
     *   <li>无法解析为 {@link ResourceLocation} 的条目 → 始终移除</li>
     *   <li>{@code spiritSummonVanillaOnly = true} 且非 {@code minecraft} 命名空间 → 移除</li>
     *   <li>未在 {@link BuiltInRegistries#ENTITY_TYPE} 中注册的条目 → 始终移除</li>
     *   <li>非 {@link LivingEntity} 的条目 → 始终移除</li>
     *   <li>{@link LivingEntity#getMaxHealth()} &gt; {@code spiritSummonMaxHealth} → 移除</li>
     * </ul>
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Level level = player.level();
        GameRules rules = level.getGameRules();
        boolean enforceVanilla = rules.getBoolean(TensuraTNOGameRules.SPIRIT_SUMMON_VANILLA_ONLY);
        int maxHpLimit = rules.getInt(TensuraTNOGameRules.SPIRIT_SUMMON_MAX_HEALTH);

        // 复制一份以避免边遍历边修改
        var entries = new java.util.ArrayList<>(SpiritSummonPockets.getAbsorbedEntities(player));
        for (String entityId : entries) {
            if (!isEntryAllowed(level, entityId, enforceVanilla, maxHpLimit)) {
                SpiritSummonPockets.removeAbsorbedEntity(player, entityId);
            }
        }
    }

    /** 判定一个收纳口袋条目（实体 ID 字符串）是否符合当前规则快照。 */
    private static boolean isEntryAllowed(Level level, String entityId, boolean enforceVanilla, int maxHpLimit) {
        ResourceLocation rl = ResourceLocation.tryParse(entityId);
        if (rl == null) return false;
        if (enforceVanilla && !isVanilla(rl)) return false;

        // BuiltInRegistries.ENTITY_TYPE.get 对未注册键会返回默认值（PIG），
        // 必须用 containsKey 检测真实存在
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) return false;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);

        // 创建临时实体读取其默认最大生命值（finalizeSpawn 未调用，使用属性默认值）。
        // 比较时使用 MAX_HEALTH 属性的基础值，而不是 getMaxHealth() / getValue()，
        // 以保持与 isMaxHealthAllowed 一致：临时实体不会有 Cook modifier，所以
        // 这里两种取法等价；但显式 getBaseValue() 可避免未来注册表层/属性默认
        // 修饰器的潜在干扰，行为更稳定。
        Entity probe = type.create(level);
        try {
            if (!(probe instanceof LivingEntity living)) return false;
            AttributeInstance attr = living.getAttribute(Attributes.MAX_HEALTH);
            if (attr == null) return false;
            return attr.getBaseValue() <= maxHpLimit;
        } finally {
            if (probe != null) probe.discard();
        }
    }
}

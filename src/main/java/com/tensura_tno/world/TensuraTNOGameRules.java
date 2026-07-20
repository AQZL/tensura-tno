package com.tensura_tno.world;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Category;
import net.minecraft.world.level.GameRules.IntegerValue;

/**
 * Tensura TNO 自定义游戏规则（{@code /gamerule}）。
 *
 * <p>当前规则（默认均为"不受限制"，需服务器管理员主动开启/收紧）：
 * <ul>
 *   <li>{@link #SPIRIT_SUMMON_VANILLA_ONLY} —— {@code spiritSummonVanillaOnly}（默认 {@code false}）：
 *       若为 {@code true}，灵之召唤只能收纳 {@code minecraft} 命名空间的原版生物。</li>
 *   <li>{@link #SPIRIT_SUMMON_MAX_HEALTH} —— {@code spiritSummonMaxHealth}
 *       （默认 {@link Integer#MAX_VALUE}，相当于不限制）：
 *       灵之召唤可收纳的生物最大生命值上限（含）。超过即拒绝收纳。</li>
 * </ul>
 *
 * <p>{@link #init()} 必须在主世界加载之前调用（典型位置：模组主类构造器顶部）。
 */
public final class TensuraTNOGameRules {

    public static GameRules.Key<GameRules.BooleanValue> SPIRIT_SUMMON_VANILLA_ONLY;
    public static GameRules.Key<GameRules.IntegerValue> SPIRIT_SUMMON_MAX_HEALTH;

    private TensuraTNOGameRules() {}

    public static void init() {
        SPIRIT_SUMMON_VANILLA_ONLY = GameRules.register(
                "spiritSummonVanillaOnly",
                Category.PLAYER,
                BooleanValue.create(false));

        SPIRIT_SUMMON_MAX_HEALTH = GameRules.register(
                "spiritSummonMaxHealth",
                Category.PLAYER,
                IntegerValue.create(Integer.MAX_VALUE));
    }
}

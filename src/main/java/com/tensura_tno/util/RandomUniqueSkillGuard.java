package com.tensura_tno.util;

/**
 * 守卫 stextras 2.0.9 在 {@code ReincarnationMenu.randomUniqueSkill} 中新增的
 * {@code stextras$applyCustomSkillLocks} 注入。
 *
 * <p><b>问题背景</b>：stextras 2.0.9 的新注入在 randomUniqueSkill 里读取玩家的
 * customLocks / adminLocks 列表，把命中 collection 的技能从抽取池剔除，并把
 * skills 抽取数减扣相应数量——但<b>它从不调用 grantedUniqueSkill 真正授予</b>
 * 这些锁定技能。结果：只要锁定列表中有任意一个 ID 同时满足"在 SKILLS 池里"
 * 且"玩家此刻没拥有该技能"，转生时玩家就会少抽（极端情况下抽取数变为 0，
 * 用重置卷轴拿不到任何独特技能）。
 *
 * <p><b>修复策略</b>：进入 randomUniqueSkill 时把 ACTIVE 设为 true；stextras 接
 * 下来调用 {@code STExtarsStorage$Player.getLockedSkills(ServerPlayer)} 与
 * {@code getAdminLockedSkills(ServerPlayer)} 时，被
 * {@code STExtrasLockBypassDuringRandomUniqueSkillMixin} 改为返回空列表。
 * 这让 stextras 的 applyCustomSkillLocks 退化为 no-op，randomUniqueSkill 恢复
 * 到旧版 (2.0.8) 行为。其他场景（GUI 锁定、技能保留监听器）不受影响。
 */
public final class RandomUniqueSkillGuard {
    public static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private RandomUniqueSkillGuard() {}
}

package com.tensura_tno.mixin.client;

import com.tensura_tno.compat.stextras.SkillLockEligibility;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让所有独特技能（以及任意技能）都被视为可锁定的有效技能，
 * 无需在 stextras 的 skill_lock_config.json 中手动维护 validSkillsTOLock 列表。
 * <p>
 * <b>例外 1：</b>究极技能（{@link Skill.SkillType#ULTIMATE}）永远不可锁定。
 * 这一判定基于 Tensura 自身的 {@code SkillType}，因此自动覆盖：
 * <ul>
 *   <li>Tensura 本体的究极技能（大贤者、变质者、毁灭之王、誓约之王 等）</li>
 *   <li>任何附属模组继承 {@link Skill} 并以 ULTIMATE 注册的究极技能
 *       （如 foxablazeultimate、Nogamenolife、Mysticism、Beyond Adventures 等）</li>
 * </ul>
 * <b>例外 2：</b>{@link #UNLOCKABLE_SKILL_IDS} 中的种族绑定固有技能不可锁定。
 * 这些技能离开对应种族就无法使用，锁了等于浪费一格锁槽位（玩家反馈 Bug）。
 */
@Mixin(targets = "org.crypticdev.stextras.storage.STExtarsStorage$Player", remap = false)
public class SkillLockValidAllSkillsMixin {

    /**
     * 种族绑定的固有技能 ID 黑名单。锁定这些技能等于浪费锁槽位，因为离开种族技能就废了。
     * 当前包含狐灵种族专属：契约小狐 / 灵之召唤。
     */
    private static final java.util.Set<String> UNLOCKABLE_SKILL_IDS = java.util.Set.of(
            "tensura_tno:spirit_summon",
            "tensura_tno:contract_little_fox"
    );

    @Inject(method = "isSkillValid", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tno$makeAllSkillsValid(ResourceLocation skill, CallbackInfoReturnable<Boolean> cir) {
        if (skill == null) {
            return;
        }
        // 种族绑定固有技能黑名单
        if (UNLOCKABLE_SKILL_IDS.contains(skill.toString())) {
            cir.setReturnValue(false);
            return;
        }
        // 究极技能（Tensura SkillType.ULTIMATE）一律不可锁定
        if (SkillLockEligibility.isUltimateSkill(skill)) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(true);
    }
}

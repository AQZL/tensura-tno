package com.tensura_tno.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 将技能锁定槽位改为直接按灵魂点数 / 20 计算，完全绕过配置文件中的阈值列表。
 *
 * 计算链：
 *   getSoulGradeLevel(rawSoulGrade, thresholds) → 本 Mixin 返回 rawSoulGrade / 20
 *   getMaxLocksForSoulGradeLevel(level)         → 本 Mixin 返回 level（即 rawSoulGrade / 20）
 *
 * 例：20点→1槽，40点→2槽，166点→8槽，无上限。
 */
@Mixin(targets = "org.crypticdev.stextras.storage.STExtarsStorage$Player", remap = false)
public class SkillLockSlotBySoulGradeMixin {

    /**
     * 拦截私有方法 getSoulGradeLevel(int soulGrade, List thresholds)，
     * 直接返回 soulGrade / 20，忽略阈值列表。
     * 这是服务端和客户端两个公共 getSoulGradeLevel 重载的共同实现源头。
     */
    @Inject(method = "getSoulGradeLevel(ILjava/util/List;)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void tno$soulGradeLevelByRaw(int soulGrade, List<Integer> thresholds,
                                                 CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Math.max(0, soulGrade / 20));
    }

    /**
     * 拦截 getMaxLocksForSoulGradeLevel(int level)，直接返回 level 本身，
     * 让槽位数 == 灵魂等级（即 rawSoulGrade / 20）。
     */
    @Inject(method = "getMaxLocksForSoulGradeLevel",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void tno$maxLockEqualsLevel(int soulGradeLevel,
                                                CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(soulGradeLevel);
    }
}


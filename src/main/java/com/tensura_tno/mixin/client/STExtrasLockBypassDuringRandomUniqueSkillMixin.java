package com.tensura_tno.mixin.client;

import com.tensura_tno.util.RandomUniqueSkillGuard;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 中和 stextras 2.0.9 在 {@code ReincarnationMenu.randomUniqueSkill} 里新增的
 * {@code stextras$applyCustomSkillLocks} 注入引发的"独特技能抽取数清零"BUG。
 *
 * <p><b>BUG 说明</b>：stextras 2.0.9 的 applyCustomSkillLocks 复制了 Tensura 原版处理
 * lockedSkills 的循环模式，<b>但漏掉了 {@code grantedUniqueSkill(...)} 那一步</b>。
 * 结果它只把锁定技能从抽取池剔除并把 {@code skills} 局部变量减扣对应数量，<b>从不真正
 * 授予这些技能</b>。当玩家锁定列表中有任意 ID 同时满足"在 SKILLS 池里"且"玩家此刻没拥
 * 有该技能"时，{@code skills} 会被减为 0，转生时拿不到任何独特技能。
 *
 * <p><b>修复策略</b>：在 randomUniqueSkill 执行期间（由
 * {@link RandomUniqueSkillStextrasGuardMixin} 在 HEAD/RETURN 维护
 * {@link RandomUniqueSkillGuard#ACTIVE}），把 stextras 读取的两个锁定列表都
 * 强制返回空。这样 applyCustomSkillLocks 退化为 no-op，randomUniqueSkill 行为回到
 * stextras 2.0.8 时代（一切正常）。其他场景中 GUI 锁定、技能保留监听器
 * (HandleSkillLock 的 REMOVE_SKILL 监听) 都完全不受影响。
 */
@Mixin(targets = "org.crypticdev.stextras.storage.STExtarsStorage$Player", remap = false)
public class STExtrasLockBypassDuringRandomUniqueSkillMixin {

    @Inject(
        method = "getLockedSkills(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void tno$bypassLockedSkillsDuringRandomUnique(
            net.minecraft.server.level.ServerPlayer player,
            CallbackInfoReturnable<List<ResourceLocation>> cir) {
        if (RandomUniqueSkillGuard.ACTIVE.get()) {
            cir.setReturnValue(List.of());
        }
    }

    @Inject(
        method = "getAdminLockedSkills(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void tno$bypassAdminLockedSkillsDuringRandomUnique(
            net.minecraft.server.level.ServerPlayer player,
            CallbackInfoReturnable<List<ResourceLocation>> cir) {
        if (RandomUniqueSkillGuard.ACTIVE.get()) {
            cir.setReturnValue(List.of());
        }
    }
}

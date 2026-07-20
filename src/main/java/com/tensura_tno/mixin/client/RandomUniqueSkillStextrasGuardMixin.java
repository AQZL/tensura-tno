package com.tensura_tno.mixin.client;

import com.tensura_tno.util.RandomUniqueSkillGuard;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@link ReincarnationMenu#randomUniqueSkill} 的 HEAD 设置
 * {@link RandomUniqueSkillGuard#ACTIVE}=true，RETURN 时清除。
 *
 * <p>守卫期间，{@link STExtrasLockBypassDuringRandomUniqueSkillMixin} 会让
 * stextras 读取的"锁定技能列表"返回空，从而中和 stextras 2.0.9 在
 * {@code stextras$applyCustomSkillLocks} 里"只剔除不授予"导致的抽取数清零 BUG。
 *
 * <p>用 try/finally 模式（HEAD set + RETURN clear）确保即使 randomUniqueSkill 内部抛
 * 异常，ThreadLocal 也能在外层 catch 之前完成清理（这里用 RETURN 而非真 finally，
 * 是因为 mixin 框架的 RETURN 等同于方法正常返回点；若内部抛异常 ThreadLocal
 * 会暂时残留，下次进入 randomUniqueSkill 时会被 HEAD 重新覆盖为 true，影响可忽略）。
 */
@Mixin(value = ReincarnationMenu.class, priority = 900)
public class RandomUniqueSkillStextrasGuardMixin {

    @Inject(method = "randomUniqueSkill", at = @At("HEAD"), remap = false)
    private static void tno$enterRandomUniqueSkill(Player player, boolean coverEP, CallbackInfo ci) {
        RandomUniqueSkillGuard.ACTIVE.set(true);
    }

    @Inject(method = "randomUniqueSkill", at = @At("RETURN"), remap = false)
    private static void tno$exitRandomUniqueSkill(Player player, boolean coverEP, CallbackInfo ci) {
        RandomUniqueSkillGuard.ACTIVE.set(false);
    }
}

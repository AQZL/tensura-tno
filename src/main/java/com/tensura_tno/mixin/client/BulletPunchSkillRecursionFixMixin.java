package com.tensura_tno.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 BulletPunchSkill 的无限递归问题。
 *
 * 根本原因：
 *   onDamageEntity 通过 server.submit(lambda) 提交额外伤害，
 *   但 NeoForge 在服务器线程上 submit() 是同步执行的。
 *   lambda 调用 target.hurt() 再次触发 onDamageEntity，
 *   而此时 setCoolDown 尚未执行（在 for 循环之后），
 *   导致递归调用也进入提交分支 → 栈溢出崩溃。
 *
 * 修复方式：ThreadLocal 重入守卫，递归调用直接返回 true。
 * 使用 targets 字符串避免编译期依赖 mysticism jar。
 */
@Mixin(targets = "io.github.Memoires.mysticism.ability.skill.intrinsic.BulletPunchSkill", remap = false)
public class BulletPunchSkillRecursionFixMixin {

    private static final ThreadLocal<Boolean> BULLET_PUNCH_ACTIVE = ThreadLocal.withInitial(() -> false);

    @Inject(
        method = "onDamageEntity",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void tensuraTno$guardReentry(CallbackInfoReturnable<Boolean> cir) {
        if (BULLET_PUNCH_ACTIVE.get()) {
            cir.setReturnValue(true);
        } else {
            BULLET_PUNCH_ACTIVE.set(true);
        }
    }

    @Inject(
        method = "onDamageEntity",
        at = @At("RETURN"),
        remap = false
    )
    private void tensuraTno$clearGuard(CallbackInfoReturnable<Boolean> cir) {
        BULLET_PUNCH_ACTIVE.set(false);
    }
}

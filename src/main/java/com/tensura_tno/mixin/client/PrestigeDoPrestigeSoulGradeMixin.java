package com.tensura_tno.mixin.client;

import com.tensura_tno.TensuraTNOCompatConfig;
import com.tensura_tno.util.PrestigeGuard;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1. 替换 doPrestige() 里写死的 +1 soulGrade 为配置值。
 * 2. 设置 ThreadLocal 守卫，让 RacePrestigeSoulGradeMixin 知道
 *    当前处于 doPrestige 流程（而非 resetPlayer 流程）。
 */
@Mixin(targets = "org.crypticdev.stextras.utils.PrestigeUtils", remap = false)
public class PrestigeDoPrestigeSoulGradeMixin {

    @Inject(method = "doPrestige", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tno$setPrestigeFlag(ServerPlayer player, CallbackInfo ci) {
        if (PrestigeGuard.BLOCKED.get()) {
            ci.cancel();
            return;
        }
        PrestigeGuard.IN_PROGRESS.set(true);
    }

    @Inject(method = "doPrestige", at = @At("RETURN"), remap = false)
    private static void tno$clearPrestigeFlag(ServerPlayer player, CallbackInfo ci) {
        PrestigeGuard.IN_PROGRESS.set(false);
    }

    @ModifyArg(
        method = "doPrestige",
        at = @At(
            value = "INVOKE",
            target = "Lorg/crypticdev/stextras/storage/STExtarsStorage$Player;setSoulGrade(Lnet/minecraft/server/level/ServerPlayer;I)V"
        ),
        index = 1,
        remap = false
    )
    private static int tno$modifySoulGradeGain(int original) {
        return original - 1 + TensuraTNOCompatConfig.getSoulGradePerPrestige();
    }
}

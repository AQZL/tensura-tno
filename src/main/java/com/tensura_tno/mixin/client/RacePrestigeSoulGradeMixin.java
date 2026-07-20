package com.tensura_tno.mixin.client;

import com.tensura_tno.TensuraTNOCompatConfig;
import com.tensura_tno.ftb.STExtrasHelper;
import com.tensura_tno.util.PrestigeGuard;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复种族声望完成时 soulGrade 只增加 JSON 硬编码值（通常为 4）而非配置值的 Bug。
 *
 * <p>只在 doPrestige() 流程中生效。resetPlayer() 也会调用
 * tryCompleteRacePrestigeForCurrentRace()，但不应授予 soul grade。
 * 通过 {@link PrestigeGuard} 的 ThreadLocal 守卫确保只有 doPrestige 路径能触发。
 */
@Mixin(targets = "org.crypticdev.stextras.quest.assignment.RacePrestigeAssignmentService", remap = false)
public class RacePrestigeSoulGradeMixin {

    @Redirect(
        method = "tryCompleteForCurrentRace",
        at = @At(
            value = "INVOKE",
            target = "Lorg/crypticdev/stextras/storage/STExtarsStorage$Player;setSoulGrade(Lnet/minecraft/server/level/ServerPlayer;I)V"
        ),
        remap = false
    )
    private static void tno$redirectRacePrestigeSoulGrade(ServerPlayer player, int ignoredValue) {
        if (!PrestigeGuard.IN_PROGRESS.get()) return;
        int current = STExtrasHelper.getSoulGrade(player);
        STExtrasHelper.setSoulGrade(player, current + TensuraTNOCompatConfig.getSoulGradePerPrestige());
    }
}

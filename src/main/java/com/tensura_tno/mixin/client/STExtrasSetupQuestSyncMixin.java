package com.tensura_tno.mixin.client;

import com.tensura_tno.ftb.STExtrasHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * STExtras 初始化/校验玩家任务后，同步服务端任务状态给客户端。
 * 修复 Tensura 主界面 Reset Counter tooltip 先读取旧客户端任务缓存的问题。
 */
@Mixin(targets = "org.crypticdev.stextras.quest.assignment.PlayerQuestAssignmentService", remap = false)
public class STExtrasSetupQuestSyncMixin {

    @Inject(
            method = "setupQuests(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private static void tensuraTno$syncAfterSetupQuests(MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        STExtrasHelper.syncQuestState(player);
    }

    @Inject(
            method = "ensurePlayerMatchesSchedule(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private static void tensuraTno$syncAfterScheduleCheck(MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        STExtrasHelper.syncQuestState(player);
    }
}

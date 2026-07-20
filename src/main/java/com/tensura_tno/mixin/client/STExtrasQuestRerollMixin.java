package com.tensura_tno.mixin.client;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 当 STExtras 刷新玩家的每日/每周委托时，
 * 通过反射调用 FTBQuestsResetHelper 重置对应 FTB Quest 任务进度。
 * 使用反射是为了避免 Mixin 类直接依赖 FTBQuests，保证 FTBQuests 未安装时不崩溃。
 */
@Mixin(targets = "org.crypticdev.stextras.quest.assignment.PlayerQuestAssignmentService", remap = false)
public class STExtrasQuestRerollMixin {

    @Inject(
            method = "rerollPlayerQuests(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;ZZ)V",
            at = @At("TAIL"),
            remap = false
    )
    private static void tno$onQuestReroll(
            MinecraftServer server,
            ServerPlayer player,
            boolean rerollDaily,
            boolean rerollWeekly,
            CallbackInfo ci
    ) {
        try {
            Class.forName("com.tensura_tno.ftb.FTBQuestsResetHelper")
                    .getMethod("onQuestReroll", ServerPlayer.class, boolean.class, boolean.class)
                    .invoke(null, player, rerollDaily, rerollWeekly);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // FTBQuests 未安装，跳过
        } catch (Exception e) {
            // 忽略其他反射异常
        }
    }
}

package com.tensura_tno.mixin.client;

import com.tensura_tno.ftb.STExtrasHelper;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * STExtras 分配 Reset Counter 的 REQUIRED 必要任务后，立刻把服务端任务状态同步给客户端。
 * 否则 Tensura 主界面的 Reset Counter tooltip 会读到旧的客户端任务缓存。
 */
@Mixin(targets = "org.crypticdev.stextras.quest.assignment.RequiredQuestSelector", remap = false)
public class STExtrasRequiredQuestSyncMixin {

    @Inject(
            method = "applyRequiredOnCharacterReset(Lnet/minecraft/server/level/ServerPlayer;I)V",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private static void tensuraTno$syncRequiredQuests(ServerPlayer player, int prestige, CallbackInfo ci) {
        STExtrasHelper.syncQuestState(player);
    }
}

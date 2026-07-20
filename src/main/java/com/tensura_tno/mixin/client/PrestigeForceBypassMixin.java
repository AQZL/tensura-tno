package com.tensura_tno.mixin.client;

import com.tensura_tno.util.AdminPrestigeBypass;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 当玩家被管理员指令标记为"强制声望"时，
 * 直接让 canPrestige() 返回 true，跳过所有条件检查。
 */
@Mixin(targets = "org.crypticdev.stextras.utils.PrestigeUtils", remap = false)
public class PrestigeForceBypassMixin {

    @Inject(method = "canPrestige", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tno$adminForcePrestige(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (AdminPrestigeBypass.isEnabled(player.getUUID())) {
            cir.setReturnValue(true);
        }
    }
}

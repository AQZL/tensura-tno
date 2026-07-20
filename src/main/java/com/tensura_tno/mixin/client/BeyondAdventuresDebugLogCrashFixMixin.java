package com.tensura_tno.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes a dedicated-server crash caused by beyond_gacha_c's Common.onAnvilUpdate
 * unconditionally calling DebugUtils.log(), which references net.minecraft.client.Minecraft.
 *
 * When DebugUtils is class-loaded on a dedicated server, RuntimeDistCleaner throws:
 * "Attempted to load class net/minecraft/client/Minecraft for invalid dist DEDICATED_SERVER"
 *
 * Fix: Redirect the DebugUtils.log(Object) invocation to a no-op so that DebugUtils
 * is never loaded, while the rest of onAnvilUpdate (the anvil recipe logic) still runs.
 */
@Pseudo
@Mixin(targets = "com.trbeyond.neoforge.event.Common", remap = false)
public class BeyondAdventuresDebugLogCrashFixMixin {

    @Redirect(
        method = "onAnvilUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lcom/trbeyond/utils/DebugUtils;log(Ljava/lang/Object;)V"
        ),
        remap = false,
        require = 0
    )
    private static void tensuraTno$suppressClientOnlyDebugLog(Object object) {
        // No-op: DebugUtils.log() calls Minecraft.getInstance() which is client-only
        // and crashes on a dedicated server. The log call carries no gameplay logic.
    }
}

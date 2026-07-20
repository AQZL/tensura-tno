package com.tensura_tno.mixin.client;

import com.tensura_tno.TensuraTNOCompatConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "io.github.manasmods.tensura.world.TensuraGameRules", remap = false)
public class NpcWorkingDefaultMixin {

    @ModifyArg(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules$BooleanValue;create(Z)Lnet/minecraft/world/level/GameRules$Type;",
            ordinal = 17
        ),
        require = 0,
        remap = false
    )
    private static boolean tensuraTno$disableNpcWorkingByDefault(boolean original) {
        if (TensuraTNOCompatConfig.isNpcWorkingDefaultDisabled()) {
            return false;
        }
        return original;
    }
}

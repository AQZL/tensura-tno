package com.tensura_tno.mixin.client;

import com.tensura_tno.compat.stextras.SkillLockEligibility;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "org.crypticdev.stextras.storage.STExtarsStorage$Player", remap = false)
public class SkillLockResistanceBlockMixin {
    @Inject(method = "canLockSkill(Lnet/minecraft/resources/ResourceLocation;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tno$blockClientResistanceLock(ResourceLocation id, CallbackInfoReturnable<Boolean> cir) {
        if (SkillLockEligibility.isResistanceSkill(id)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canLockSkill(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/ResourceLocation;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tno$blockServerResistanceLock(ServerPlayer player, ResourceLocation id, CallbackInfoReturnable<Boolean> cir) {
        if (SkillLockEligibility.isResistanceSkill(id)) {
            cir.setReturnValue(false);
        }
    }
}

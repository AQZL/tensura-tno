package com.tensura_tno.mixin.client;

import com.tensura_tno.compat.YigdCompat;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.manasmods.tensura.entity.human.CloneEntity", remap = false)
public class CloneEntityYigdRemoveMixin {

    @Inject(method = "remove()V", at = @At("HEAD"), remap = false, require = 0)
    private void tensuraTno$createYigdGraveBeforeRemove(CallbackInfo ci) {
        YigdCompat.createGraveForManualCloneRemoval((LivingEntity) (Object) this);
    }
}

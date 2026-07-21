package com.tensura_tno.mixin.client;

import com.tensura_tno.client.compat.MoreSkillsAlbisVinaVfxOptimizer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.github.wal_bos.moreskills.client.fx.AlbisVinaVfxClient$Beam", remap = false)
public abstract class MoreSkillsAlbisVinaBeamOptimizationMixin {
    @Shadow private Vec3 start;
    @Shadow private Vec3 end;
    @Shadow private float radius;

    @Inject(method = "render(Lorg/joml/Matrix4f;J)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tensuraTno$cullBeam(Matrix4f matrix, long now, CallbackInfo ci) {
        if (!MoreSkillsAlbisVinaVfxOptimizer.isBeamVisible(this.start, this.end, this.radius)) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "renderThreads", constant = @Constant(intValue = 20), require = 0, remap = false)
    private int tensuraTno$threads(int original) {
        return MoreSkillsAlbisVinaVfxOptimizer.count(original, this.start.lerp(this.end, 0.5D));
    }

    @ModifyConstant(method = "renderMotes", constant = @Constant(intValue = 64), require = 0, remap = false)
    private int tensuraTno$motes(int original) {
        return MoreSkillsAlbisVinaVfxOptimizer.count(original, this.start.lerp(this.end, 0.5D));
    }
}

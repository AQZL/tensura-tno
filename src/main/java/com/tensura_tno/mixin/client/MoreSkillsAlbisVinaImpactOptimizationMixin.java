package com.tensura_tno.mixin.client;

import com.tensura_tno.client.compat.MoreSkillsAlbisVinaVfxOptimizer;
import net.minecraft.util.Mth;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cuts Albis Vina's repeated terrain scans and scales its decorative geometry by distance. */
@Pseudo
@Mixin(targets = "com.github.wal_bos.moreskills.client.fx.AlbisVinaVfxClient$Impact", remap = false)
public abstract class MoreSkillsAlbisVinaImpactOptimizationMixin {
    private static final int TENSURA_TNO$CACHE_SIZE = 4096;
    private final long[] tensuraTno$surfaceKeys = new long[TENSURA_TNO$CACHE_SIZE];
    private final double[] tensuraTno$surfaceValues = new double[TENSURA_TNO$CACHE_SIZE];
    private final int[] tensuraTno$surfaceGenerations = new int[TENSURA_TNO$CACHE_SIZE];
    private int tensuraTno$surfaceGeneration = 1;

    @Shadow private Vec3 origin;
    @Shadow private float visualRadius;

    @Inject(method = "render(Lorg/joml/Matrix4f;J)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tensuraTno$beginRender(Matrix4f matrix, long now, CallbackInfo ci) {
        if (++this.tensuraTno$surfaceGeneration == 0) {
            java.util.Arrays.fill(this.tensuraTno$surfaceGenerations, 0);
            this.tensuraTno$surfaceGeneration = 1;
        }
        if (!MoreSkillsAlbisVinaVfxOptimizer.isImpactVisible(this.origin, this.visualRadius)) {
            ci.cancel();
        }
    }

    @Inject(method = "sampleSurfaceY(DDD)D", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tensuraTno$reuseSurfaceY(double x, double z, double yHint, CallbackInfoReturnable<Double> cir) {
        int slot = this.tensuraTno$surfaceSlot(Mth.floor(x), Mth.floor(z));
        long key = tensuraTno$surfaceKey(Mth.floor(x), Mth.floor(z));
        if (this.tensuraTno$surfaceGenerations[slot] == this.tensuraTno$surfaceGeneration
                && this.tensuraTno$surfaceKeys[slot] == key) {
            cir.setReturnValue(this.tensuraTno$surfaceValues[slot]);
        }
    }

    @Inject(method = "sampleSurfaceY(DDD)D", at = @At("RETURN"), require = 0, remap = false)
    private void tensuraTno$cacheSurfaceY(double x, double z, double yHint, CallbackInfoReturnable<Double> cir) {
        int slot = this.tensuraTno$surfaceSlot(Mth.floor(x), Mth.floor(z));
        this.tensuraTno$surfaceKeys[slot] = tensuraTno$surfaceKey(Mth.floor(x), Mth.floor(z));
        this.tensuraTno$surfaceValues[slot] = cir.getReturnValue();
        this.tensuraTno$surfaceGenerations[slot] = this.tensuraTno$surfaceGeneration;
    }

    private int tensuraTno$surfaceSlot(int x, int z) {
        int hash = x * 0x1f1f1f1f ^ z * 0x5f356495;
        return (hash ^ hash >>> 16) & (TENSURA_TNO$CACHE_SIZE - 1);
    }

    private static long tensuraTno$surfaceKey(int x, int z) {
        return (long) x << 32 ^ z & 0xffffffffL;
    }

    @ModifyConstant(method = "renderSubsurfaceIceBlooms", constant = @Constant(intValue = 520), require = 0, remap = false)
    private int tensuraTno$blooms(int original) { return tensuraTno$count(original); }
    @ModifyConstant(method = "renderDenseCrystalGrain", constant = @Constant(intValue = 1350), require = 0, remap = false)
    private int tensuraTno$grain(int original) { return tensuraTno$count(original); }
    @ModifyConstant(method = "renderSideFrostVeins", constant = @Constant(intValue = 480), require = 0, remap = false)
    private int tensuraTno$veins(int original) { return tensuraTno$count(original); }
    @ModifyConstant(method = "renderDeepFrostFibers", constant = @Constant(intValue = 620), require = 0, remap = false)
    private int tensuraTno$fibers(int original) { return tensuraTno$count(original); }
    @ModifyConstant(method = "renderHairlineCracks", constant = @Constant(intValue = 880), require = 0, remap = false)
    private int tensuraTno$hairlines(int original) { return tensuraTno$count(original); }
    @ModifyConstant(method = "renderSpecularSheen", constant = @Constant(intValue = 260), require = 0, remap = false)
    private int tensuraTno$sheen(int original) { return tensuraTno$count(original); }
    @ModifyConstant(method = "renderStarGlints", constant = @Constant(intValue = 260), require = 0, remap = false)
    private int tensuraTno$stars(int original) { return tensuraTno$count(original); }
    @ModifyConstant(method = "renderDistributedGlitter", constant = @Constant(intValue = 1600), require = 0, remap = false)
    private int tensuraTno$glitter(int original) { return tensuraTno$count(original); }
    @ModifyConstant(method = "renderEdgeIceSpikes", constant = @Constant(intValue = 210), require = 0, remap = false)
    private int tensuraTno$edgeSpikes(int original) { return tensuraTno$count(original); }
    @ModifyConstant(method = "renderMicroSpikeField", constant = @Constant(intValue = 330), require = 0, remap = false)
    private int tensuraTno$microSpikes(int original) { return tensuraTno$count(original); }
    @ModifyConstant(method = "renderSpikeOnlyNeedles", constant = @Constant(intValue = 105), require = 0, remap = false)
    private int tensuraTno$needles(int original) { return tensuraTno$count(original); }
    @ModifyConstant(method = "renderShatterBurst", constant = @Constant(intValue = 110), require = 0, remap = false)
    private int tensuraTno$shatter(int original) { return tensuraTno$count(original); }

    private int tensuraTno$count(int original) {
        return MoreSkillsAlbisVinaVfxOptimizer.count(original, this.origin);
    }
}

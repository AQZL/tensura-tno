package com.tensura_tno.mixin.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.tensura_tno.client.compat.MoreSkillsElfariaIceMeshRenderer;
import com.tensura_tno.client.compat.MoreSkillsElfariaVfxOptimizer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the two allocation-heavy ice meshes and scales secondary detail by distance. */
@Pseudo
@Mixin(
        targets = "com.github.wal_bos.moreskills.client.fx.ElfariaAlbisIceSheetRenderer",
        remap = false
)
public abstract class MoreSkillsElfariaIceSheetOptimizationMixin {

    @Inject(
            method = "albisDisc(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;"
                    + "Lnet/minecraft/world/phys/Vec3;DDJD)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void tensuraTno$drawOptimizedDisc(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 center,
            double radius,
            double live,
            long seed,
            double yLift,
            CallbackInfo ci
    ) {
        if (MoreSkillsElfariaIceMeshRenderer.renderDisc(
                buffer,
                matrix,
                center,
                radius,
                live,
                seed,
                yLift
        )) {
            ci.cancel();
        }
    }

    @Inject(
            method = "albisRectSheet(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;DDDJD)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void tensuraTno$drawOptimizedRectSheet(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 center,
            Vec3 axis,
            Vec3 side,
            double halfLength,
            double halfWidth,
            double live,
            long seed,
            double yLift,
            CallbackInfo ci
    ) {
        if (MoreSkillsElfariaIceMeshRenderer.renderRectSheet(
                buffer,
                matrix,
                center,
                axis,
                side,
                halfLength,
                halfWidth,
                live,
                seed,
                yLift
        )) {
            ci.cancel();
        }
    }

    @Inject(
            method = "albisDiscLines(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;"
                    + "Lnet/minecraft/world/phys/Vec3;DDJD)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void tensuraTno$cullDiscLines(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 center,
            double radius,
            double live,
            long seed,
            double yLift,
            CallbackInfo ci
    ) {
        if (MoreSkillsElfariaVfxOptimizer.shouldOptimizeIceMesh()
                && (live <= 0.002D
                || !MoreSkillsElfariaVfxOptimizer.isGeometryVisible(center, Math.abs(radius) + 0.25D))) {
            ci.cancel();
        }
    }

    @Inject(
            method = "albisSheen(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;DDDJD)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void tensuraTno$cullSheen(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 center,
            Vec3 axis,
            Vec3 side,
            double halfLength,
            double halfWidth,
            double live,
            long seed,
            double yLift,
            CallbackInfo ci
    ) {
        double extent = Math.abs(halfLength) * Math.sqrt(axis.lengthSqr())
                + Math.abs(halfWidth) * Math.sqrt(side.lengthSqr())
                + 0.25D;
        if (MoreSkillsElfariaVfxOptimizer.shouldOptimizeIceMesh()
                && (live <= 0.002D
                || !MoreSkillsElfariaVfxOptimizer.isGeometryVisible(center, extent))) {
            ci.cancel();
        }
    }

    @ModifyConstant(
            method = "albisDiscLines(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;"
                    + "Lnet/minecraft/world/phys/Vec3;DDJD)V",
            constant = @Constant(intValue = 280),
            require = 0,
            remap = false
    )
    private static int tensuraTno$reduceDiscLineCount(int original) {
        return MoreSkillsElfariaVfxOptimizer.discLineCount(original);
    }

    @ModifyConstant(
            method = "albisSheen(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;DDDJD)V",
            constant = @Constant(intValue = 96),
            require = 0,
            remap = false
    )
    private static int tensuraTno$reduceSheenCount(int original) {
        return MoreSkillsElfariaVfxOptimizer.sheenCount(original);
    }
}

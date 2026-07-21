package com.tensura_tno.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Reduces closed-mesh tessellation without changing radii, angles, timing, or post-processing. */
@Pseudo
@Mixin(targets = "com.github.wal_bos.moreskills.client.fx.AsmodayBlackHoleClient", remap = false)
public abstract class MoreSkillsAsmodayBlackHoleOptimizationMixin {
    @ModifyConstant(method = "renderBlackSphere", constant = @Constant(intValue = 28), require = 0, remap = false)
    private static int tensuraTno$sphereStacks(int value) { return 18; }
    @ModifyConstant(method = "renderBlackSphere", constant = @Constant(intValue = 56), require = 0, remap = false)
    private static int tensuraTno$sphereSlices(int value) { return 36; }

    @ModifyConstant(method = "renderAccretionVolume", constant = @Constant(intValue = 38), require = 0, remap = false)
    private static int tensuraTno$frontBaseRings(int value) { return 22; }
    @ModifyConstant(method = "renderAccretionVolume", constant = @Constant(intValue = 15), require = 0, remap = false)
    private static int tensuraTno$backBaseRings(int value) { return 9; }
    @ModifyConstant(method = "renderAccretionVolume", constant = @Constant(intValue = 22), require = 0, remap = false)
    private static int tensuraTno$frontHotRings(int value) { return 13; }
    @ModifyConstant(method = "renderAccretionVolume", constant = @Constant(intValue = 8), require = 0, remap = false)
    private static int tensuraTno$backHotRings(int value) { return 5; }
    @ModifyConstant(method = "renderAccretionVolume", constant = @Constant(intValue = 14), require = 0, remap = false)
    private static int tensuraTno$frontPlasmaRings(int value) { return 8; }

    @ModifyConstant(method = "addRibbonLayer", constant = @Constant(intValue = 5), require = 0, remap = false)
    private static int tensuraTno$frontRadialCuts(int value) { return 3; }
    @ModifyConstant(method = "addRibbonLayer", constant = @Constant(intValue = 4), require = 0, remap = false)
    private static int tensuraTno$backRadialCuts(int value) { return 2; }

    @ModifyConstant(method = "renderCondensedYDisk", constant = @Constant(intValue = 30), require = 0, remap = false)
    private static int tensuraTno$diskRings(int value) { return 18; }
    @ModifyConstant(method = "renderCondensedYDisk", constant = @Constant(intValue = 14), require = 0, remap = false)
    private static int tensuraTno$diskBackRings(int value) { return 8; }
    @ModifyConstant(method = "renderCondensedYDisk", constant = @Constant(intValue = 16), require = 0, remap = false)
    private static int tensuraTno$diskHotRings(int value) { return 10; }

    @ModifyConstant(method = "addCondensedYDiskTube", constant = @Constant(intValue = 128), require = 0, remap = false)
    private static int tensuraTno$diskFrontSegments(int value) { return 72; }
    @ModifyConstant(method = "addCondensedYDiskTube", constant = @Constant(intValue = 72), require = 0, remap = false)
    private static int tensuraTno$diskBackSegments(int value) { return 44; }
    @ModifyConstant(method = "addCondensedYDiskTube", constant = @Constant(intValue = 7), require = 0, remap = false)
    private static int tensuraTno$diskFrontBands(int value) { return 4; }

    @ModifyConstant(method = "renderLensedWall", constant = @Constant(intValue = 10), require = 0, remap = false)
    private static int tensuraTno$lensedBands(int value) { return 6; }
    @ModifyConstant(method = "addVerticalBand", constant = @Constant(intValue = 58), require = 0, remap = false)
    private static int tensuraTno$lensedSegments(int value) { return 36; }
    @ModifyConstant(method = "renderInnerFilaments", constant = @Constant(intValue = 26), require = 0, remap = false)
    private static int tensuraTno$filaments(int value) { return 14; }
}

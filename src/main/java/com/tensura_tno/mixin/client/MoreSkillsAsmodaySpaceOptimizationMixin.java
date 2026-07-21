package com.tensura_tno.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Keeps every full-screen layer but lowers the density of its independently placed decorations. */
@Pseudo
@Mixin(targets = "com.github.wal_bos.moreskills.client.fx.AsmodaySpaceClient", remap = false)
public abstract class MoreSkillsAsmodaySpaceOptimizationMixin {
    @ModifyConstant(method = "renderNebula", constant = @Constant(intValue = 36), require = 0, remap = false)
    private static int tensuraTno$nebula(int value) { return 24; }
    @ModifyConstant(method = "renderPhainonDeepNebula", constant = @Constant(intValue = 58), require = 0, remap = false)
    private static int tensuraTno$deepNebula(int value) { return 36; }
    @ModifyConstant(method = "renderVoidDust", constant = @Constant(intValue = 80), require = 0, remap = false)
    private static int tensuraTno$stars(int value) { return 48; }
    @ModifyConstant(method = "renderPhainonVoidDust", constant = @Constant(intValue = 180), require = 0, remap = false)
    private static int tensuraTno$phainonDust(int value) { return 90; }
    @ModifyConstant(method = "renderPhainonGoldDust", constant = @Constant(intValue = 120), require = 0, remap = false)
    private static int tensuraTno$embers(int value) { return 64; }
    @ModifyConstant(method = "renderPhainonRedAbyssDust", constant = @Constant(intValue = 140), require = 0, remap = false)
    private static int tensuraTno$phainonStars(int value) { return 72; }
}

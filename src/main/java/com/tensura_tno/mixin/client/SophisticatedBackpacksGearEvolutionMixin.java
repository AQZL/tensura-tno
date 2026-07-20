package com.tensura_tno.mixin.client;

import com.tensura_tno.TensuraTNOCompatConfig;
import com.tensura_tno.compat.SophisticatedBackpacksCompat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.manasmods.tensura.handler.GearHandler", remap = false)
public class SophisticatedBackpacksGearEvolutionMixin {

    @Inject(method = "initiateGearEvolution", at = @At("TAIL"), remap = false, require = 0)
    private static void tensura_tno$refreshSophisticatedBackpacksSlots(Level level, ItemStack stack, CallbackInfo ci) {
        if (!TensuraTNOCompatConfig.isSophisticatedBackpacksEvolutionSlotRefreshEnabled()) {
            return;
        }

        SophisticatedBackpacksCompat.refreshEvolvedBackpackSlots(stack);
    }
}
package com.tensura_tno.mixin.client;

import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes Arclight crashing block_factorys_bosses:cage during ticking.
 *
 * <p>Arclight injects Bukkit Hanging break handling into BlockAttachedEntity.tick()
 * and casts the Bukkit wrapper to org.bukkit.entity.Hanging. Modded CageEntity
 * extends BlockAttachedEntity, but its Bukkit wrapper is ArclightModEntity, so
 * the injected cast throws ClassCastException and crashes the server tick.</p>
 *
 * <p>We only suppress CageEntity's super.tick() call. Other vanilla hanging
 * entities keep Arclight's normal behavior.</p>
 */
@Pseudo
@Mixin(targets = "net.unusual.block_factorys_bosses.entity.decoration.CageEntity", remap = false)
public class BossesCageArclightTickFixMixin {

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/decoration/BlockAttachedEntity;tick()V"
        ),
        remap = true,
        require = 0
    )
    private void tensuraTno$skipArclightHangingTick(BlockAttachedEntity entity) {
        // Intentionally no-op.
        //
        // Do NOT call entity.tick() here: this redirect replaces the super.tick()
        // invocation inside CageEntity.tick(), and entity.tick() would dispatch
        // virtually back to CageEntity.tick(), causing infinite recursion.
    }
}

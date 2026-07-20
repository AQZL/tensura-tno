package com.tensura_tno.mixin.client;

import com.tensura_tno.compat.CuriosEPCompat;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends Tensura's gear EP restoration to Curios (accessory) slots.
 *
 * ExistenceStorage.handleGearEnergyRegen, which fills EP_DURABILITY from areaMagicule and uses
 * EP_DURABILITY to repair item durability, only iterates EquipmentSlot.values() (vanilla slots).
 * This Mixin injects at the TAIL of ExistenceStorage.init() to register an additional
 * LIVING_POST_TICK listener that applies the same logic to all Curios-equipped items.
 */
@Pseudo
@Mixin(targets = "io.github.manasmods.tensura.storage.ep.ExistenceStorage", remap = false)
public class CuriosGearRegenMixin {

    @Inject(
        method = "init",
        at = @At("TAIL"),
        remap = false,
        require = 0
    )
    private static void tensuraTno$registerCuriosGearRegen(CallbackInfo ci) {
        EntityEvents.LIVING_POST_TICK.register((entity) -> {
            Level level = entity.level();
            if (level.isClientSide()) return;
            MinecraftServer server = level.getServer();
            if (server == null) return;
            CuriosEPCompat.handleCuriosGearRegen(entity, server);
        });
    }
}

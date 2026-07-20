package com.tensura_tno.mixin.client;

import com.tensura_tno.compat.CuriosEPCompat;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.world.TensuraGameRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends Tensura's gear EP gain to also cover items equipped in Curios (accessory) slots.
 *
 * DeathHandler.init() registers a DEATH_EVENT_NORMAL handler that grants EP to items in
 * vanilla EquipmentSlots only. This Mixin injects at the TAIL of init() to register an
 * ADDITIONAL handler that performs the same EP grant for Curios-equipped items.
 *
 * Running as a separate event registration ensures:
 * - Correct ordering (fires after the original vanilla-slot handler)
 * - No interference with existing logic
 * - No duplication of player EP, only item EP in Curios slots
 */
@Pseudo
@Mixin(targets = "io.github.manasmods.tensura.handler.DeathHandler", remap = false)
public class CuriosGearEPMixin {

    @Inject(
        method = "init",
        at = @At("TAIL"),
        remap = false,
        require = 0
    )
    private static void tensuraTno$registerCuriosEPGain(CallbackInfo ci) {
        EntityEvents.DEATH_EVENT_NORMAL.register((entity, source) -> {
            if (entity.isAlive()) return EventResult.pass();
            if (entity.getType().is(TensuraEntityTags.EP_DROP_EXCLUDED)) return EventResult.pass();

            Entity pAttacker = source.getEntity();
            if (pAttacker == entity) return EventResult.pass();

            // Resolve CloneEntity → owner without compile-time dependency on SmartBrainLib
            try {
                Class<?> cloneClass = Class.forName("io.github.manasmods.tensura.entity.human.CloneEntity");
                if (cloneClass.isInstance(pAttacker)) {
                    Object owner = cloneClass.getMethod("getOwner").invoke(pAttacker);
                    if (owner instanceof Entity ownerEntity) {
                        pAttacker = ownerEntity;
                    }
                }
            } catch (Exception | NoClassDefFoundError ignored) {
                // SmartBrainLib not loaded or CloneEntity unavailable
            }

            LivingEntity attacker;
            if (pAttacker instanceof LivingEntity living) {
                attacker = living;
            } else {
                attacker = entity.getLastAttacker();
            }
            if (attacker == null) return EventResult.pass();

            double EP = EnergyHelper.getEPGain(entity, attacker, false);
            if (EP <= 0.0D) return EventResult.pass();
            if (TensuraStorages.getExistenceFrom(entity).isSkippingEPDrop()) return EventResult.pass();

            float multiplier = (float) entity.level().getGameRules().getInt(TensuraGameRules.EP_GAIN_MULTIPLIER);
            double totalEP = EP * multiplier;

            CuriosEPCompat.applyEPToCuriosSlots(attacker, totalEP);

            return EventResult.pass();
        });
    }
}

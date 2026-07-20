package com.tensura_tno.compat;

import com.tensura_tno.TensuraTNOMod;
import io.github.manasmods.tensura.enchantment.EngravingHelper;
import io.github.manasmods.tensura.enchantment.TensuraEnchantmentHelper;
import io.github.manasmods.tensura.enchantment.TensuraEnchantments;
import io.github.manasmods.tensura.handler.GearHandler;
import io.github.manasmods.tensura.registry.item.misc.TensuraDataComponents;
import io.github.manasmods.tensura.storage.AreaMagiculeHelper;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Applies Tensura gear EP gain to items equipped in Curios slots.
 *
 * Curios API is NOT a compile-time dependency, so all Curios calls are made
 * via reflection. The returned handler IS cast to IItemHandlerModifiable
 * (a NeoForge interface that IS on the classpath) since ICuriosItemHandler
 * extends it and the runtime object implements it.
 */
public class CuriosEPCompat {

    private static volatile boolean curiosPresent = true; // assume present until ClassNotFoundException

    /**
     * Iterates all equipped Curios and applies the same EP gain logic as
     * DeathHandler.gearGetEP(LivingEntity, EquipmentSlot, double).
     */
    public static void applyEPToCuriosSlots(LivingEntity attacker, double totalEP) {
        if (!curiosPresent) return;
        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getCuriosInventory = curiosApiClass.getMethod("getCuriosInventory", LivingEntity.class);
            @SuppressWarnings("unchecked")
            Optional<Object> optHandler = (Optional<Object>) getCuriosInventory.invoke(null, attacker);
            if (optHandler.isEmpty()) return;

            Object handler = optHandler.get();
            Method getEquippedCuriosMethod = handler.getClass().getMethod("getEquippedCurios");
            IItemHandlerModifiable equipped = (IItemHandlerModifiable) getEquippedCuriosMethod.invoke(handler);

            for (int i = 0; i < equipped.getSlots(); i++) {
                grantEPToCurioStack(attacker, equipped, i, totalEP);
            }
        } catch (ClassNotFoundException e) {
            curiosPresent = false;
        } catch (Exception e) {
            TensuraTNOMod.LOGGER.error("[TensuraTNO] CuriosEPCompat error", e);
        }
    }

    /**
     * Mirrors DeathHandler.gearGetEP logic for a single Curios stack.
     * On evolution, writes the new stack back into the handler via setStackInSlot.
     */
    @SuppressWarnings("unchecked")
    private static void grantEPToCurioStack(LivingEntity entity, IItemHandlerModifiable handler, int slot, double totalEP) {
        ItemStack stack = handler.getStackInSlot(slot);
        if (!stack.has((DataComponentType) TensuraDataComponents.EP.get())) return;

        double maxEP = (Double) stack.get((DataComponentType) TensuraDataComponents.MAX_EP.get());
        double currentEP = (Double) stack.get((DataComponentType) TensuraDataComponents.EP.get());
        double epGain = calcGearEPGain(entity, stack, totalEP);
        double newEP = currentEP + epGain;

        if (newEP < maxEP) {
            stack.set((DataComponentType) TensuraDataComponents.EP.get(), newEP);
            EngravingHelper.grantRandomEngraving(entity, stack, newEP);
            GearHandler.applyUniqueGearEvolution(stack, newEP);
        } else {
            ResourceLocation evolutionLocation = (ResourceLocation) stack.get((DataComponentType) TensuraDataComponents.EVOLUTION.get());
            if (evolutionLocation != null
                    && TensuraEnchantmentHelper.getEnchantmentLevel(entity.level(), TensuraEnchantments.STAGNATION, stack) <= 0) {
                newEP = Math.min(newEP, maxEP);
                stack.set((DataComponentType) TensuraDataComponents.EP.get(), newEP);
                EngravingHelper.grantRandomEngraving(entity, stack, newEP);
                Holder<Item> evolutionHolder = BuiltInRegistries.ITEM
                        .getHolder(ResourceKey.create(Registries.ITEM, evolutionLocation))
                        .orElse(null);
                if (evolutionHolder == null) return;
                ItemStack evolution = new ItemStack(evolutionHolder, stack.getCount(), stack.getComponentsPatch());
                GearHandler.initiateGearEvolution(entity.level(), evolution);
                handler.setStackInSlot(slot, evolution);
            } else {
                if (currentEP != maxEP) {
                    newEP = Math.min(newEP, maxEP);
                    stack.set((DataComponentType) TensuraDataComponents.EP.get(), newEP);
                    EngravingHelper.grantRandomEngraving(entity, stack, newEP);
                    GearHandler.applyUniqueGearEvolution(stack, newEP);
                }
            }
        }
    }

    /** Mirrors DeathHandler.getGearEPGain. */
    private static double calcGearEPGain(LivingEntity entity, ItemStack stack, double totalEP) {
        float lethargy = (float) TensuraEnchantmentHelper.getEnchantmentLevel(entity.level(), TensuraEnchantments.LETHARGY, stack) * 0.2F;
        float vigor    = (float) TensuraEnchantmentHelper.getEnchantmentLevel(entity.level(), TensuraEnchantments.VIGOR, stack) * 0.2F;
        float growth   = (float) TensuraEnchantmentHelper.getEnchantmentLevel(entity.level(), TensuraEnchantments.GROWTH, stack);
        double boost = (double) (1.0F + vigor + growth - lethargy);
        return (double) Math.round(totalEP * (Double) stack.get((DataComponentType) TensuraDataComponents.EP_GAIN.get()) * boost);
    }

    /**
     * Mirrors ExistenceStorage.handleGearEnergyRegen for Curios slots.
     * Called every server tick; internally throttles to every 10 ticks to match the original.
     */
    @SuppressWarnings("unchecked")
    public static void handleCuriosGearRegen(LivingEntity entity, MinecraftServer server) {
        if (!curiosPresent) return;
        if (server.getTickCount() % 10 != 0) return;

        double areaMagicule = AreaMagiculeHelper.getMagicule(entity, true) * EnergyHelper.CONFIG.areaMagiculeRegen;

        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getCuriosInventory = curiosApiClass.getMethod("getCuriosInventory", LivingEntity.class);
            Optional<Object> optHandler = (Optional<Object>) getCuriosInventory.invoke(null, entity);
            if (optHandler.isEmpty()) return;

            Object handler = optHandler.get();
            Method getEquippedCuriosMethod = handler.getClass().getMethod("getEquippedCurios");
            IItemHandlerModifiable equipped = (IItemHandlerModifiable) getEquippedCuriosMethod.invoke(handler);

            for (int i = 0; i < equipped.getSlots(); i++) {
                ItemStack stack = equipped.getStackInSlot(i);
                if (!stack.has((DataComponentType) TensuraDataComponents.EP.get())) continue;
                if (TensuraEnchantmentHelper.getEnchantmentLevel(entity.level(), TensuraEnchantments.RUINATION, stack) > 0) continue;

                int multiplier = 10;
                int damage = stack.getDamageValue();
                double durabilityEP = (Double) stack.get((DataComponentType) TensuraDataComponents.EP_DURABILITY.get());
                double restoration = (double) TensuraEnchantmentHelper.getEnchantmentLevel(entity.level(), TensuraEnchantments.RESTORATION, stack);
                int regen = (int) Math.min(durabilityEP, (double) (damage * multiplier));

                if (damage > 0 && regen / multiplier > 0) {
                    stack.setDamageValue(damage - (int) ((double) (regen / multiplier) * (1.0D + restoration)));
                    stack.set((DataComponentType) TensuraDataComponents.EP_DURABILITY.get(), durabilityEP - regen);
                } else {
                    double EP = (Double) stack.get((DataComponentType) TensuraDataComponents.EP.get());
                    if (durabilityEP < EP) {
                        stack.set((DataComponentType) TensuraDataComponents.EP_DURABILITY.get(), Math.min(EP, durabilityEP + areaMagicule));
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            curiosPresent = false;
        } catch (Exception e) {
            TensuraTNOMod.LOGGER.error("[TensuraTNO] CuriosEPCompat regen error", e);
        }
    }
}

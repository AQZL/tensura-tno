package com.tensura_tno.race;

import io.github.manasmods.manascore.race.api.RaceAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

public final class SlimeRaceHelper {

    public static final Set<ResourceLocation> SLIME_RACE_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath("tensura", "slime"),
            ResourceLocation.fromNamespaceAndPath("tensura", "metal_slime"),
            ResourceLocation.fromNamespaceAndPath("tensura", "demon_slime"),
            ResourceLocation.fromNamespaceAndPath("tensura", "god_slime")
    );

    private SlimeRaceHelper() {}

    public static boolean isSlimeRace(LivingEntity entity) {
        if (entity == null) return false;
        try {
            return RaceAPI.getRaceFrom(entity).getRace()
                    .map(inst -> {
                        ResourceLocation id = inst.getRace().getRegistryName();
                        return id != null && SLIME_RACE_IDS.contains(id);
                    })
                    .orElse(false);
        } catch (Throwable t) {
            return false;
        }
    }
}

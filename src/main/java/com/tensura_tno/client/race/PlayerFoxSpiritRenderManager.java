package com.tensura_tno.client.race;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensura_tno.race.fox_spirit.FoxSpiritPlayerFormHelper;
import io.github.manasmods.manascore.race.api.RaceAPI;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Client-side renderer state for all five fox-spirit race stages. */
public final class PlayerFoxSpiritRenderManager {

    public static final Set<ResourceLocation> FOX_SPIRIT_RACE_IDS = Set.of(
            id("baby_spirit_fox"),
            id("fox_spirit_envoy"),
            id("spirit_fox_contract_master"),
            id("mystic_fox_master"),
            id("heavenly_fox_sovereign")
    );

    private static final PlayerFoxSpiritRenderer RENDERER = new PlayerFoxSpiritRenderer();
    private static final ConcurrentMap<UUID, PlayerFoxSpiritAnimatable> ANIMATABLES =
            new ConcurrentHashMap<>();

    private PlayerFoxSpiritRenderManager() {
    }

    public static boolean shouldRenderAsFoxSpirit(Player player) {
        return FoxSpiritPlayerFormHelper.shouldUseFoxForm(player);
    }

    public static boolean isFoxSpiritRace(LivingEntity entity) {
        if (entity == null) return false;
        try {
            return RaceAPI.getRaceFrom(entity).getRace()
                    .map(instance -> instance.getRace().getRegistryName())
                    .filter(java.util.Objects::nonNull)
                    .map(FOX_SPIRIT_RACE_IDS::contains)
                    .orElse(false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static PlayerFoxSpiritAnimatable getOrCreate(Player player) {
        return ANIMATABLES.compute(player.getUUID(), (uuid, existing) -> {
            if (existing == null) return new PlayerFoxSpiritAnimatable(player);
            existing.setPlayer(player);
            return existing;
        });
    }

    public static void forget(UUID uuid) {
        ANIMATABLES.remove(uuid);
    }

    public static void render(Player player, PoseStack poseStack, MultiBufferSource bufferSource,
                              int packedLight, float partialTick) {
        RENDERER.render(poseStack, getOrCreate(player), bufferSource,
                null, null, packedLight, partialTick);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tensura_tno", path);
    }
}

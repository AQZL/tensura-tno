package com.tensura_tno.world.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Narrow read-only view over biome data, used by {@code SafeSpawnFinder} and
 * {@code SpawnRelocator} to decouple the spawn-relocation logic from any
 * concrete {@code ServerLevel} implementation.
 *
 * <p>Production code bridges this interface to the real {@code ServerLevel}
 * via a lambda; tests can supply a stub backed by a simple coordinate ->
 * biome-id function. This keeps the unit-test JVM free of Vanilla world
 * instantiation while the runtime path stays purely on Mojang / NeoForge
 * public API.
 *
 * <p>Implementations must depend only on Mojang and NeoForge public API and
 * MUST NOT reference any class under {@code com.github.manasmods.tensura.*}.
 *
 * @see SurfaceLookup
 */
@FunctionalInterface
public interface BiomeLookup {

    /**
     * Returns the biome {@link ResourceLocation} at the given block position.
     *
     * @param pos a non-null block position; only the biome at this position
     *            is consulted (callers typically pass the candidate surface
     *            position rather than feet/head positions)
     * @return the biome's {@link ResourceLocation} (e.g.
     *         {@code minecraft:plains}); never {@code null}
     */
    ResourceLocation biomeAt(BlockPos pos);
}

package com.tensura_tno.world.spawn;

import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Test-friendly orchestration port for the world-spawn relocation pass.
 *
 * <p>Production code adapts a real {@link ServerLevel} (plus the loaded
 * {@link SpawnRelocatedFlag} and the cached configuration values) to this
 * interface in
 * {@link SpawnRelocator#relocateWorldSpawnIfNeeded(ServerLevel)}; tests pass
 * a stub-backed implementation through
 * {@link SpawnRelocator#relocateWorldSpawnCore(WorldSpawnContext)} so that
 * every guard listed in design.md's Property 4 can be exercised without
 * instantiating a Vanilla {@code ServerLevel}.
 *
 * <p>The interface intentionally exposes <em>every</em> guard input as a
 * separate accessor (feature toggle, Tensura mod presence, dimension check,
 * persistent flag, current shared spawn pos, biome resolution, finder, and
 * the search radius / dimension id used in log lines) so that the property
 * test can flip exactly one guard at a time and observe whether the
 * relocation pass remains a no-op.
 *
 * <p>This interface depends only on Mojang public API and MUST NOT
 * reference any class under {@code com.github.manasmods.tensura.*}, in
 * keeping with Requirement 5.3.
 *
 * @see SpawnRelocator#relocateWorldSpawnCore(WorldSpawnContext)
 * @see PlayerSpawnContext
 */
interface WorldSpawnContext {

    /**
     * @return whether the {@code spawnBiomeBlacklistEnabled} feature toggle
     *         is currently {@code true}; mirrors
     *         {@link BiomeBlacklistConfig#isEnabled()} (Requirement 4.3).
     */
    boolean isEnabled();

    /**
     * @return whether the Tensura core mod is currently loaded; mirrors
     *         {@code ModList.get().isLoaded("tensura")} (Requirements 5.1,
     *         5.2).
     */
    boolean isTensuraLoaded();

    /**
     * @return whether the underlying level's dimension key equals
     *         {@code minecraft:overworld} (Requirement 7.1).
     */
    boolean isOverworld();

    /**
     * @return the underlying level's dimension {@link ResourceLocation},
     *         used in the "non-overworld dimension" {@code DEBUG} log line.
     *         May be {@code null} for synthetic test contexts.
     */
    ResourceLocation dimensionId();

    /**
     * @return the level's current shared spawn position; mirrors
     *         {@code ServerLevel#getSharedSpawnPos()}.
     */
    BlockPos getSharedSpawnPos();

    /**
     * Records the relocated shared spawn position back into the level;
     * mirrors {@code ServerLevel#setDefaultSpawnPos(BlockPos, float)}.
     */
    void setDefaultSpawnPos(BlockPos pos, float angle);

    /**
     * @param pos a non-{@code null} block position; typically either the
     *            current shared spawn or the candidate hit returned by
     *            {@link #findSafeSpawn(BlockPos)}
     * @return the biome {@link ResourceLocation} at {@code pos}; may be
     *         {@code null} for synthetic positions outside the test world
     */
    ResourceLocation biomeIdAt(BlockPos pos);

    /**
     * @return the live biome blacklist set; mirrors
     *         {@link BiomeBlacklistConfig#getBlacklist()}. Never
     *         {@code null}.
     */
    Set<ResourceLocation> blacklist();

    /**
     * @return whether the per-save {@link SpawnRelocatedFlag} reports that
     *         relocation has already been considered (Requirement 1.4).
     */
    boolean isFlagDone();

    /**
     * Marks the per-save {@link SpawnRelocatedFlag} as done; mirrors
     * {@link SpawnRelocatedFlag#markDone()}. Called from <em>every</em>
     * exit path of {@link SpawnRelocator#relocateWorldSpawnCore(WorldSpawnContext)}
     * past the dimension / flag-already-done short circuits, so the next
     * server start never re-runs the spiral search.
     */
    void markFlagDone();

    /**
     * Locates a candidate safe spawn within the configured search radius;
     * mirrors {@link SafeSpawnFinder#findSafeSpawn(ServerLevel, BlockPos,
     * int, int, Set)}. Implementations may also throw a
     * {@link RuntimeException} to model the "finder threw" failure path
     * required by Requirement 6.5.
     *
     * @param origin the origin {@link BlockPos} (the current shared spawn);
     *               never {@code null}
     * @return an {@link Optional} containing the first safe candidate, or
     *         empty if none was found within the search grid
     */
    Optional<BlockPos> findSafeSpawn(BlockPos origin);

    /**
     * @return the configured horizontal search radius, used only to format
     *         the "no safe spawn found within radius=R" {@code WARN} log
     *         line (Requirement 6.4 / 8.1).
     */
    int searchRadius();
}

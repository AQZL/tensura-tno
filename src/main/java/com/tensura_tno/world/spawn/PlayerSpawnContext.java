package com.tensura_tno.world.spawn;

import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test-friendly orchestration port for the per-player first-login spawn
 * relocation pass.
 *
 * <p>Production code adapts a real {@link ServerPlayer} (plus its
 * {@link ServerLevel} and persistent NBT) to this interface in
 * {@link SpawnRelocator#relocatePlayerIfNeeded(ServerPlayer)}; tests pass a
 * stub-backed implementation through
 * {@link SpawnRelocator#relocatePlayerCore(PlayerSpawnContext)} so that
 * every guard listed in design.md's Property 4 can be exercised without
 * instantiating a Vanilla {@code ServerPlayer}.
 *
 * <p>The interface intentionally exposes <em>every</em> guard input as a
 * separate accessor (feature toggle, Tensura mod presence, dimension check,
 * respawn-position presence, persistent NBT flag, current player position,
 * biome resolution, finder, and the search radius / dimension id / player
 * name used in log lines) so that the property test can flip exactly one
 * guard at a time and observe whether the relocation pass remains a no-op.
 *
 * <p>This interface depends only on Mojang public API and MUST NOT
 * reference any class under {@code com.github.manasmods.tensura.*}, in
 * keeping with Requirement 5.3.
 *
 * @see SpawnRelocator#relocatePlayerCore(PlayerSpawnContext)
 * @see WorldSpawnContext
 */
interface PlayerSpawnContext {

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
     * @return whether the player's current dimension equals
     *         {@code minecraft:overworld} (Requirement 7.1).
     */
    boolean isOverworld();

    /**
     * @return the player's current dimension {@link ResourceLocation},
     *         used in the "non-overworld dimension" {@code DEBUG} log line.
     *         May be {@code null} for synthetic test contexts.
     */
    ResourceLocation dimensionId();

    /**
     * @return whether the per-player NBT flag
     *         {@code tensura_tno.spawn_relocated} is already {@code true}
     *         (Requirements 2.4, 3.3 — at-most-once per player).
     */
    boolean isPersistentFlagSet();

    /**
     * Sets the per-player NBT flag {@code tensura_tno.spawn_relocated} to
     * {@code true}; mirrors the production
     * {@code markPlayerRelocated(ServerPlayer)} helper. Called from
     * <em>every</em> exit path of
     * {@link SpawnRelocator#relocatePlayerCore(PlayerSpawnContext)} past the
     * NBT-already-set short circuit, so subsequent logins never re-run the
     * spiral search.
     */
    void setPersistentFlag();

    /**
     * @return whether the player currently holds a non-{@code null}
     *         respawn-bed / respawn-anchor position (Requirement 3.1 —
     *         protect bed / anchor binds).
     */
    boolean hasRespawnPosition();

    /**
     * @return the player's current block-aligned position; mirrors
     *         {@code Entity#blockPosition()} (i.e. the floored xyz).
     */
    BlockPos blockPosition();

    /**
     * Teleports the player to the supplied <em>continuous</em> world
     * coordinates; mirrors
     * {@code Entity#teleportTo(double, double, double)}.
     *
     * <p>The block-center {@code +0.5} offset on x / z (and the integer y
     * staying on the surface) is applied by the caller before invoking
     * this method, mirroring the production code path.
     */
    void teleportTo(double x, double y, double z);

    /**
     * @param pos a non-{@code null} block position; typically the player's
     *            current block position or the candidate hit returned by
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
     * Locates a candidate safe spawn within the configured search radius;
     * mirrors {@link SafeSpawnFinder#findSafeSpawn(ServerLevel, BlockPos,
     * int, int, Set)}. Implementations may also throw a
     * {@link RuntimeException} to model the "finder threw" failure path
     * required by Requirement 6.5.
     *
     * @param origin the origin {@link BlockPos} (the player's current
     *               position); never {@code null}
     * @return an {@link Optional} containing the first safe candidate, or
     *         empty if none was found within the search grid
     */
    Optional<BlockPos> findSafeSpawn(BlockPos origin);

    /**
     * @return the configured horizontal search radius, used only to format
     *         the "no safe spawn found within radius=R" {@code WARN} log
     *         line (Requirement 6.4 / 8.2).
     */
    int searchRadius();

    /**
     * @return the player's display name, used only in {@code INFO} /
     *         {@code WARN} / {@code DEBUG} log lines (Requirement 8.2).
     *         May be {@code null} for synthetic test contexts that do not
     *         populate a name.
     */
    String playerName();
}

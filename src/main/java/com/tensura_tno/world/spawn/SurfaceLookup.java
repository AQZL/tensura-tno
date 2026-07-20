package com.tensura_tno.world.spawn;

import net.minecraft.core.BlockPos;

/**
 * Narrow view over a level's surface and standability information, used by
 * {@code SafeSpawnFinder} to decouple the spawn-relocation logic from any
 * concrete {@code ServerLevel} implementation.
 *
 * <p>Production code bridges this interface to the real {@code ServerLevel}
 * via a lambda (e.g. {@code Heightmap.Types.MOTION_BLOCKING_NO_LEAVES} for
 * {@link #surfaceAt(int, int)}); tests can supply a stub backed by a simple
 * coordinate -> height / standable function. This keeps the unit-test JVM
 * free of Vanilla world instantiation while the runtime path stays purely on
 * Mojang / NeoForge public API.
 *
 * <p>Implementations must depend only on Mojang and NeoForge public API and
 * MUST NOT reference any class under {@code com.github.manasmods.tensura.*}.
 *
 * @see BiomeLookup
 */
public interface SurfaceLookup {

    /**
     * Returns the surface block position at the given (x, z) horizontal
     * coordinate.
     *
     * <p>Conceptually equivalent to taking the height from the
     * {@code MOTION_BLOCKING_NO_LEAVES} heightmap and constructing a
     * {@link BlockPos} at {@code (x, height, z)}. The returned position is
     * the candidate "feet" position; the block immediately below is the
     * standable surface, while the block at the returned position and the
     * one above must be air for the position to be safe.
     *
     * @param x world-space x coordinate
     * @param z world-space z coordinate
     * @return the surface {@link BlockPos}; never {@code null}
     */
    BlockPos surfaceAt(int x, int z);

    /**
     * Returns the inclusive minimum build height of the level (the y at
     * which the world starts; usually negative in 1.21).
     *
     * @return the minimum build height
     */
    int minBuildHeight();

    /**
     * Returns the exclusive maximum build height of the level (the first y
     * above the world's top).
     *
     * @return the maximum build height
     */
    int maxBuildHeight();

    /**
     * Returns whether the given block position can serve as a "feet"
     * position for a player to stand on.
     *
     * <p>A standable position requires:
     * <ul>
     *   <li>the block at {@code pos.below()} is solid (non-air, non-fluid,
     *       supports an entity standing on its top face), and</li>
     *   <li>the blocks at {@code pos} and {@code pos.above()} are passable
     *       (typically air), so a 2-block-tall player has clearance.</li>
     * </ul>
     *
     * @param pos the candidate "feet" block position
     * @return {@code true} if the position is standable per the criteria
     *         above; {@code false} otherwise
     */
    boolean isStandable(BlockPos pos);
}

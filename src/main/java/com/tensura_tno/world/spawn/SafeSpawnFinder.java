package com.tensura_tno.world.spawn;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * Pure-function utility class that locates a "safe" spawn position by
 * walking a square spiral around an origin and validating each candidate
 * against a biome blacklist plus standard standability rules.
 *
 * <p>This file currently exposes the package-private
 * {@link #spiralIterator(int, int, int, int)} grid generator, the
 * {@link #SEARCH_TIMEOUT_WARN_MS} constant, the candidate-validation
 * helpers {@link #surfacePos(ServerLevel, int, int)} and
 * {@link #isSafePosition(ServerLevel, BlockPos, Set)} (each provided in two
 * overloads — one accepting a real {@link ServerLevel}, one accepting the
 * narrow {@link BiomeLookup} / {@link SurfaceLookup} interfaces for tests),
 * plus the public entry point
 * {@link #findSafeSpawn(ServerLevel, BlockPos, int, int, Set)} (with a
 * narrow-interface overload for unit tests).
 *
 * <p>The class is intentionally {@code public final} with a private
 * constructor: it is a stateless helper, all members are static, and it
 * must remain extension-free so the spec's static-dependency property test
 * can validate that no Tensura internals leak into this code path.
 *
 * @see BiomeLookup
 * @see SurfaceLookup
 */
public final class SafeSpawnFinder {

    /**
     * Wall-clock threshold (in milliseconds) above which a single
     * {@code findSafeSpawn} invocation triggers a {@code WARN} log
     * suggesting the operator lower {@code spawnSafeSearchRadius} or raise
     * {@code spawnSafeSearchStep}. Consumed by task 4.4.
     */
    public static final long SEARCH_TIMEOUT_WARN_MS = 200L;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[TensuraTNO][SafeSpawnFinder]";

    private SafeSpawnFinder() {
        // utility class; no instances
    }

    /**
     * Locates a "safe" spawn position by walking a square spiral around
     * {@code origin} and returning the first candidate that satisfies
     * {@link #isSafePosition(ServerLevel, BlockPos, Set)}. This is the
     * production entry point bridging into the narrow-interface overload
     * {@link #findSafeSpawn(BiomeLookup, SurfaceLookup, BlockPos, int, int, Set)}
     * via lambdas built from {@code level} (so the actual loop logic lives
     * on the test-friendly path).
     *
     * <p>Behaviour (Requirements 6.1, 6.2, 6.3, 9.3):
     * <ul>
     *   <li>The first candidate inspected is {@code (origin.x, origin.z)}
     *       at the heightmap-resolved surface; iteration then expands
     *       outward in concentric square rings spaced by {@code step} up
     *       to a Chebyshev distance of {@code radius}.</li>
     *   <li>The first candidate satisfying
     *       {@link #isSafePosition(ServerLevel, BlockPos, Set)} is returned
     *       wrapped in {@link Optional}.</li>
     *   <li>If no candidate is safe within the search grid, returns
     *       {@link Optional#empty()}.</li>
     *   <li>If wall-clock duration exceeds
     *       {@link #SEARCH_TIMEOUT_WARN_MS} (Requirement 9.4), logs a
     *       {@code WARN} suggesting the operator lower
     *       {@code spawnSafeSearchRadius} or raise
     *       {@code spawnSafeSearchStep}.</li>
     * </ul>
     *
     * @param level     a non-null overworld {@link ServerLevel}
     * @param origin    starting point; only x/z are used (the y is
     *                  rederived from the heightmap)
     * @param radius    maximum Chebyshev search distance, in blocks
     * @param step      ring spacing, in blocks
     * @param blacklist forbidden biome IDs; never {@code null}
     * @return an {@link Optional} containing the first safe position, or
     *         empty if none is found within the search grid
     */
    public static Optional<BlockPos> findSafeSpawn(ServerLevel level, BlockPos origin, int radius, int step,
                                                   Set<ResourceLocation> blacklist) {
        return findSafeSpawn(toBiomeLookup(level), toSurfaceLookup(level), origin, radius, step, blacklist);
    }

    /**
     * 高速路径：优先使用 {@link ServerLevel#findClosestBiome3d} 通过生物群系数据源
     * 直接定位最近的非黑名单群系候选点。该 API 走的是 biome source（生成期纯函数），
     * <b>不会触发 chunk 同步加载 / 生成</b>，是 Tensura 主模组 {@code MixinMinecraftServer}
     * 自身使用的方案，比螺旋扫描快几个数量级且不会卡服。
     *
     * <p>找到候选点后再调一次 {@link ServerLevel#getHeight} 取地表 y（仅会触发 1 个
     * chunk 的加载），并验证可站立性。若该候选点站立不可用，回退到旧的螺旋扫描路径
     * 兜底；若回退也找不到，返回 {@link Optional#empty()}。
     *
     * @param level     生效世界
     * @param origin    搜索原点（玩家 / 世界出生点）
     * @param radius    搜索半径（块）
     * @param step      螺旋兜底路径的环间距
     * @param blacklist 不允许出生的群系 ID 集合；不可为 {@code null}
     */
    public static Optional<BlockPos> findSafeSpawnFast(ServerLevel level, BlockPos origin, int radius, int step,
                                                      Set<ResourceLocation> blacklist) {
        long t0 = System.currentTimeMillis();
        try {
            Pair<BlockPos, Holder<Biome>> hit = level.findClosestBiome3d(
                    holder -> holder.unwrapKey()
                            .map(ResourceKey::location)
                            .map(id -> !blacklist.contains(id))
                            .orElse(false),
                    origin,
                    Math.max(64, radius),
                    Math.max(16, step),
                    64);
            if (hit != null) {
                BlockPos biomePos = hit.getFirst();
                int x = biomePos.getX();
                int z = biomePos.getZ();
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos surface = new BlockPos(x, y, z);
                if (isSafePosition(level, surface, blacklist)) {
                    warnIfSlow(t0, radius, step);
                    return Optional.of(surface);
                }
                // biome3d 命中但表面不可站立 —— 走螺旋兜底
                Optional<BlockPos> fallback = findSafeSpawn(level, surface, radius, step, blacklist);
                if (fallback.isPresent()) {
                    return fallback;
                }
            }
        } catch (RuntimeException e) {
            LOGGER.warn("{} findClosestBiome3d threw, falling back to spiral search", LOG_PREFIX, e);
        }
        // biome3d 未命中或抛异常 —— 用旧螺旋路径兜底
        return findSafeSpawn(level, origin, radius, step, blacklist);
    }

    /**
     * Narrow-interface overload of
     * {@link #findSafeSpawn(ServerLevel, BlockPos, int, int, Set)} used as
     * a test entry point. Mirrors the design pseudocode in
     * {@code design.md} (SafeSpawnFinder section, "核心搜索循环"): walk
     * {@link #spiralIterator(int, int, int, int)}, resolve the surface via
     * {@link #surfacePos(SurfaceLookup, int, int)}, and return the first
     * candidate that
     * {@link #isSafePosition(BiomeLookup, SurfaceLookup, BlockPos, Set)}
     * accepts.
     *
     * <p>The slow-search {@code WARN} (Requirement 9.4) is emitted on
     * <em>both</em> exit paths — the early-return-on-hit path and the
     * exhausted-grid empty path — so callers always observe a single log
     * line per invocation when the threshold is exceeded.
     *
     * @param biomeLookup   biome adapter; never {@code null}
     * @param surfaceLookup surface / standability adapter; never
     *                      {@code null}
     * @param origin        starting point; only x/z are used
     * @param radius        maximum Chebyshev search distance, in blocks
     * @param step          ring spacing, in blocks
     * @param blacklist     forbidden biome IDs; never {@code null}
     * @return an {@link Optional} containing the first safe position, or
     *         empty if none is found within the search grid
     */
    static Optional<BlockPos> findSafeSpawn(BiomeLookup biomeLookup, SurfaceLookup surfaceLookup,
                                            BlockPos origin, int radius, int step,
                                            Set<ResourceLocation> blacklist) {
        long t0 = System.currentTimeMillis();
        Iterator<int[]> it = spiralIterator(origin.getX(), origin.getZ(), radius, step);
        while (it.hasNext()) {
            int[] xz = it.next();
            BlockPos surface = surfacePos(surfaceLookup, xz[0], xz[1]);
            if (isSafePosition(biomeLookup, surfaceLookup, surface, blacklist)) {
                warnIfSlow(t0, radius, step);
                return Optional.of(surface);
            }
        }
        warnIfSlow(t0, radius, step);
        return Optional.empty();
    }

    private static void warnIfSlow(long t0, int radius, int step) {
        long elapsed = System.currentTimeMillis() - t0;
        if (elapsed > SEARCH_TIMEOUT_WARN_MS) {
            LOGGER.warn("{} safe-spawn search took {}ms, consider lowering spawnSafeSearchRadius ({}) "
                + "or raising spawnSafeSearchStep ({})", LOG_PREFIX, elapsed, radius, step);
        }
    }

    /**
     * Returns an iterator that yields {@code (x, z)} coordinate pairs in a
     * square (Chebyshev) spiral around {@code (cx, cz)}.
     *
     * <p>The first point produced is always {@code (cx, cz)} (layer 0).
     * After that, the iterator walks layer {@code r = 1, 2, ...} along the
     * four edges of the {@code (2r+1) x (2r+1)} ring at Chebyshev distance
     * {@code r * step} from the origin, visiting each grid point on the
     * ring exactly once. Iteration stops as soon as {@code r * step}
     * exceeds {@code radius}.
     *
     * <p>The returned iterator is guaranteed to satisfy the following
     * invariants (validated by the property test in task 4.2):
     * <ol>
     *   <li><b>Uniqueness</b>: every produced {@code (x, z)} is distinct.</li>
     *   <li><b>Layer monotonicity</b>: the layer index
     *       {@code max(|x - cx|, |z - cz|) / step} is non-decreasing across
     *       the produced sequence.</li>
     *   <li><b>Upper bound</b>: the total number of produced points is at
     *       most {@code (2 * ceil(radius / step) + 1)^2}.</li>
     * </ol>
     *
     * <p>Defensive edge cases:
     * <ul>
     *   <li>{@code radius < 0} or {@code radius < step}: the iterator
     *       yields only the center point, since the first ring at
     *       {@code r = 1} already exceeds the radius.</li>
     *   <li>{@code step <= 0}: only the center point is yielded; degenerate
     *       step values cannot define a meaningful ring spacing.</li>
     * </ul>
     *
     * @param cx     center x-coordinate (also the x of the first emitted
     *               point)
     * @param cz     center z-coordinate (also the z of the first emitted
     *               point)
     * @param radius maximum Chebyshev distance (in blocks, in world space)
     *               from {@code (cx, cz)} to the outermost emitted ring;
     *               passing a non-positive value yields only the center
     * @param step   spacing (in blocks) between adjacent rings; must be
     *               {@code >= 1} for non-trivial output, otherwise only
     *               the center point is yielded
     * @return a fresh single-pass iterator over {@code int[2]} arrays
     *         shaped as {@code [x, z]}
     */
    static Iterator<int[]> spiralIterator(int cx, int cz, int radius, int step) {
        return new SquareSpiralIterator(cx, cz, radius, step);
    }

    /**
     * Returns the surface block position at {@code (x, z)} on the given
     * {@link ServerLevel} via the {@code MOTION_BLOCKING_NO_LEAVES}
     * heightmap. This is the production bridge that calls into the
     * narrow-interface overload {@link #surfacePos(SurfaceLookup, int, int)}
     * via a small lambda adapter, keeping the actual logic on the
     * test-friendly path.
     *
     * <p>The returned position is the candidate "feet" position; the block
     * immediately below is the standable surface (validated by
     * {@link #isSafePosition(ServerLevel, BlockPos, Set)}), while the block
     * at the returned position and the one above must be air for the
     * position to be safe.
     *
     * @param level a non-null overworld {@link ServerLevel}
     * @param x     world-space x coordinate
     * @param z     world-space z coordinate
     * @return the surface {@link BlockPos}; never {@code null}
     */
    static BlockPos surfacePos(ServerLevel level, int x, int z) {
        return surfacePos(toSurfaceLookup(level), x, z);
    }

    /**
     * Narrow-interface overload of {@link #surfacePos(ServerLevel, int, int)}
     * used as a test entry point. The actual logic — delegate to the
     * supplied {@link SurfaceLookup#surfaceAt(int, int)} — lives here so
     * unit tests can drive it without instantiating Vanilla classes.
     *
     * @param lookup a non-null {@link SurfaceLookup} adapter
     * @param x      world-space x coordinate
     * @param z      world-space z coordinate
     * @return the surface {@link BlockPos}; never {@code null}
     */
    static BlockPos surfacePos(SurfaceLookup lookup, int x, int z) {
        return lookup.surfaceAt(x, z);
    }

    /**
     * Returns whether the given surface position is safe to host a player
     * spawn on the supplied {@link ServerLevel}. This is the production
     * bridge that builds {@link BiomeLookup} / {@link SurfaceLookup}
     * adapters from {@code level} and delegates to the narrow-interface
     * overload {@link #isSafePosition(BiomeLookup, SurfaceLookup, BlockPos, Set)}.
     *
     * <p>A position is "safe" when all of the following hold (Requirement
     * 6.2):
     * <ul>
     *   <li>the biome resolved at {@code surface} is <b>not</b> contained
     *       in {@code blacklist};</li>
     *   <li>the block at {@code surface.below()} is non-air, non-fluid,
     *       and presents a sturdy upward face (so a player can stand on
     *       it);</li>
     *   <li>the blocks at {@code surface} and {@code surface.above()} are
     *       both air (so a 2-block-tall player has clearance);</li>
     *   <li>{@code surface.getY()} lies in
     *       {@code [minBuildHeight, maxBuildHeight)} so the candidate is
     *       inside the addressable column.</li>
     * </ul>
     *
     * @param level     a non-null overworld {@link ServerLevel}
     * @param surface   the candidate "feet" position (typically produced
     *                  by {@link #surfacePos(ServerLevel, int, int)})
     * @param blacklist the set of biome {@link ResourceLocation}s that are
     *                  disallowed; never {@code null}
     * @return {@code true} if all four conditions above hold; {@code false}
     *         otherwise
     */
    static boolean isSafePosition(ServerLevel level, BlockPos surface, Set<ResourceLocation> blacklist) {
        return isSafePosition(toBiomeLookup(level), toSurfaceLookup(level), surface, blacklist);
    }

    /**
     * Narrow-interface overload of
     * {@link #isSafePosition(ServerLevel, BlockPos, Set)} used as a test
     * entry point. The actual safety logic lives here so unit tests can
     * drive it through arbitrary {@link BiomeLookup} / {@link SurfaceLookup}
     * stubs.
     *
     * <p>Validation order matches the production overload's documentation:
     * <ol>
     *   <li>height-range guard (cheapest);</li>
     *   <li>blacklist membership;</li>
     *   <li>standability via {@link SurfaceLookup#isStandable(BlockPos)}.</li>
     * </ol>
     *
     * @param biomeLookup   biome adapter; never {@code null}
     * @param surfaceLookup surface / standability adapter; never
     *                      {@code null}
     * @param surface       candidate "feet" position; never {@code null}
     * @param blacklist     forbidden biome IDs; never {@code null}
     * @return whether the candidate position is safe per Requirement 6.2
     */
    static boolean isSafePosition(BiomeLookup biomeLookup, SurfaceLookup surfaceLookup,
                                  BlockPos surface, Set<ResourceLocation> blacklist) {
        int y = surface.getY();
        if (y < surfaceLookup.minBuildHeight() || y >= surfaceLookup.maxBuildHeight()) {
            return false;
        }
        ResourceLocation biome = biomeLookup.biomeAt(surface);
        if (biome == null || blacklist.contains(biome)) {
            return false;
        }
        return surfaceLookup.isStandable(surface);
    }

    /**
     * Builds a {@link BiomeLookup} adapter that resolves biomes from the
     * given {@link ServerLevel} via
     * {@code level.getBiome(pos).unwrapKey().get().location()}, mirroring
     * the {@code unwrapKey().map(...).orElse(null)} idiom already used
     * elsewhere in {@code tensura_tno}. Unregistered holders
     * gracefully resolve to {@code null}, which the
     * {@link #isSafePosition(BiomeLookup, SurfaceLookup, BlockPos, Set)}
     * narrow overload treats as "not safe" so callers never crash.
     */
    private static BiomeLookup toBiomeLookup(ServerLevel level) {
        return pos -> {
            Holder<Biome> holder = level.getBiome(pos);
            return holder.unwrapKey().map(key -> key.location()).orElse(null);
        };
    }

    /**
     * Builds a {@link SurfaceLookup} adapter over the given
     * {@link ServerLevel}:
     * <ul>
     *   <li>{@link SurfaceLookup#surfaceAt(int, int)} uses
     *       {@link Heightmap.Types#MOTION_BLOCKING_NO_LEAVES} (matching
     *       Vanilla's spawn-point heightmap so leaf canopies are skipped);
     *   </li>
     *   <li>{@link SurfaceLookup#minBuildHeight()} /
     *       {@link SurfaceLookup#maxBuildHeight()} forward to the level's
     *       column bounds;</li>
     *   <li>{@link SurfaceLookup#isStandable(BlockPos)} encodes the
     *       Requirement 6.2 standability check: the block immediately
     *       below the feet must be a non-empty, non-fluid, sturdy upward
     *       face, and the two blocks at and above the feet must be air so
     *       a 2-block-tall player has clearance.</li>
     * </ul>
     */
    private static SurfaceLookup toSurfaceLookup(ServerLevel level) {
        return new SurfaceLookup() {
            @Override
            public BlockPos surfaceAt(int x, int z) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                return new BlockPos(x, y, z);
            }

            @Override
            public int minBuildHeight() {
                return level.getMinBuildHeight();
            }

            @Override
            public int maxBuildHeight() {
                return level.getMaxBuildHeight();
            }

            @Override
            public boolean isStandable(BlockPos pos) {
                BlockPos below = pos.below();
                BlockState belowState = level.getBlockState(below);
                if (belowState.isAir()) {
                    return false;
                }
                FluidState belowFluid = belowState.getFluidState();
                if (!belowFluid.isEmpty()) {
                    return false;
                }
                if (!belowState.isFaceSturdy(level, below, Direction.UP)) {
                    return false;
                }
                BlockState feetState = level.getBlockState(pos);
                if (!feetState.isAir()) {
                    return false;
                }
                BlockState headState = level.getBlockState(pos.above());
                return headState.isAir();
            }
        };
    }

    /**
     * Single-pass iterator implementing the square spiral described in the
     * Javadoc of {@link #spiralIterator(int, int, int, int)}.
     *
     * <p>State machine:
     * <ul>
     *   <li>{@code emittedCenter}: whether the layer-0 point has been
     *       returned.</li>
     *   <li>{@code layer}: the current ring index {@code r}, starting at
     *       {@code 1} once the center has been emitted.</li>
     *   <li>{@code edge}: which edge of the current ring is being walked
     *       (0 = right, 1 = bottom, 2 = left, 3 = top).</li>
     *   <li>{@code idx}: position along the current edge in the range
     *       {@code [0, 2 * layer)}.</li>
     * </ul>
     *
     * <p>Edge layout (in unit-step offsets {@code dx, dz} relative to the
     * center, before being multiplied by {@code step}):
     * <ul>
     *   <li>Right edge: {@code dx = +r}, {@code dz} runs from
     *       {@code -r + 1} to {@code +r} (length {@code 2r}).</li>
     *   <li>Bottom edge: {@code dz = +r}, {@code dx} runs from
     *       {@code +r - 1} to {@code -r} (length {@code 2r}).</li>
     *   <li>Left edge: {@code dx = -r}, {@code dz} runs from
     *       {@code +r - 1} to {@code -r} (length {@code 2r}).</li>
     *   <li>Top edge: {@code dz = -r}, {@code dx} runs from
     *       {@code -r + 1} to {@code +r} (length {@code 2r}).</li>
     * </ul>
     * Each ring's four corners are therefore covered exactly once and the
     * total ring length is {@code 8r}.
     */
    private static final class SquareSpiralIterator implements Iterator<int[]> {

        private final int cx;
        private final int cz;
        private final int radius;
        private final int step;
        /** Set once degenerate input means no ring should ever be emitted. */
        private final boolean ringsDisabled;

        private boolean emittedCenter;
        private int layer;
        private int edge;
        private int idx;

        SquareSpiralIterator(int cx, int cz, int radius, int step) {
            this.cx = cx;
            this.cz = cz;
            this.radius = radius;
            this.step = step;
            // A non-positive step cannot define meaningful rings; emit only
            // the center to keep the iterator total and avoid divide-by-zero
            // semantics in any downstream consumer.
            this.ringsDisabled = step <= 0;
            this.emittedCenter = false;
            this.layer = 1;
            this.edge = 0;
            this.idx = 0;
        }

        @Override
        public boolean hasNext() {
            if (!emittedCenter) {
                return true;
            }
            if (ringsDisabled) {
                return false;
            }
            // Use long arithmetic so layer * step cannot overflow even for
            // pathological radius / step combinations.
            return (long) layer * (long) step <= radius;
        }

        @Override
        public int[] next() {
            if (!emittedCenter) {
                emittedCenter = true;
                return new int[] { cx, cz };
            }
            if (ringsDisabled || (long) layer * (long) step > radius) {
                throw new NoSuchElementException();
            }

            int r = layer;
            int dx;
            int dz;
            switch (edge) {
                case 0: // right edge: dx = +r, dz = -r + 1 .. +r
                    dx = r;
                    dz = -r + 1 + idx;
                    break;
                case 1: // bottom edge: dz = +r, dx = +r - 1 .. -r
                    dx = r - 1 - idx;
                    dz = r;
                    break;
                case 2: // left edge: dx = -r, dz = +r - 1 .. -r
                    dx = -r;
                    dz = r - 1 - idx;
                    break;
                case 3: // top edge: dz = -r, dx = -r + 1 .. +r
                    dx = -r + 1 + idx;
                    dz = -r;
                    break;
                default:
                    throw new IllegalStateException("unexpected edge index: " + edge);
            }

            int x = cx + dx * step;
            int z = cz + dz * step;

            // Advance the state machine: each edge contains exactly 2r
            // points; once an edge is exhausted move on to the next edge,
            // and once all four edges are walked move on to the next layer.
            idx++;
            if (idx >= 2 * r) {
                idx = 0;
                edge++;
                if (edge >= 4) {
                    edge = 0;
                    layer++;
                }
            }
            return new int[] { x, z };
        }
    }
}

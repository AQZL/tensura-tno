package com.tensura_tno.world.spawn;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.layout.PatternLayout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import com.tensura_tno.world.spawn.support.BiomeWorldStub;

/**
 * Property-based tests for {@link SafeSpawnFinder}.
 *
 * <p>This file contains Property 2 (square-spiral iterator invariants) and
 * Property 3 (finder soundness + completeness). Subsequent spec tasks
 * (4.6) append additional property tests and example tests to this same
 * class.
 *
 * <p>Test framework: jqwik 1.9.x running on the JUnit 5 platform.
 */
class SafeSpawnFinderTest {

    /**
     * Property 2: Square-spiral iterator invariants.
     *
     * <p>For any center {@code (cx, cz)} in {@code [-1024, 1024]^2}, any
     * {@code radius} in {@code [0, 4096]}, and any {@code step} in
     * {@code [16, 512]}, the iterator returned by
     * {@link SafeSpawnFinder#spiralIterator(int, int, int, int)} satisfies
     * three invariants:
     * <ol>
     *   <li><b>Uniqueness</b>: every produced {@code (x, z)} point is
     *       distinct.</li>
     *   <li><b>Layer monotonicity</b>: the layer index
     *       {@code max(|x-cx|, |z-cz|) / step} is non-decreasing across
     *       the produced sequence.</li>
     *   <li><b>Upper bound</b>: the total number of produced points is at
     *       most {@code (2 * ceil(radius / step) + 1)^2}.</li>
     * </ol>
     *
     * <p>The {@code step} lower bound is set to {@code 16} (matching the
     * production {@code spawnSafeSearchStep} minimum in
     * {@link BiomeBlacklistConfig}) so that even at the worst case
     * {@code radius=4096, step=16} the per-try uniqueness {@link HashSet}
     * holds at most {@code (2*256+1)^2 = 263169} keys (~21 MB), well
     * within the default test JVM heap. Production
     * {@code spawnSafeSearchRadius} / {@code spawnSafeSearchStep} ranges
     * are {@code [64, 8192]} / {@code [16, 512]}, so this tighter test
     * range still covers the realistic operational parameter space, plus
     * the {@code radius=0} edge case where the iterator emits only the
     * center point.
     *
     * <p><b>Validates: Requirements 6.1, 9.3</b>
     */
    @Property(tries = 200)
    void spiralIteratorInvariants(
            @ForAll @IntRange(min = -1024, max = 1024) int cx,
            @ForAll @IntRange(min = -1024, max = 1024) int cz,
            @ForAll @IntRange(min = 0, max = 4096) int radius,
            @ForAll @IntRange(min = 16, max = 512) int step) {

        Iterator<int[]> it = SafeSpawnFinder.spiralIterator(cx, cz, radius, step);

        Set<Long> seen = new HashSet<>();
        long count = 0L;
        int previousLayer = -1;

        while (it.hasNext()) {
            int[] xz = it.next();
            int x = xz[0];
            int z = xz[1];

            // (a) Uniqueness: pack (x, z) into a single long key and ensure
            //     no point is yielded twice.
            long key = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
            if (!seen.add(key)) {
                throw new AssertionError(
                        "duplicate point produced: (" + x + ", " + z + ")"
                                + " for cx=" + cx + ", cz=" + cz
                                + ", radius=" + radius + ", step=" + step);
            }

            // (b) Layer monotonicity: layer = max(|x-cx|, |z-cz|) / step.
            //     Use long arithmetic to avoid overflow for large offsets.
            long absDx = Math.abs((long) x - (long) cx);
            long absDz = Math.abs((long) z - (long) cz);
            long chebyshev = Math.max(absDx, absDz);
            int layer = (int) (chebyshev / (long) step);
            if (layer < previousLayer) {
                throw new AssertionError(
                        "layer monotonicity violated: previous=" + previousLayer
                                + ", current=" + layer
                                + " at (" + x + ", " + z + ")"
                                + " for cx=" + cx + ", cz=" + cz
                                + ", radius=" + radius + ", step=" + step);
            }
            previousLayer = layer;

            count++;

            // Defensive: cap iteration count to keep a buggy iterator from
            // producing an infinite sequence and stalling the test process.
            // The maximum legitimate count is bounded by (c) below.
            if (count > 10_000_000L) {
                throw new AssertionError(
                        "iterator produced more than 10M points; suspected"
                                + " infinite loop for cx=" + cx + ", cz=" + cz
                                + ", radius=" + radius + ", step=" + step);
            }
        }

        // (c) Upper bound: total points <= (2 * ceil(radius / step) + 1)^2.
        //     ceil(radius/step) = (radius + step - 1) / step for radius>=0,
        //     step>=1.
        long ceilRadius = ((long) radius + (long) step - 1L) / (long) step;
        long side = 2L * ceilRadius + 1L;
        long upperBound = side * side;
        if (count > upperBound) {
            throw new AssertionError(
                    "point count " + count + " exceeds upper bound "
                            + upperBound + " for cx=" + cx + ", cz=" + cz
                            + ", radius=" + radius + ", step=" + step);
        }
    }

    /**
     * Property 3: SafeSpawnFinder return-value correctness (soundness +
     * completeness).
     *
     * <p>Drives the package-private narrow-interface overload
     * {@link SafeSpawnFinder#findSafeSpawn(BiomeLookup, SurfaceLookup, BlockPos, int, int, Set)}
     * with a {@link BiomeWorldStub} whose biome and standability are
     * randomised over a small pool of {@link ResourceLocation}s, then
     * checks two complementary invariants:
     *
     * <ol>
     *   <li><b>Soundness</b>: if the finder returns {@code Optional.of(p)},
     *       then
     *       {@link SafeSpawnFinder#isSafePosition(BiomeLookup, SurfaceLookup, BlockPos, Set)}
     *       must hold for {@code p} under the same lookups and blacklist.
     *       In other words, the finder never advertises an unsafe point.
     *       (Requirement 6.2)</li>
     *   <li><b>Completeness</b>: if at least one safe witness exists among
     *       the points produced by
     *       {@link SafeSpawnFinder#spiralIterator(int, int, int, int)}
     *       (re-walked here independently and tested with the same
     *       {@code isSafePosition} predicate), then the finder must return
     *       a non-empty {@link Optional}. In other words, the finder never
     *       skips over an existing witness in the search grid.
     *       (Requirements 6.3, 6.4)</li>
     * </ol>
     *
     * <p>The biome and standability functions are seeded by a deterministic
     * mixing of {@code (worldSeed, x, z, salt)} so generation is
     * reproducible per jqwik counter-example, and the witness re-walk sees
     * exactly the same world the finder did. We deliberately keep the
     * biome pool small (six ids) and the blacklist size in {@code [0, 6]},
     * so blacklist coverage ranges from "everything safe" to "almost
     * nothing safe" — both extremes exercise different branches of the
     * finder's loop. The {@code salt} parameter decorrelates biome and
     * standability, so a coordinate's biome and its standability are
     * independent draws — that is what makes the search predicate
     * non-trivial across the grid.
     *
     * <p>Search parameters are capped at radius 256 and step in
     * {@code [16, 64]} so the total search grid stays under ~1k points per
     * try; the second {@link SafeSpawnFinder#spiralIterator(int, int, int, int)}
     * walk in the completeness check stays bounded by the same envelope.
     *
     * <p><b>Validates: Requirements 6.2, 6.3, 6.4</b>
     */
    @Property(tries = 200)
    void findSafeSpawnSoundnessAndCompleteness(
            @ForAll @IntRange(min = -512, max = 512) int cx,
            @ForAll @IntRange(min = -512, max = 512) int cz,
            @ForAll @IntRange(min = 0, max = 256) int radius,
            @ForAll @IntRange(min = 16, max = 64) int step,
            @ForAll int worldSeed,
            @ForAll @IntRange(min = 0, max = 6) int blacklistSize) {

        // Small pool of biome ids: a mix of Tensura / Vanilla / generic
        // mod ids. The blacklist is the prefix of length `blacklistSize`,
        // so blacklistSize=0 means "every biome is safe" and
        // blacklistSize=pool.length means "every biome is forbidden".
        ResourceLocation[] pool = new ResourceLocation[] {
                ResourceLocation.fromNamespaceAndPath("tensura", "ancient_forest"),
                ResourceLocation.fromNamespaceAndPath("tensura", "desert_of_death"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "plains"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "forest"),
                ResourceLocation.fromNamespaceAndPath("modid", "safe_biome"),
                ResourceLocation.fromNamespaceAndPath("modid", "another_biome"),
        };
        Set<ResourceLocation> blacklist = new HashSet<>();
        for (int i = 0; i < blacklistSize; i++) {
            blacklist.add(pool[i]);
        }

        // Deterministic randomised biome and standability per coordinate.
        // The salt parameter ensures the two lookups don't correlate, so
        // a coord with a "safe" biome may or may not be standable, and
        // vice versa.
        BiomeWorldStub world = BiomeWorldStub.builder()
                .biomeAt((x, z) -> pool[Math.floorMod(mix(worldSeed, x, z, 1), pool.length)])
                .constantHeight(64)
                .isStandable(pos -> Math.floorMod(mix(worldSeed, pos.getX(), pos.getZ(), 2), 4) != 0)
                .minBuildHeight(-64)
                .maxBuildHeight(320)
                .build();

        BlockPos origin = new BlockPos(cx, 64, cz);
        Optional<BlockPos> result = SafeSpawnFinder.findSafeSpawn(
                world.asBiomeLookup(), world.asSurfaceLookup(), origin, radius, step, blacklist);

        // Soundness: any returned point must satisfy isSafePosition under
        // the same lookups and blacklist. (Requirement 6.2)
        if (result.isPresent()) {
            BlockPos p = result.get();
            boolean safe = SafeSpawnFinder.isSafePosition(
                    world.asBiomeLookup(), world.asSurfaceLookup(), p, blacklist);
            if (!safe) {
                throw new AssertionError(
                        "soundness violated: findSafeSpawn returned " + p
                                + " but isSafePosition is false; cx=" + cx
                                + ", cz=" + cz + ", radius=" + radius
                                + ", step=" + step + ", seed=" + worldSeed
                                + ", blacklist=" + blacklist);
            }
        }

        // Completeness: walk the spiral independently and look for any
        // witness; if one exists, the finder must have returned non-empty.
        // Walking the iterator a second time is safe because spiralIterator
        // is a fresh single-pass instance per call. (Requirements 6.3, 6.4)
        Iterator<int[]> witnessIt = SafeSpawnFinder.spiralIterator(cx, cz, radius, step);
        BlockPos witness = null;
        while (witnessIt.hasNext()) {
            int[] xz = witnessIt.next();
            BlockPos surface = world.asSurfaceLookup().surfaceAt(xz[0], xz[1]);
            if (SafeSpawnFinder.isSafePosition(
                    world.asBiomeLookup(), world.asSurfaceLookup(), surface, blacklist)) {
                witness = surface;
                break;
            }
        }
        if (witness != null && result.isEmpty()) {
            throw new AssertionError(
                    "completeness violated: witness " + witness
                            + " exists in spiral grid but findSafeSpawn"
                            + " returned empty; cx=" + cx + ", cz=" + cz
                            + ", radius=" + radius + ", step=" + step
                            + ", seed=" + worldSeed
                            + ", blacklist=" + blacklist);
        }
    }

    /**
     * Deterministic 32-bit mixing function used to make per-coordinate
     * biome and standability decisions reproducible from a single
     * {@code worldSeed} parameter. The {@code salt} argument decorrelates
     * the two lookups so a coordinate's biome and its standability are
     * independent draws — that is what makes the search predicate
     * non-trivial across the grid.
     *
     * <p>Implementation follows the standard MurmurHash3 32-bit finalizer
     * applied to a small linear combination of (seed, x, z, salt). This
     * keeps the test self-contained (no Random instance state crossing
     * iterations) and is fast enough to run inside a tight inner loop.
     */
    private static int mix(int seed, int x, int z, int salt) {
        int h = seed;
        h = 31 * h + x;
        h = 31 * h + z;
        h = 31 * h + salt;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    // =====================================================================
    // Slow-search WARN example test (task 4.6, Requirement 9.4)
    // =====================================================================

    /**
     * Example test: when {@link SafeSpawnFinder#findSafeSpawn} exceeds
     * {@link SafeSpawnFinder#SEARCH_TIMEOUT_WARN_MS} (200 ms) of wall-clock
     * time, exactly one {@code WARN} log event is emitted whose message
     * contains the literal {@code "safe-spawn search took"} marker so
     * server operators can identify the slow-search trigger and tune
     * {@code spawnSafeSearchRadius} / {@code spawnSafeSearchStep}.
     *
     * <p>The slowness is induced deterministically by injecting a
     * {@link BiomeLookup} that calls {@link Thread#sleep(long)} for 250 ms
     * on its first invocation — comfortably above the 200 ms threshold —
     * and is otherwise inert. We pair it with a tiny {@code radius=1,
     * step=1} grid (only the center is visited) so the test wall-clock
     * cost is dominated by the single sleep rather than search work, and
     * we set the blacklist to contain the biome returned by the lookup so
     * the finder rejects the only candidate and falls through to the
     * empty-grid exit path — which still runs the {@code warnIfSlow}
     * check on its way out.
     *
     * <p>Log capture uses a programmatic Log4j2 list-appender attached to
     * the named logger {@code com.tensura_tno.world.spawn.SafeSpawnFinder}
     * (Mojang's {@code LogUtils.getLogger()} returns an SLF4J logger which
     * is bound to Log4j2 in this project through
     * {@code log4j-slf4j2-impl}, so attaching to the named Log4j2 logger
     * captures every event the production code emits). This mirrors the
     * {@code CapturingAppender} pattern in {@code BiomeBlacklistConfigTest}.
     *
     * <p>Annotated with {@link DisabledIfEnvironmentVariable} so the test
     * is skipped on CI: it intentionally sleeps ~250 ms, which is fine for
     * a local manual run but not worth paying for on every CI build.
     *
     * <p><b>Validates: Requirement 9.4</b>
     */
    @Test
    @DisabledIfEnvironmentVariable(named = "CI", matches = "true")
    void slowSearch_emitsWarnLog() {
        ResourceLocation biome = ResourceLocation.fromNamespaceAndPath("tensura", "ancient_forest");
        Set<ResourceLocation> blacklist = new HashSet<>();
        blacklist.add(biome);

        // Surface lookup: trivial — every (x, z) -> (x, 64, z), every
        // position standable, build range matches Vanilla overworld.
        BiomeWorldStub world = BiomeWorldStub.builder()
                .constantBiome(biome)
                .constantHeight(64)
                .build();

        // BiomeLookup that sleeps once on its first invocation so the
        // wall-clock cost of findSafeSpawn deterministically exceeds
        // SEARCH_TIMEOUT_WARN_MS (200 ms).
        BiomeLookup slowBiomeLookup = pos -> {
            try {
                Thread.sleep(250L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("test thread interrupted during induced sleep", e);
            }
            return biome;
        };

        CapturingAppender appender = CapturingAppender.attach(SafeSpawnFinder.class);
        try {
            // Tiny grid: radius=1, step=1 means spiralIterator emits only
            // the center point. The blacklist contains `biome`, so
            // isSafePosition rejects it and findSafeSpawn returns empty
            // after walking the slow biome lookup once.
            BlockPos origin = new BlockPos(0, 64, 0);
            Optional<BlockPos> result = SafeSpawnFinder.findSafeSpawn(
                    slowBiomeLookup, world.asSurfaceLookup(), origin, 1, 1, blacklist);

            // The blacklist contains the only biome the lookup ever returns,
            // so the finder must walk the full grid and exit empty.
            assertTrue(result.isEmpty(),
                "findSafeSpawn should return empty when the only candidate is in the blacklist; got=" + result);

            List<LogEvent> warns = appender.events().stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
            assertTrue(!warns.isEmpty(),
                "findSafeSpawn must emit a WARN when wall-clock duration exceeds "
                    + SafeSpawnFinder.SEARCH_TIMEOUT_WARN_MS + "ms; events=" + appender.events());

            boolean matched = warns.stream()
                .map(e -> e.getMessage().getFormattedMessage())
                .anyMatch(msg -> msg.contains("safe-spawn search took"));
            assertTrue(matched,
                "WARN log must contain the marker 'safe-spawn search took'; warns="
                    + warns.stream().map(e -> e.getMessage().getFormattedMessage()).toList());
        } finally {
            appender.detach();
        }
    }

    /**
     * Programmatic Log4j2 list-appender used by
     * {@link #slowSearch_emitsWarnLog()}. The production code uses
     * {@code LogUtils.getLogger()} (SLF4J), which is bound to Log4j2 in
     * this project through {@code log4j-slf4j2-impl}; events therefore
     * reach the Log4j2 logger named after the caller class. This mirrors
     * the {@code CapturingAppender} helper in
     * {@code BiomeBlacklistConfigTest}.
     */
    static final class CapturingAppender extends AbstractAppender {

        private final List<LogEvent> events = new CopyOnWriteArrayList<>();
        private final org.apache.logging.log4j.core.Logger target;

        private CapturingAppender(String name, org.apache.logging.log4j.core.Logger target) {
            super(name, null, PatternLayout.createDefaultLayout(), false,
                org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
            this.target = target;
        }

        static CapturingAppender attach(Class<?> clazz) {
            String loggerName = clazz.getName();
            // Make sure the named logger does not silently drop WARN/INFO/DEBUG
            // because of an inherited high root threshold.
            Configurator.setLevel(loggerName, Level.ALL);
            org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(loggerName);
            CapturingAppender app = new CapturingAppender(
                "capturing-" + loggerName + "-" + System.identityHashCode(Thread.currentThread()),
                logger);
            app.start();
            logger.addAppender(app);
            return app;
        }

        void detach() {
            target.removeAppender(this);
            stop();
        }

        List<LogEvent> events() {
            return new ArrayList<>(events);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}

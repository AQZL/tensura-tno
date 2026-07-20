package com.tensura_tno.world.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.layout.PatternLayout;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import com.tensura_tno.world.spawn.support.BiomeWorldStub;

/**
 * Property-based tests for {@link SpawnRelocator}.
 *
 * <p>This file currently contains:
 * <ul>
 *   <li>Property 1 — biome blacklist membership matches set containment;</li>
 *   <li>Property 4 — guard preservation: every blocking guard makes the
 *       relocation pass a no-op (task 7.5);</li>
 *   <li>Property 5 — hit-path consistency: when all guards pass, the
 *       resulting position equals the finder's output (task 7.6);</li>
 *   <li>Property 6 — relocation idempotence: at most one effective
 *       application per scope across N consecutive invocations
 *       (task 7.7).</li>
 * </ul>
 * Subsequent spec tasks (7.8) append additional example tests to this
 * same class.
 *
 * <p>Test framework: jqwik 1.9.x running on the JUnit 5 platform.
 */
class SpawnRelocatorTest {

    /**
     * Property 1: Biome blacklist membership matches set containment.
     *
     * <p>For any {@code Set<ResourceLocation> blacklist} and any
     * {@code BiomeLookup} (here driven by a {@link BiomeWorldStub}'s
     * {@code biomeAt} function), the package-private narrow overload
     * {@link SpawnRelocator#isInBlacklist(BiomeLookup, BlockPos, Set)}
     * returns {@code true} if and only if the biome resource location
     * resolved at {@code pos} is contained in {@code blacklist}.
     *
     * <p>The narrow overload is the test entry point that
     * {@link SpawnRelocator#isInBlacklist(net.minecraft.server.level.ServerLevel, BlockPos)}
     * delegates to in production: bridging the real
     * {@code ServerLevel.getBiome(pos).unwrapKey().get().location()} call
     * through a {@code BiomeLookup} lambda. Both Requirements 1.1 and 2.1
     * require this exact "resolve biome id then compare against blacklist"
     * semantics for the world initial spawn path and the player first-login
     * path respectively.
     *
     * <p><b>Validates: Requirements 1.1, 2.1</b>
     */
    @Property(tries = 100)
    void isInBlacklist_membershipMatchesSetContainment(
            @ForAll @IntRange(min = -2048, max = 2048) int x,
            @ForAll @IntRange(min = -64, max = 319) int y,
            @ForAll @IntRange(min = -2048, max = 2048) int z,
            @ForAll("biomeIds") ResourceLocation biomeAtPos,
            @ForAll("biomeIdSets") Set<ResourceLocation> blacklist) {

        // Drive the BiomeLookup with a stub world that resolves any
        // coordinate to `biomeAtPos`. We sample a single (x, y, z) per
        // invocation, so per-coordinate variation is irrelevant for this
        // membership rule; jqwik's coverage of the `biomeAtPos` /
        // `blacklist` cross-product ensures both branches of the
        // biconditional (hit and miss) are exercised many times.
        BiomeWorldStub world = BiomeWorldStub.builder()
                .constantBiome(biomeAtPos)
                .build();

        BlockPos pos = new BlockPos(x, y, z);

        boolean actual = SpawnRelocator.isInBlacklist(world.asBiomeLookup(), pos, blacklist);

        // The expected value is computed by re-resolving the biome through
        // the same BiomeLookup adapter that production code uses, so the
        // test stays faithful to the "resolve via lookup, compare against
        // blacklist" contract spelled out in Property 1.
        ResourceLocation resolved = world.asBiomeLookup().biomeAt(pos);
        boolean expected = blacklist.contains(resolved);

        if (actual != expected) {
            throw new AssertionError(
                    "isInBlacklist must match blacklist.contains(biomeAt(pos));"
                            + " actual=" + actual
                            + ", expected=" + expected
                            + ", resolvedBiome=" + resolved
                            + ", blacklist=" + blacklist
                            + ", pos=" + pos);
        }
    }

    /**
     * Pool of plausible biome resource locations used for both the
     * "biome at position" generator and the blacklist-element generator.
     * Biased toward Tensura / Vanilla overworld biome ids so the
     * blacklist intersects {@code biomeAtPos} frequently and both
     * branches of the biconditional get exercised.
     */
    @Provide
    Arbitrary<ResourceLocation> biomeIds() {
        return Arbitraries.of(
                ResourceLocation.fromNamespaceAndPath("tensura", "ancient_forest"),
                ResourceLocation.fromNamespaceAndPath("tensura", "desert_of_death"),
                ResourceLocation.fromNamespaceAndPath("tensura", "magic_lake"),
                ResourceLocation.fromNamespaceAndPath("tensura", "wandering_woods"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "plains"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "forest"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "ocean"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "badlands"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "swamp"),
                ResourceLocation.fromNamespaceAndPath("modid", "custom_biome")
        );
    }

    /**
     * Generator for {@code Set<ResourceLocation>} drawn from the same pool
     * as {@link #biomeIds()}. The empty set, single-element sets, and the
     * full pool are all reachable, so the property exercises the full
     * truth table for set containment.
     */
    @Provide
    Arbitrary<Set<ResourceLocation>> biomeIdSets() {
        return biomeIds().list().ofMinSize(0).ofMaxSize(10).map(list -> {
            // Convert to a Set while preserving insertion order, so jqwik's
            // shrinker has a stable representation when minimising
            // counter-examples.
            Set<ResourceLocation> set = new LinkedHashSet<>((List<ResourceLocation>) list);
            return set;
        });
    }

    // ----- Property 4: Guard preservation -----------------------------------

    /**
     * Identifies which guard the property test should "block" on a given
     * trial. Every entry corresponds to one of the nine blocking
     * conditions enumerated in design.md's Property 4 / task 7.5
     * description; the {@link #appliesTo(Path)} predicate notes whether a
     * particular blocker is meaningful for the world-spawn path
     * ({@code WORLD}), the player path ({@code PLAYER}), or both.
     *
     * <p>Each property iteration picks one {@code Blocker} uniformly at
     * random, materialises a "happy-path" world / player context (every
     * guard satisfied so the relocation pass would otherwise rewrite the
     * coordinate), then flips the chosen guard so it returns the blocking
     * value. The post-condition is that the position observed by the
     * caller stays equal to the original coordinate.
     */
    private enum Blocker {
        DISABLED,
        TENSURA_NOT_LOADED,
        NON_OVERWORLD,
        BIOME_NOT_HIT,
        PLAYER_HAS_RESPAWN,
        PLAYER_FLAG_SET,
        WORLD_FLAG_DONE,
        FINDER_EMPTY,
        FINDER_THROWS;

        /**
         * Returns whether this blocker is meaningful for the given
         * relocation path. Some blockers are world-only ({@code WORLD_FLAG_DONE})
         * or player-only ({@code PLAYER_HAS_RESPAWN},
         * {@code PLAYER_FLAG_SET}); the rest apply to both. Blockers that
         * do not apply to a given path are simply ignored on that path's
         * trial — the path keeps its happy-path configuration so the
         * post-condition still asserts "no relocation happened" (which
         * holds because, on the other path, the corresponding blocker is
         * the one being exercised).
         */
        boolean appliesTo(Path path) {
            return switch (this) {
                case PLAYER_HAS_RESPAWN, PLAYER_FLAG_SET -> path == Path.PLAYER;
                case WORLD_FLAG_DONE -> path == Path.WORLD;
                default -> true;
            };
        }
    }

    /**
     * Identifies which orchestration entry point the trial is exercising.
     * The two paths share most blockers but differ in the ones tied to
     * their persistence model: the world path has its own SavedData flag
     * ({@code WORLD_FLAG_DONE}) while the player path has a respawn-bed
     * guard plus an NBT flag ({@code PLAYER_HAS_RESPAWN},
     * {@code PLAYER_FLAG_SET}).
     */
    private enum Path {
        WORLD,
        PLAYER
    }

    /**
     * Provider of {@link Blocker} values. Uniform over the full enum so
     * that, across {@code @Property(tries = 200)} iterations, each path
     * (WORLD / PLAYER) sees every applicable blocker many times. Blockers
     * that do not apply to a given path are degenerate trials (no guard
     * is flipped on that path), but the cross-path coverage means each
     * blocker is genuinely exercised on at least one path per property
     * invocation.
     */
    @Provide
    Arbitrary<Blocker> blockers() {
        return Arbitraries.of(Blocker.values());
    }

    /**
     * Property 4: Guard preservation.
     *
     * <p>For any blocking guard listed below, calling
     * {@link SpawnRelocator#relocateWorldSpawnCore(WorldSpawnContext)}
     * (resp. {@link SpawnRelocator#relocatePlayerCore(PlayerSpawnContext)})
     * leaves {@code ctx.getSharedSpawnPos()} (resp.
     * {@code ctx.blockPosition()}) unchanged.
     *
     * <p>The blocking guards exercised, drawn from design.md's Property 4:
     * <ul>
     *   <li>{@code DISABLED} — feature toggle off (Requirement 4.3)</li>
     *   <li>{@code TENSURA_NOT_LOADED} — Tensura mod absent
     *       (Requirements 5.1, 5.2)</li>
     *   <li>{@code NON_OVERWORLD} — wrong dimension
     *       (Requirements 7.1, 7.2)</li>
     *   <li>{@code BIOME_NOT_HIT} — current biome not in blacklist
     *       (Requirements 1.3, 2.3)</li>
     *   <li>{@code PLAYER_HAS_RESPAWN} — player path only; bed/anchor
     *       binding (Requirement 3.1)</li>
     *   <li>{@code PLAYER_FLAG_SET} — player path only; persistent NBT
     *       flag already true (Requirements 2.4 / 3.3 / 6.5)</li>
     *   <li>{@code WORLD_FLAG_DONE} — world path only; SavedData
     *       already done (Requirements 1.4 / 6.5)</li>
     *   <li>{@code FINDER_EMPTY} — {@code findSafeSpawn} returns
     *       {@code Optional.empty()} (Requirement 6.3)</li>
     *   <li>{@code FINDER_THROWS} — {@code findSafeSpawn} throws a
     *       {@code RuntimeException} (Requirements 6.4, 6.5)</li>
     * </ul>
     *
     * <p>The trial picks a blocker plus an origin point, builds a
     * happy-path world AND player context (so we cover both entry points
     * in a single trial), flips the chosen guard, and runs the
     * orchestration core for both paths. Both post-conditions
     * (world spawn unchanged, player position unchanged) are asserted on
     * every iteration: any blocker that is path-specific simply leaves
     * the other path in its happy-path configuration — but the
     * happy-path configuration also intentionally fails one of its
     * post-condition prerequisites (the origin biome <i>is</i> in the
     * blacklist), so we still need at least one blocker to keep the
     * other path from rewriting. We do that by always pinning each path's
     * "scope-specific" blocker (PLAYER side: NBT flag; WORLD side:
     * SavedData flag) <em>except</em> the one being exercised. See the
     * {@code applyBlocker} helper for details.
     *
     * <p><b>Validates: Requirements 1.3, 2.3, 3.1, 5.1, 6.3, 6.4, 6.5,
     * 7.1, 7.2</b>
     */
    @Property(tries = 200)
    void guardsPreservePositions(
            @ForAll("blockers") Blocker blocker,
            @ForAll @IntRange(min = -2048, max = 2048) int originX,
            @ForAll @IntRange(min = 0, max = 255) int originY,
            @ForAll @IntRange(min = -2048, max = 2048) int originZ) {

        BlockPos origin = new BlockPos(originX, originY, originZ);
        ResourceLocation hitBiome = ResourceLocation.fromNamespaceAndPath("tensura", "ancient_forest");
        ResourceLocation safeBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");
        Set<ResourceLocation> blacklist = Set.of(hitBiome);

        // Witness candidate the (untouched) finder would return on the
        // happy path: a deterministic offset from origin, in a biome that
        // is NOT in the blacklist. The post-condition fails iff the
        // orchestration path actually applies this rewrite.
        BlockPos witness = origin.offset(64, 0, 0);

        // ---- WORLD path ----------------------------------------------------
        TestWorldContext worldCtx = new TestWorldContext(origin, blacklist, hitBiome, safeBiome, witness);
        // Apply the blocker on the WORLD path if it is meaningful there.
        if (blocker.appliesTo(Path.WORLD)) {
            applyWorldBlocker(worldCtx, blocker);
        } else {
            // The blocker is player-only for this trial. To keep the world
            // path from rewriting (which would invalidate the assertion
            // unrelated to the blocker we are studying), we pin the
            // WORLD-side scope blocker as a baseline so this trial
            // actually still proves "world path stayed put". This is the
            // dual of "blockers that do not apply to a path are degenerate
            // trials"; here we install the canonical world-only stop.
            applyWorldBlocker(worldCtx, Blocker.WORLD_FLAG_DONE);
        }

        BlockPos worldBefore = worldCtx.getSharedSpawnPos();
        SpawnRelocator.relocateWorldSpawnCore(worldCtx);
        BlockPos worldAfter = worldCtx.getSharedSpawnPos();
        if (!worldBefore.equals(worldAfter)) {
            throw new AssertionError(
                    "world-spawn relocation must be a no-op under blocker " + blocker
                            + "; before=" + worldBefore + ", after=" + worldAfter);
        }
        if (worldCtx.setDefaultSpawnPosCalls != 0) {
            throw new AssertionError(
                    "setDefaultSpawnPos must NOT be invoked under blocker " + blocker
                            + "; calls=" + worldCtx.setDefaultSpawnPosCalls);
        }

        // ---- PLAYER path ---------------------------------------------------
        TestPlayerContext playerCtx = new TestPlayerContext(origin, blacklist, hitBiome, safeBiome, witness);
        if (blocker.appliesTo(Path.PLAYER)) {
            applyPlayerBlocker(playerCtx, blocker);
        } else {
            applyPlayerBlocker(playerCtx, Blocker.PLAYER_FLAG_SET);
        }

        BlockPos playerBefore = playerCtx.blockPosition();
        SpawnRelocator.relocatePlayerCore(playerCtx);
        BlockPos playerAfter = playerCtx.blockPosition();
        if (!playerBefore.equals(playerAfter)) {
            throw new AssertionError(
                    "player relocation must be a no-op under blocker " + blocker
                            + "; before=" + playerBefore + ", after=" + playerAfter);
        }
        if (playerCtx.teleportCalls != 0) {
            throw new AssertionError(
                    "teleportTo must NOT be invoked under blocker " + blocker
                            + "; calls=" + playerCtx.teleportCalls);
        }
    }

    /**
     * Flips exactly the WORLD-side facet of {@code blocker} on
     * {@code ctx}. The "happy path" baseline lives in
     * {@link TestWorldContext}'s constructor; this helper just pokes the
     * one field associated with the chosen blocker.
     */
    private static void applyWorldBlocker(TestWorldContext ctx, Blocker blocker) {
        switch (blocker) {
            case DISABLED -> ctx.enabled = false;
            case TENSURA_NOT_LOADED -> ctx.tensuraLoaded = false;
            case NON_OVERWORLD -> ctx.overworld = false;
            case BIOME_NOT_HIT ->
                // Resolve the origin biome to something OUTSIDE the blacklist;
                // every other position keeps its baseline mapping so a hit
                // would still be possible if the orchestration ignored the
                // origin's biome (which it must NOT).
                ctx.biomeAtOrigin = ctx.safeBiome;
            case WORLD_FLAG_DONE -> ctx.flagDone = true;
            case FINDER_EMPTY -> ctx.finder = origin -> Optional.empty();
            case FINDER_THROWS ->
                ctx.finder = origin -> { throw new RuntimeException("finder boom"); };
            default -> {
                // Player-only blockers: leave the WORLD context happy-path,
                // which the caller already chose to do.
            }
        }
    }

    /**
     * Flips exactly the PLAYER-side facet of {@code blocker} on
     * {@code ctx}. The "happy path" baseline lives in
     * {@link TestPlayerContext}'s constructor; this helper just pokes the
     * one field associated with the chosen blocker.
     */
    private static void applyPlayerBlocker(TestPlayerContext ctx, Blocker blocker) {
        switch (blocker) {
            case DISABLED -> ctx.enabled = false;
            case TENSURA_NOT_LOADED -> ctx.tensuraLoaded = false;
            case NON_OVERWORLD -> ctx.overworld = false;
            case BIOME_NOT_HIT -> ctx.biomeAtOrigin = ctx.safeBiome;
            case PLAYER_HAS_RESPAWN -> ctx.hasRespawn = true;
            case PLAYER_FLAG_SET -> ctx.persistentFlag = true;
            case FINDER_EMPTY -> ctx.finder = origin -> Optional.empty();
            case FINDER_THROWS ->
                ctx.finder = origin -> { throw new RuntimeException("finder boom"); };
            default -> {
                // World-only blockers: leave the PLAYER context happy-path,
                // which the caller already chose to do.
            }
        }
    }

    // ----- Property 5: Hit-path consistency ---------------------------------

    /**
     * Property 5: Hit-path consistency — when all guards pass, position
     * equals the finder's output.
     *
     * <p>Per design.md's Property 5: under the negation of every Property 4
     * blocker (feature enabled, Tensura loaded, overworld dimension, flag
     * not done, origin biome in the blacklist, no respawn-bed binding,
     * persistent NBT flag false), and for any {@link BlockPos} {@code p}
     * that {@link SafeSpawnFinder#findSafeSpawn} would return:
     *
     * <ul>
     *   <li>after
     *       {@link SpawnRelocator#relocateWorldSpawnCore(WorldSpawnContext)},
     *       {@code ctx.getSharedSpawnPos().equals(p)} holds;</li>
     *   <li>after
     *       {@link SpawnRelocator#relocatePlayerCore(PlayerSpawnContext)},
     *       {@code ctx.blockPosition().equals(p)} holds modulo the
     *       canonical {@code +0.5} block-center offset on x / z that the
     *       production code applies before
     *       {@code Entity#teleportTo(double, double, double)}. Vanilla's
     *       {@code Entity#blockPosition()} floors that continuous offset
     *       back to the integer block, so the net effect is exactly
     *       {@code p}.</li>
     * </ul>
     *
     * <p>The trial reuses the {@link TestWorldContext} / {@link TestPlayerContext}
     * stubs introduced for Property 4: their constructor defaults already
     * encode the happy-path baseline, so all the trial has to do is swap
     * the finder for {@code () -> Optional.of(p)} and dispatch the two
     * orchestration cores. The post-conditions are then read back from
     * the stubs, which mirror the production code's
     * {@code setDefaultSpawnPos} / {@code teleportTo} side-effects.
     *
     * <p>The witness {@code p} is sampled from a moderately wide
     * coordinate band: x / z in {@code [-2048, 2048]} (matches the world
     * spawn / player position generators used for Property 4) and y in
     * {@code [-64, 319]} (covers the Vanilla 1.21.1 build-height range,
     * including the {@code y < 0} negative-coordinate case where the
     * floor of a {@code +0.5} offset must still produce the original
     * integer block; see {@code BlockPos.containing} javadoc).
     *
     * <p><b>Validates: Requirements 1.2, 2.2</b>
     */
    @Property(tries = 100)
    void hitPathPositionEqualsFinderOutput(
            @ForAll @IntRange(min = -2048, max = 2048) int originX,
            @ForAll @IntRange(min = 0, max = 255) int originY,
            @ForAll @IntRange(min = -2048, max = 2048) int originZ,
            @ForAll @IntRange(min = -2048, max = 2048) int witnessX,
            @ForAll @IntRange(min = -64, max = 319) int witnessY,
            @ForAll @IntRange(min = -2048, max = 2048) int witnessZ) {

        BlockPos origin = new BlockPos(originX, originY, originZ);
        BlockPos p = new BlockPos(witnessX, witnessY, witnessZ);

        ResourceLocation hitBiome = ResourceLocation.fromNamespaceAndPath("tensura", "ancient_forest");
        ResourceLocation safeBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");
        Set<ResourceLocation> blacklist = Set.of(hitBiome);

        // ---- WORLD path ----------------------------------------------------
        // Happy-path baseline + finder stub returning Optional.of(p) for
        // any origin — the constructor-supplied witness is also p so the
        // initial Function<BlockPos, Optional<BlockPos>> is already
        // "() -> Optional.of(p)"; we re-pin it here for clarity.
        TestWorldContext worldCtx = new TestWorldContext(origin, blacklist, hitBiome, safeBiome, p);
        worldCtx.finder = o -> Optional.of(p);

        SpawnRelocator.relocateWorldSpawnCore(worldCtx);

        BlockPos worldAfter = worldCtx.getSharedSpawnPos();
        if (!p.equals(worldAfter)) {
            throw new AssertionError(
                    "world spawn must equal finder output on hit path;"
                            + " expected=" + p
                            + ", actual=" + worldAfter
                            + ", origin=" + origin);
        }
        if (worldCtx.setDefaultSpawnPosCalls != 1) {
            throw new AssertionError(
                    "setDefaultSpawnPos must be invoked exactly once on hit path;"
                            + " calls=" + worldCtx.setDefaultSpawnPosCalls);
        }

        // ---- PLAYER path ---------------------------------------------------
        TestPlayerContext playerCtx = new TestPlayerContext(origin, blacklist, hitBiome, safeBiome, p);
        playerCtx.finder = o -> Optional.of(p);

        SpawnRelocator.relocatePlayerCore(playerCtx);

        // Production code teleports to (p.x + 0.5, p.y, p.z + 0.5);
        // the stub's teleportTo defers to BlockPos.containing(x, y, z),
        // which uses Mth.floor (toward negative infinity) — so the net
        // effect for any integer p is exactly p, including negative
        // coordinates where floor(p.x + 0.5) == p.x still holds.
        BlockPos playerAfter = playerCtx.blockPosition();
        if (!p.equals(playerAfter)) {
            throw new AssertionError(
                    "player block position must equal finder output on hit path;"
                            + " expected=" + p
                            + ", actual=" + playerAfter
                            + ", origin=" + origin);
        }
        if (playerCtx.teleportCalls != 1) {
            throw new AssertionError(
                    "teleportTo must be invoked exactly once on hit path;"
                            + " calls=" + playerCtx.teleportCalls);
        }
    }

    // ----- Property 6: Relocation idempotence -------------------------------

    /**
     * Property 6: Relocation idempotence — at most one effective
     * application per scope.
     *
     * <p>Per design.md's Property 6: for any sequence of {@code N ≥ 1}
     * consecutive invocations of
     * {@link SpawnRelocator#relocateWorldSpawnCore(WorldSpawnContext)} on
     * the same world context (resp.
     * {@link SpawnRelocator#relocatePlayerCore(PlayerSpawnContext)} on the
     * same player context), at most the first invocation modifies the
     * shared spawn position (resp. the player's block position). After the
     * first invocation, the corresponding persistence flag
     * ({@code SpawnRelocatedFlag.isDone()} on the world side, the
     * {@code tensura_tno.spawn_relocated} NBT byte on the player side) is
     * {@code true} and never flips back to {@code false}, and every
     * subsequent invocation is a no-op on both the position and the
     * underlying side-effecting API ({@code setDefaultSpawnPos} /
     * {@code teleportTo}).
     *
     * <p>The trial follows the exact recipe spelled out in task 7.7:
     * <ol>
     *   <li>Build a happy-path world AND player context whose finder
     *       returns {@code Optional.of(witness)} — every guard satisfied
     *       so the FIRST call MUST rewrite the coordinate.</li>
     *   <li>Capture the initial position (which equals {@code origin} by
     *       construction).</li>
     *   <li>Loop {@code N} times calling the orchestration core. After
     *       iteration 1 the position must equal {@code witness}, the
     *       side-effect counter must equal {@code 1}, and the persistence
     *       flag must be {@code true}. After every subsequent iteration
     *       all three must remain unchanged: position still
     *       {@code witness}, counter still {@code 1}, flag still
     *       {@code true} (i.e. it never flipped back to {@code false}).</li>
     * </ol>
     *
     * <p>Why the post-conditions are strong enough to pin down
     * idempotence:
     * <ul>
     *   <li>Asserting {@code setDefaultSpawnPosCalls == 1}
     *       (resp. {@code teleportCalls == 1}) on every iteration —
     *       including the very last one — catches any code path that
     *       would invoke the side-effecting API a second time, even if it
     *       happens to write the same value (which would still be a
     *       semantic regression: re-acquiring chunks, re-sending packets,
     *       re-running expensive Vanilla side-effects).</li>
     *   <li>Asserting {@code flagDone}/{@code persistentFlag} on every
     *       iteration after iteration 1 catches any path that resets the
     *       persistence flag back to {@code false} (which would re-arm
     *       the at-most-once contract on the next server start /
     *       login).</li>
     *   <li>Choosing {@code witness != origin} (the canonical
     *       {@code origin.offset(64, 0, 0)} we use elsewhere) means a
     *       broken implementation that fails to short-circuit on
     *       subsequent calls would either (a) re-rewrite the position to
     *       {@code witness} (invisible — same value), but
     *       (b) re-bump the call counter (visible — caught by the
     *       {@code == 1} assertion).</li>
     * </ul>
     *
     * <p>Block-center note: on the player path the production code calls
     * {@code teleportTo(p.x + 0.5, p.y, p.z + 0.5)} and the stub floors
     * the continuous coordinates back through {@code BlockPos.containing}
     * — so for any integer {@code witness}, including negative
     * coordinates where {@code floor(p.x + 0.5) == p.x} still holds, the
     * observable {@code blockPosition()} is exactly {@code witness}. This
     * is the same equality used by Property 5 and lets us assert
     * {@code witness.equals(playerCtx.blockPosition())} without an
     * epsilon.
     *
     * <p><b>Validates: Requirements 1.4, 2.4, 3.3</b>
     */
    @Property(tries = 100)
    void relocationIsIdempotentAcrossInvocations(
            @ForAll @IntRange(min = -2048, max = 2048) int originX,
            @ForAll @IntRange(min = 0, max = 255) int originY,
            @ForAll @IntRange(min = -2048, max = 2048) int originZ,
            @ForAll @IntRange(min = 1, max = 16) int n) {

        BlockPos origin = new BlockPos(originX, originY, originZ);
        ResourceLocation hitBiome = ResourceLocation.fromNamespaceAndPath("tensura", "ancient_forest");
        ResourceLocation safeBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");
        Set<ResourceLocation> blacklist = Set.of(hitBiome);
        // Witness != origin so a broken implementation that re-runs the
        // rewrite on iteration 2 would observably bump the call counter
        // even though it would happen to write the same value.
        BlockPos witness = origin.offset(64, 0, 0);

        // ---- WORLD path ----------------------------------------------------
        TestWorldContext worldCtx = new TestWorldContext(origin, blacklist, hitBiome, safeBiome, witness);

        BlockPos worldInitial = worldCtx.getSharedSpawnPos();
        if (!origin.equals(worldInitial)) {
            throw new AssertionError(
                    "world spawn must start at origin before any invocation;"
                            + " initial=" + worldInitial + ", origin=" + origin);
        }
        if (worldCtx.flagDone) {
            throw new AssertionError(
                    "world flag must start false before any invocation; got true");
        }

        for (int i = 1; i <= n; i++) {
            SpawnRelocator.relocateWorldSpawnCore(worldCtx);

            // After iteration 1: position == witness, counter == 1, flag == true.
            // After every subsequent iteration: all three must remain
            // exactly the same — i.e. no second rewrite, no flag reset.
            if (!witness.equals(worldCtx.getSharedSpawnPos())) {
                throw new AssertionError(
                        "world spawn must equal finder output after invocation " + i
                                + " (of " + n + ");"
                                + " expected=" + witness
                                + ", actual=" + worldCtx.getSharedSpawnPos());
            }
            if (worldCtx.setDefaultSpawnPosCalls != 1) {
                throw new AssertionError(
                        "setDefaultSpawnPos must be invoked exactly once across " + n
                                + " calls; observed=" + worldCtx.setDefaultSpawnPosCalls
                                + " after invocation " + i);
            }
            if (!worldCtx.flagDone) {
                throw new AssertionError(
                        "world flag must be true after invocation " + i
                                + " and never flip back to false");
            }
        }

        // ---- PLAYER path ---------------------------------------------------
        TestPlayerContext playerCtx = new TestPlayerContext(origin, blacklist, hitBiome, safeBiome, witness);

        BlockPos playerInitial = playerCtx.blockPosition();
        if (!origin.equals(playerInitial)) {
            throw new AssertionError(
                    "player must start at origin before any invocation;"
                            + " initial=" + playerInitial + ", origin=" + origin);
        }
        if (playerCtx.persistentFlag) {
            throw new AssertionError(
                    "player persistent flag must start false before any invocation; got true");
        }

        for (int i = 1; i <= n; i++) {
            SpawnRelocator.relocatePlayerCore(playerCtx);

            if (!witness.equals(playerCtx.blockPosition())) {
                throw new AssertionError(
                        "player block position must equal finder output after invocation "
                                + i + " (of " + n + ");"
                                + " expected=" + witness
                                + ", actual=" + playerCtx.blockPosition());
            }
            if (playerCtx.teleportCalls != 1) {
                throw new AssertionError(
                        "teleportTo must be invoked exactly once across " + n
                                + " calls; observed=" + playerCtx.teleportCalls
                                + " after invocation " + i);
            }
            if (!playerCtx.persistentFlag) {
                throw new AssertionError(
                        "player persistent flag must be true after invocation "
                                + i + " and never flip back to false");
            }
        }
    }

    // ----- Task 7.8: Example tests ------------------------------------------

    /**
     * Example test: when the Tensura core mod is not loaded, both
     * orchestration cores must short-circuit before touching the spawn
     * coordinate, emit a single {@code DEBUG} log line whose message
     * contains {@code "skip: tensura mod not loaded"}, and leave the
     * world / player position exactly equal to the original.
     *
     * <p>Why a property test would be overkill here: the production code's
     * "Tensura missing" branch is a literal early-return after a single
     * static {@code if (!ctx.isTensuraLoaded())} check; flipping the
     * single boolean exercises the whole branch faithfully, and there is
     * no input-space variation to cover. The two narrow ports
     * ({@link WorldSpawnContext}, {@link PlayerSpawnContext}) are pinned
     * to a baseline whose <em>only</em> guard failure is
     * {@code isTensuraLoaded() == false}; every other guard is satisfied
     * so a regression that ignored the Tensura flag would visibly rewrite
     * the position and / or invoke the side-effecting API.
     *
     * <p>Log-capture pattern matches
     * {@code BiomeBlacklistConfigTest.CapturingAppender}: the production
     * code routes through {@code com.mojang.logging.LogUtils.getLogger()}
     * which is bound to Log4j2 in this project via
     * {@code log4j-slf4j2-impl}, so attaching to the named Log4j2 logger
     * for {@link SpawnRelocator} captures every event the production code
     * emits regardless of inherited level thresholds.
     *
     * <p><b>Covers Requirements 5.1, 5.2.</b>
     */
    @Test
    void example_tensuraMissingShortCircuits() {
        BlockPos origin = new BlockPos(100, 64, -200);
        ResourceLocation hitBiome = ResourceLocation.fromNamespaceAndPath("tensura", "ancient_forest");
        ResourceLocation safeBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");
        Set<ResourceLocation> blacklist = Set.of(hitBiome);
        BlockPos witness = origin.offset(64, 0, 0);

        // ---- WORLD path ----------------------------------------------------
        TestWorldContext worldCtx = new TestWorldContext(origin, blacklist, hitBiome, safeBiome, witness);
        worldCtx.tensuraLoaded = false;

        CapturingAppender appender = CapturingAppender.attach(SpawnRelocator.class);
        try {
            SpawnRelocator.relocateWorldSpawnCore(worldCtx);

            // Position must NOT change.
            assertEquals(origin, worldCtx.getSharedSpawnPos(),
                "world spawn must remain at origin when Tensura is missing");
            assertEquals(0, worldCtx.setDefaultSpawnPosCalls,
                "setDefaultSpawnPos must NOT be invoked when Tensura is missing");
            // markFlagDone must NOT be invoked: the Tensura-missing guard
            // sits before the try/finally that marks the flag, so flipping
            // it must leave the persistence flag untouched.
            assertEquals(false, worldCtx.flagDone,
                "world flag must NOT be marked done when Tensura is missing"
                    + " (the guard sits before the try/finally that marks it)");

            // Exactly one DEBUG event with the expected message.
            List<LogEvent> debugEvents = appender.events().stream()
                .filter(e -> e.getLevel() == org.apache.logging.log4j.Level.DEBUG)
                .collect(Collectors.toList());
            assertEquals(1, debugEvents.size(),
                "world path must emit exactly one DEBUG event when Tensura is missing; events="
                    + debugEvents);
            String msg = debugEvents.get(0).getMessage().getFormattedMessage();
            assertTrue(msg.contains("skip: tensura mod not loaded"),
                "DEBUG message must contain 'skip: tensura mod not loaded'; was=" + msg);
            assertTrue(msg.contains(SpawnRelocator.LOG_PREFIX),
                "DEBUG message must contain the SpawnRelocator log prefix; was=" + msg);
        } finally {
            appender.detach();
        }

        // ---- PLAYER path ---------------------------------------------------
        TestPlayerContext playerCtx = new TestPlayerContext(origin, blacklist, hitBiome, safeBiome, witness);
        playerCtx.tensuraLoaded = false;

        CapturingAppender appender2 = CapturingAppender.attach(SpawnRelocator.class);
        try {
            SpawnRelocator.relocatePlayerCore(playerCtx);

            assertEquals(origin, playerCtx.blockPosition(),
                "player position must remain at origin when Tensura is missing");
            assertEquals(0, playerCtx.teleportCalls,
                "teleportTo must NOT be invoked when Tensura is missing");
            assertEquals(false, playerCtx.persistentFlag,
                "player persistent flag must NOT be set when Tensura is missing"
                    + " (the guard sits before the try/finally that sets it)");

            List<LogEvent> debugEvents = appender2.events().stream()
                .filter(e -> e.getLevel() == org.apache.logging.log4j.Level.DEBUG)
                .collect(Collectors.toList());
            assertEquals(1, debugEvents.size(),
                "player path must emit exactly one DEBUG event when Tensura is missing; events="
                    + debugEvents);
            String msg = debugEvents.get(0).getMessage().getFormattedMessage();
            assertTrue(msg.contains("skip: tensura mod not loaded"),
                "DEBUG message must contain 'skip: tensura mod not loaded'; was=" + msg);
            assertTrue(msg.contains(SpawnRelocator.LOG_PREFIX),
                "DEBUG message must contain the SpawnRelocator log prefix; was=" + msg);
        } finally {
            appender2.detach();
        }
    }

    /**
     * Example test: the public static surface of {@link SpawnRelocator} is
     * frozen to exactly two NeoForge event listeners — the
     * {@link ServerStartedEvent} entry and the
     * {@link PlayerEvent.PlayerLoggedInEvent} entry. Every other static
     * helper on the class must be package-private (or stricter) so that
     * downstream code cannot accidentally hook into the orchestration
     * pipeline through anything other than the documented event
     * subscriptions.
     *
     * <p>Why a reflection check belongs here: requirements 9.1 / 9.2 / 10.1
     * collectively mandate that the only NeoForge entry points are the
     * two listeners (no per-tick handlers, no extra public hooks); 3.2 /
     * 7.3 require the player listener to be specifically the
     * {@code PlayerLoggedInEvent} (not, e.g., {@code PlayerRespawnEvent}
     * or a tick event). A reflective surface check is the most direct way
     * to encode "no future contributor accidentally adds a third public
     * static method that NeoForge could pick up".
     *
     * <p>The test enumerates every declared method (including synthetic
     * bridge methods generated by the compiler), filters to
     * {@code public static}, and matches each one against a small allow
     * list keyed by name + parameter types. Any unexpected method causes
     * a hard failure with a diagnostic listing every offending signature.
     *
     * <p><b>Covers Requirements 3.2, 7.3, 9.1, 9.2, 10.1.</b>
     */
    @Test
    void example_publicListenerSurface() {
        // Allowed (name, parameter-type) pairs. Both signatures are spelled
        // out in design.md's class diagram and are required by the spec to
        // be the ONLY public static methods on SpawnRelocator.
        record Allowed(String name, Class<?>[] params) {}
        List<Allowed> allowed = List.of(
            new Allowed("onServerStarted", new Class<?>[] { ServerStartedEvent.class }),
            new Allowed("onPlayerLoggedIn", new Class<?>[] { PlayerEvent.PlayerLoggedInEvent.class })
        );

        List<Method> publicStatics = new ArrayList<>();
        for (Method m : SpawnRelocator.class.getDeclaredMethods()) {
            int mods = m.getModifiers();
            // Skip compiler-synthesized bridge methods so we are only
            // looking at the real source-level public static surface.
            if (m.isSynthetic() || m.isBridge()) {
                continue;
            }
            if (Modifier.isPublic(mods) && Modifier.isStatic(mods)) {
                publicStatics.add(m);
            }
        }

        // Match every public static against the allow list.
        List<Method> unexpected = new ArrayList<>();
        for (Method m : publicStatics) {
            boolean matched = allowed.stream().anyMatch(a ->
                a.name().equals(m.getName())
                    && java.util.Arrays.equals(a.params(), m.getParameterTypes()));
            if (!matched) {
                unexpected.add(m);
            }
        }
        assertTrue(unexpected.isEmpty(),
            "SpawnRelocator must expose ONLY the two NeoForge event listeners as public static;"
                + " unexpected=" + unexpected.stream()
                    .map(Method::toGenericString)
                    .collect(Collectors.toList())
                + ", observedAll=" + publicStatics.stream()
                    .map(Method::toGenericString)
                    .collect(Collectors.toList()));

        // Both required listeners must actually be present.
        for (Allowed a : allowed) {
            boolean found = publicStatics.stream().anyMatch(m ->
                a.name().equals(m.getName())
                    && java.util.Arrays.equals(a.params(), m.getParameterTypes()));
            assertTrue(found,
                "SpawnRelocator must expose " + a.name() + "("
                    + java.util.Arrays.stream(a.params())
                        .map(Class::getName).collect(Collectors.joining(", "))
                    + ") as a public static method; observed="
                    + publicStatics.stream()
                        .map(Method::toGenericString)
                        .collect(Collectors.toList()));
        }

        // And BOTH return void: NeoForge event listeners are sinks.
        for (Method m : publicStatics) {
            assertEquals(void.class, m.getReturnType(),
                "public static method " + m.getName() + " must return void;"
                    + " was=" + m.getReturnType().getName());
        }
    }

    /**
     * Example test: on a happy-path world relocation, the {@code INFO} log
     * line conforms to design.md's documented format:
     * <ul>
     *   <li>starts with the {@code [TensuraTNO][SpawnRelocator]} prefix
     *       (Requirement 8.1);</li>
     *   <li>contains the formatted original {@link BlockPos} (the literal
     *       {@code "(x, y, z)"} returned by the production
     *       {@code formatPos} helper);</li>
     *   <li>contains the formatted destination {@link BlockPos};</li>
     *   <li>contains both {@code biome=...} markers — the original biome
     *       and the destination biome — so server operators can grep both
     *       sides of the relocation from a single line.</li>
     * </ul>
     *
     * <p>The trial reuses the same {@link TestWorldContext} happy-path
     * baseline as Property 5: every guard satisfied, finder pinned to
     * {@code () -> Optional.of(witness)}. Production code branches into
     * the {@code LOGGER.info(...)} call exactly once; the appender then
     * captures the formatted message and the assertions check each
     * required substring.
     *
     * <p><b>Covers Requirement 8.1.</b>
     */
    @Test
    void example_worldLogFormat() {
        BlockPos origin = new BlockPos(123, 64, -456);
        BlockPos witness = new BlockPos(789, 70, 1011);
        ResourceLocation hitBiome = ResourceLocation.fromNamespaceAndPath("tensura", "ancient_forest");
        ResourceLocation safeBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");
        Set<ResourceLocation> blacklist = Set.of(hitBiome);

        TestWorldContext worldCtx = new TestWorldContext(origin, blacklist, hitBiome, safeBiome, witness);
        worldCtx.finder = o -> Optional.of(witness);

        CapturingAppender appender = CapturingAppender.attach(SpawnRelocator.class);
        try {
            SpawnRelocator.relocateWorldSpawnCore(worldCtx);

            // Sanity: side-effect happened (otherwise we would not have
            // reached the LOGGER.info call we are testing).
            assertEquals(witness, worldCtx.getSharedSpawnPos(),
                "world spawn must equal witness on hit path");

            List<LogEvent> infos = appender.events().stream()
                .filter(e -> e.getLevel() == org.apache.logging.log4j.Level.INFO)
                .collect(Collectors.toList());
            assertEquals(1, infos.size(),
                "happy-path world relocation must emit exactly one INFO event; events=" + infos);

            String msg = infos.get(0).getMessage().getFormattedMessage();
            assertTrue(msg.contains(SpawnRelocator.LOG_PREFIX),
                "INFO message must contain '" + SpawnRelocator.LOG_PREFIX + "' prefix; was=" + msg);
            // formatPos uses "(x, y, z)" so we re-construct the exact
            // substring we expect.
            String originStr = "(" + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")";
            String witnessStr = "(" + witness.getX() + ", " + witness.getY() + ", " + witness.getZ() + ")";
            assertTrue(msg.contains(originStr),
                "INFO message must contain original position " + originStr + "; was=" + msg);
            assertTrue(msg.contains(witnessStr),
                "INFO message must contain new position " + witnessStr + "; was=" + msg);
            // Both biome markers must be present (origin -> hitBiome,
            // witness -> safeBiome via the stub's biomeIdAt mapping).
            assertTrue(msg.contains("biome=" + hitBiome),
                "INFO message must contain original biome=" + hitBiome + "; was=" + msg);
            assertTrue(msg.contains("biome=" + safeBiome),
                "INFO message must contain new biome=" + safeBiome + "; was=" + msg);
            // And the literal "biome=" marker must appear at least twice
            // (once per side of the relocation).
            int firstMarker = msg.indexOf("biome=");
            int secondMarker = msg.indexOf("biome=", firstMarker + 1);
            assertTrue(firstMarker >= 0 && secondMarker > firstMarker,
                "INFO message must contain TWO 'biome=' markers (one per side); was=" + msg);
        } finally {
            appender.detach();
        }
    }

    /**
     * Example test: on a happy-path player relocation, the {@code INFO}
     * log line conforms to design.md's documented format and additionally
     * includes the player's name (Requirement 8.2):
     * <ul>
     *   <li>starts with the {@code [TensuraTNO][SpawnRelocator]} prefix;</li>
     *   <li>contains {@link PlayerSpawnContext#playerName()};</li>
     *   <li>contains the formatted original / destination {@link BlockPos};</li>
     *   <li>contains both {@code biome=...} markers.</li>
     * </ul>
     *
     * <p>Mirrors {@link #example_worldLogFormat()} on the player path.
     * The {@link TestPlayerContext} stub returns {@code "TestPlayer"} for
     * {@link PlayerSpawnContext#playerName()}, so the assertion looks for
     * that exact substring in the captured message.
     *
     * <p><b>Covers Requirement 8.2.</b>
     */
    @Test
    void example_playerLogFormat() {
        BlockPos origin = new BlockPos(-321, 72, 654);
        BlockPos witness = new BlockPos(987, 80, -1011);
        ResourceLocation hitBiome = ResourceLocation.fromNamespaceAndPath("tensura", "desert_of_death");
        ResourceLocation safeBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "forest");
        Set<ResourceLocation> blacklist = Set.of(hitBiome);

        TestPlayerContext playerCtx = new TestPlayerContext(origin, blacklist, hitBiome, safeBiome, witness);
        playerCtx.finder = o -> Optional.of(witness);

        CapturingAppender appender = CapturingAppender.attach(SpawnRelocator.class);
        try {
            SpawnRelocator.relocatePlayerCore(playerCtx);

            assertEquals(witness, playerCtx.blockPosition(),
                "player position must equal witness on hit path"
                    + " (block-center +0.5 floors back to integer block)");

            List<LogEvent> infos = appender.events().stream()
                .filter(e -> e.getLevel() == org.apache.logging.log4j.Level.INFO)
                .collect(Collectors.toList());
            assertEquals(1, infos.size(),
                "happy-path player relocation must emit exactly one INFO event; events=" + infos);

            String msg = infos.get(0).getMessage().getFormattedMessage();
            assertTrue(msg.contains(SpawnRelocator.LOG_PREFIX),
                "INFO message must contain '" + SpawnRelocator.LOG_PREFIX + "' prefix; was=" + msg);
            assertTrue(msg.contains(playerCtx.playerName()),
                "INFO message must contain player name '" + playerCtx.playerName() + "'; was=" + msg);

            String originStr = "(" + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")";
            String witnessStr = "(" + witness.getX() + ", " + witness.getY() + ", " + witness.getZ() + ")";
            assertTrue(msg.contains(originStr),
                "INFO message must contain original position " + originStr + "; was=" + msg);
            assertTrue(msg.contains(witnessStr),
                "INFO message must contain new position " + witnessStr + "; was=" + msg);

            assertTrue(msg.contains("biome=" + hitBiome),
                "INFO message must contain original biome=" + hitBiome + "; was=" + msg);
            assertTrue(msg.contains("biome=" + safeBiome),
                "INFO message must contain new biome=" + safeBiome + "; was=" + msg);
            int firstMarker = msg.indexOf("biome=");
            int secondMarker = msg.indexOf("biome=", firstMarker + 1);
            assertTrue(firstMarker >= 0 && secondMarker > firstMarker,
                "INFO message must contain TWO 'biome=' markers (one per side); was=" + msg);
        } finally {
            appender.detach();
        }
    }

    /**
     * Programmatic Log4j2 list-appender. Mirrors
     * {@code BiomeBlacklistConfigTest.CapturingAppender}: the production
     * code emits via {@code com.mojang.logging.LogUtils.getLogger()},
     * which is bound to Log4j2 in this project through
     * {@code log4j-slf4j2-impl}; events therefore reach the Log4j2 logger
     * named after the caller class.
     */
    private static final class CapturingAppender extends AbstractAppender {

        private final List<LogEvent> events = new CopyOnWriteArrayList<>();
        private final org.apache.logging.log4j.core.Logger target;

        private CapturingAppender(String name, org.apache.logging.log4j.core.Logger target) {
            super(name, null, PatternLayout.createDefaultLayout(), false,
                org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
            this.target = target;
        }

        static CapturingAppender attach(Class<?> clazz) {
            String loggerName = clazz.getName();
            // Force the logger to ALL so the test sees DEBUG events even
            // though the project's runtime configuration may have a
            // higher inherited threshold.
            Configurator.setLevel(loggerName, org.apache.logging.log4j.Level.ALL);
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

    /**
     * In-test mutable {@link WorldSpawnContext} stub. Every guard input is
     * exposed as a plain field so the property test can flip exactly one
     * blocker per trial. Defaults form the "happy path": feature enabled,
     * Tensura loaded, overworld dimension, flag not yet done, origin biome
     * is in the blacklist, finder returns the {@code witness} hit.
     *
     * <p>Counts {@code setDefaultSpawnPos} / {@code markFlagDone} calls so
     * the property can also assert "no write happened" beyond just "the
     * recorded position equals the original".
     */
    private static final class TestWorldContext implements WorldSpawnContext {

        boolean enabled = true;
        boolean tensuraLoaded = true;
        boolean overworld = true;
        boolean flagDone = false;
        ResourceLocation biomeAtOrigin;
        final ResourceLocation safeBiome;
        Function<BlockPos, Optional<BlockPos>> finder;

        private BlockPos sharedSpawnPos;
        private final BlockPos originalSpawnPos;
        private final Set<ResourceLocation> blacklist;
        int setDefaultSpawnPosCalls;

        TestWorldContext(BlockPos origin, Set<ResourceLocation> blacklist,
                         ResourceLocation hitBiome, ResourceLocation safeBiome, BlockPos witness) {
            this.originalSpawnPos = origin;
            this.sharedSpawnPos = origin;
            this.blacklist = blacklist;
            this.biomeAtOrigin = hitBiome;
            this.safeBiome = safeBiome;
            this.finder = o -> Optional.of(witness);
        }

        @Override public boolean isEnabled() { return enabled; }
        @Override public boolean isTensuraLoaded() { return tensuraLoaded; }
        @Override public boolean isOverworld() { return overworld; }
        @Override public ResourceLocation dimensionId() {
            return overworld
                    ? Level.OVERWORLD.location()
                    : ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
        }
        @Override public BlockPos getSharedSpawnPos() { return sharedSpawnPos; }
        @Override public void setDefaultSpawnPos(BlockPos pos, float angle) {
            sharedSpawnPos = pos;
            setDefaultSpawnPosCalls++;
        }
        @Override public ResourceLocation biomeIdAt(BlockPos pos) {
            // Origin -> configured (default: hit) biome; every other position
            // resolves to the safe biome so the finder's witness is "safe".
            return pos.equals(originalSpawnPos) ? biomeAtOrigin : safeBiome;
        }
        @Override public Set<ResourceLocation> blacklist() { return blacklist; }
        @Override public boolean isFlagDone() { return flagDone; }
        @Override public void markFlagDone() { flagDone = true; }
        @Override public Optional<BlockPos> findSafeSpawn(BlockPos origin) {
            return finder.apply(origin);
        }
        @Override public int searchRadius() { return 1024; }
    }

    /**
     * In-test mutable {@link PlayerSpawnContext} stub. Mirrors
     * {@link TestWorldContext} for the player path.
     *
     * <p>Defaults form the "happy path": feature enabled, Tensura loaded,
     * overworld dimension, persistent NBT flag false, no respawn position,
     * origin biome in the blacklist, finder returns the {@code witness}
     * hit. The property test flips exactly one of these to encode the
     * blocker under test.
     */
    private static final class TestPlayerContext implements PlayerSpawnContext {

        boolean enabled = true;
        boolean tensuraLoaded = true;
        boolean overworld = true;
        boolean persistentFlag = false;
        boolean hasRespawn = false;
        ResourceLocation biomeAtOrigin;
        final ResourceLocation safeBiome;
        Function<BlockPos, Optional<BlockPos>> finder;

        private BlockPos blockPosition;
        private final BlockPos originalPosition;
        private final Set<ResourceLocation> blacklist;
        int teleportCalls;

        TestPlayerContext(BlockPos origin, Set<ResourceLocation> blacklist,
                          ResourceLocation hitBiome, ResourceLocation safeBiome, BlockPos witness) {
            this.originalPosition = origin;
            this.blockPosition = origin;
            this.blacklist = blacklist;
            this.biomeAtOrigin = hitBiome;
            this.safeBiome = safeBiome;
            this.finder = o -> Optional.of(witness);
        }

        @Override public boolean isEnabled() { return enabled; }
        @Override public boolean isTensuraLoaded() { return tensuraLoaded; }
        @Override public boolean isOverworld() { return overworld; }
        @Override public ResourceLocation dimensionId() {
            return overworld
                    ? Level.OVERWORLD.location()
                    : ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
        }
        @Override public boolean isPersistentFlagSet() { return persistentFlag; }
        @Override public void setPersistentFlag() { persistentFlag = true; }
        @Override public boolean hasRespawnPosition() { return hasRespawn; }
        @Override public BlockPos blockPosition() { return blockPosition; }
        @Override public void teleportTo(double x, double y, double z) {
            // BlockPos.containing({integer}.5, y, {integer}.5) floors to
            // the integer block, mirroring Vanilla blockPosition().
            blockPosition = BlockPos.containing(x, y, z);
            teleportCalls++;
        }
        @Override public ResourceLocation biomeIdAt(BlockPos pos) {
            return pos.equals(originalPosition) ? biomeAtOrigin : safeBiome;
        }
        @Override public Set<ResourceLocation> blacklist() { return blacklist; }
        @Override public Optional<BlockPos> findSafeSpawn(BlockPos origin) {
            return finder.apply(origin);
        }
        @Override public int searchRadius() { return 1024; }
        @Override public String playerName() { return "TestPlayer"; }
    }
}

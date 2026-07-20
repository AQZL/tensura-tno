package com.tensura_tno.world.spawn.support;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.tensura_tno.world.spawn.BiomeLookup;
import com.tensura_tno.world.spawn.SurfaceLookup;

/**
 * Test-only stand-in for the slice of {@code net.minecraft.server.level.ServerLevel}
 * that the spawn-relocation pipeline interacts with.
 *
 * <p>The design.md "Stub 设计" section calls out that {@code ServerLevel}
 * cannot be instantiated in a unit-test JVM, so this stub deliberately does
 * <strong>not</strong> extend {@code ServerLevel}: it is a plain holder that
 * exposes the same surface area as the (package-private) test entry points
 * on {@code SpawnRelocator}. Production code keeps using the real
 * {@code ServerLevel}; tests pass a {@link ServerLevelStub} into the
 * narrow-interface overloads instead.
 *
 * <p>State held:
 * <ul>
 *   <li>The {@link ResourceKey} of the level's dimension (defaults to
 *       {@link Level#OVERWORLD}).</li>
 *   <li>The current "shared spawn position" plus a tracker for how many
 *       times {@code setDefaultSpawnPos} has been called and what the last
 *       call's arguments were (lets tests assert that
 *       {@code SpawnRelocator} did or did not write back).</li>
 *   <li>A {@link BiomeWorldStub} that supplies all biome / surface data;
 *       the stub builds {@link BiomeLookup} / {@link SurfaceLookup}
 *       adapters from it on demand.</li>
 *   <li>An optional {@link SpawnRelocatedFlagStub} representing the
 *       per-save SavedData (the production code looks it up via
 *       {@code SpawnRelocatedFlag.get(level)}; tests pass the stub
 *       directly into the narrow-interface overloads).</li>
 * </ul>
 *
 * <p>This class lives under {@code src/test/java} and MUST NOT be referenced
 * by production code.
 */
public final class ServerLevelStub {

    private final BiomeWorldStub world;
    private ResourceKey<Level> dimension;
    private BlockPos sharedSpawnPos;
    private float sharedSpawnAngle;
    private final SpawnRelocatedFlagStub relocatedFlag;

    private int setDefaultSpawnPosCallCount;
    private BlockPos lastSetDefaultSpawnPos;
    private float lastSetDefaultSpawnAngle;

    private ServerLevelStub(Builder builder) {
        this.world = Objects.requireNonNull(builder.world, "world");
        this.dimension = Objects.requireNonNull(builder.dimension, "dimension");
        this.sharedSpawnPos = Objects.requireNonNull(builder.sharedSpawnPos, "sharedSpawnPos");
        this.sharedSpawnAngle = builder.sharedSpawnAngle;
        this.relocatedFlag = builder.relocatedFlag != null
                ? builder.relocatedFlag
                : new SpawnRelocatedFlagStub();
    }

    // ----- ServerLevel-shaped accessors -------------------------------------

    /**
     * Mirror of {@code ServerLevel#getSharedSpawnPos()}.
     */
    public BlockPos getSharedSpawnPos() {
        return sharedSpawnPos;
    }

    /**
     * Mirror of {@code ServerLevel#getSharedSpawnAngle()} for tests that need
     * to verify the angle round-tripped through {@code setDefaultSpawnPos}.
     */
    public float getSharedSpawnAngle() {
        return sharedSpawnAngle;
    }

    /**
     * Mirror of {@code ServerLevel#setDefaultSpawnPos(BlockPos, float)}.
     * Records the call so tests can assert how many times (and with what
     * arguments) {@code SpawnRelocator} wrote back.
     */
    public void setDefaultSpawnPos(BlockPos pos, float angle) {
        Objects.requireNonNull(pos, "pos");
        this.sharedSpawnPos = pos;
        this.sharedSpawnAngle = angle;
        this.setDefaultSpawnPosCallCount++;
        this.lastSetDefaultSpawnPos = pos;
        this.lastSetDefaultSpawnAngle = angle;
    }

    /**
     * Mirror of {@code ServerLevel#dimension()}.
     */
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /**
     * Test-only: change the dimension key (e.g. to verify the
     * "non-overworld -> skip" guard).
     */
    public void setDimension(ResourceKey<Level> dimension) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
    }

    /**
     * Mirror of {@code ServerLevel#getBiome(BlockPos).unwrapKey().get().location()}
     * collapsed into a single call.
     */
    public ResourceLocation getBiome(BlockPos pos) {
        return world.biomeAt(pos.getX(), pos.getZ());
    }

    /**
     * Mirror of {@code ServerLevel#getHeight(Heightmap.Types, int, int)}
     * for the {@code MOTION_BLOCKING_NO_LEAVES} heightmap that the
     * production code consults.
     */
    public int getHeight(int x, int z) {
        return world.heightAt(x, z);
    }

    /** Mirror of {@code ServerLevel#getMinBuildHeight()}. */
    public int getMinBuildHeight() {
        return world.minBuildHeight();
    }

    /** Mirror of {@code ServerLevel#getMaxBuildHeight()}. */
    public int getMaxBuildHeight() {
        return world.maxBuildHeight();
    }

    // ----- Stub-only accessors ---------------------------------------------

    /**
     * Returns the {@link SpawnRelocatedFlagStub} representing this level's
     * per-save SavedData record. Production code reaches the equivalent
     * value via {@code SpawnRelocatedFlag.get(level)} — tests pass this
     * stub directly into the narrow-interface overload that
     * {@code SpawnRelocator} exposes for testing.
     */
    public SpawnRelocatedFlagStub getRelocatedFlag() {
        return relocatedFlag;
    }

    /** Returns the underlying virtual world powering biome / surface queries. */
    public BiomeWorldStub world() {
        return world;
    }

    /** Convenience: build a {@link BiomeLookup} from the underlying stub world. */
    public BiomeLookup asBiomeLookup() {
        return world.asBiomeLookup();
    }

    /** Convenience: build a {@link SurfaceLookup} from the underlying stub world. */
    public SurfaceLookup asSurfaceLookup() {
        return world.asSurfaceLookup();
    }

    /**
     * @return how many times {@link #setDefaultSpawnPos(BlockPos, float)}
     *         has been called on this stub. Useful for asserting "exactly
     *         one effective application" (idempotence) properties.
     */
    public int getSetDefaultSpawnPosCallCount() {
        return setDefaultSpawnPosCallCount;
    }

    /**
     * @return the {@link BlockPos} passed to the most recent
     *         {@link #setDefaultSpawnPos(BlockPos, float)} call, or
     *         {@code null} if the method has never been invoked.
     */
    public BlockPos getLastSetDefaultSpawnPos() {
        return lastSetDefaultSpawnPos;
    }

    /**
     * @return the angle passed to the most recent
     *         {@link #setDefaultSpawnPos(BlockPos, float)} call, or
     *         {@code 0.0F} if the method has never been invoked.
     */
    public float getLastSetDefaultSpawnAngle() {
        return lastSetDefaultSpawnAngle;
    }

    // ----- Builder ---------------------------------------------------------

    /**
     * Returns a fresh {@link Builder} pre-populated with sensible defaults
     * (overworld dimension, shared spawn at {@code (0, 64, 0)}, default
     * angle {@code 0.0F}, freshly created {@link SpawnRelocatedFlagStub}).
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link ServerLevelStub}. */
    public static final class Builder {

        private BiomeWorldStub world = BiomeWorldStub.builder().build();
        private ResourceKey<Level> dimension = Level.OVERWORLD;
        private BlockPos sharedSpawnPos = new BlockPos(0, 64, 0);
        private float sharedSpawnAngle = 0.0F;
        private SpawnRelocatedFlagStub relocatedFlag;

        private Builder() {
        }

        public Builder world(BiomeWorldStub world) {
            this.world = Objects.requireNonNull(world, "world");
            return this;
        }

        public Builder dimension(ResourceKey<Level> dimension) {
            this.dimension = Objects.requireNonNull(dimension, "dimension");
            return this;
        }

        public Builder sharedSpawnPos(BlockPos pos) {
            this.sharedSpawnPos = Objects.requireNonNull(pos, "pos");
            return this;
        }

        public Builder sharedSpawnAngle(float angle) {
            this.sharedSpawnAngle = angle;
            return this;
        }

        /** Override the per-save flag (e.g. to start with {@code done = true}). */
        public Builder relocatedFlag(SpawnRelocatedFlagStub flag) {
            this.relocatedFlag = Objects.requireNonNull(flag, "flag");
            return this;
        }

        public ServerLevelStub build() {
            return new ServerLevelStub(this);
        }
    }

    /**
     * Test-only mirror of {@link com.tensura_tno.world.spawn.SpawnRelocatedFlag}.
     *
     * <p>The real flag class extends {@code SavedData} and is registered with
     * {@code DimensionDataStorage}, both of which require a live server level.
     * Since this stub package never instantiates a live level, we mirror the
     * flag's <em>observable</em> contract with a small data-only class. Tests
     * pass this stub through {@code SpawnRelocator}'s narrow-interface
     * overloads (which accept any {@code Object} that exposes
     * {@code isDone() / markDone()}) instead of the live SavedData.
     */
    public static final class SpawnRelocatedFlagStub {

        private boolean done;
        private boolean dirty;

        public SpawnRelocatedFlagStub() {
            this(false);
        }

        public SpawnRelocatedFlagStub(boolean initiallyDone) {
            this.done = initiallyDone;
        }

        /** Mirrors {@code SpawnRelocatedFlag#isDone()}. */
        public boolean isDone() {
            return done;
        }

        /**
         * Mirrors {@code SpawnRelocatedFlag#markDone()}: flips the done flag
         * to {@code true} and records that the underlying record is dirty,
         * just as the real implementation calls {@code setDirty()}.
         */
        public void markDone() {
            this.done = true;
            this.dirty = true;
        }

        /** Mirrors {@code SavedData#isDirty()} for assertion convenience. */
        public boolean isDirty() {
            return dirty;
        }
    }
}

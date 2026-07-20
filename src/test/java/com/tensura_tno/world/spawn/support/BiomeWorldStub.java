package com.tensura_tno.world.spawn.support;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import com.tensura_tno.world.spawn.BiomeLookup;
import com.tensura_tno.world.spawn.SurfaceLookup;

/**
 * Test-only functional virtual world that backs {@link ServerLevelStub} and
 * supplies adapters for the production code's
 * {@link com.tensura_tno.world.spawn.BiomeLookup} /
 * {@link com.tensura_tno.world.spawn.SurfaceLookup} narrow interfaces.
 *
 * <p>The stub is intentionally minimal: it captures three pure functions
 * that completely describe the slice of world data the spawn-relocation
 * pipeline needs:
 * <ul>
 *   <li>{@code biomeAt}: {@code (x, z) -> ResourceLocation} — biome id at
 *       horizontal coordinate, used by {@code BiomeLookup}.</li>
 *   <li>{@code heightAt}: {@code (x, z) -> int} — surface y at horizontal
 *       coordinate (matches the
 *       {@code Heightmap.Types.MOTION_BLOCKING_NO_LEAVES} contract used in
 *       production), used by {@code SurfaceLookup#surfaceAt}.</li>
 *   <li>{@code isStandable}: {@code BlockPos -> boolean} — whether a candidate
 *       feet-position has a solid block below and clearance above, used by
 *       {@code SurfaceLookup#isStandable}.</li>
 * </ul>
 *
 * <p>Sensible defaults are provided so most tests only override the function
 * they care about (e.g. the biome lookup) and accept "plains everywhere,
 * y=64, every position standable, world height [-64, 320)".
 *
 * <p>This class lives under {@code src/test/java} and MUST NOT be referenced
 * by production code. It only depends on Mojang public API and the project's
 * narrow interfaces.
 *
 * @see ServerLevelStub
 * @see BiomeLookup
 * @see SurfaceLookup
 */
public final class BiomeWorldStub {

    /** Default biome returned by {@link Builder} when no override is set. */
    public static final ResourceLocation DEFAULT_BIOME =
            ResourceLocation.fromNamespaceAndPath("minecraft", "plains");

    private final BiFunction<Integer, Integer, ResourceLocation> biomeAt;
    private final BiFunction<Integer, Integer, Integer> heightAt;
    private final Predicate<BlockPos> isStandable;
    private final int minBuildHeight;
    private final int maxBuildHeight;

    private BiomeWorldStub(Builder builder) {
        this.biomeAt = Objects.requireNonNull(builder.biomeAt, "biomeAt");
        this.heightAt = Objects.requireNonNull(builder.heightAt, "heightAt");
        this.isStandable = Objects.requireNonNull(builder.isStandable, "isStandable");
        this.minBuildHeight = builder.minBuildHeight;
        this.maxBuildHeight = builder.maxBuildHeight;
        if (this.minBuildHeight >= this.maxBuildHeight) {
            throw new IllegalArgumentException(
                    "minBuildHeight (" + this.minBuildHeight
                            + ") must be < maxBuildHeight (" + this.maxBuildHeight + ")");
        }
    }

    /**
     * Returns the biome at the given horizontal coordinate.
     *
     * @param x world-space x
     * @param z world-space z
     * @return biome resource location; never {@code null}
     */
    public ResourceLocation biomeAt(int x, int z) {
        ResourceLocation rl = biomeAt.apply(x, z);
        return Objects.requireNonNull(rl,
                "biomeAt function returned null at (" + x + "," + z + ")");
    }

    /**
     * Returns the surface y at the given horizontal coordinate (matches the
     * {@code Heightmap.Types.MOTION_BLOCKING_NO_LEAVES} contract used in
     * production).
     */
    public int heightAt(int x, int z) {
        return heightAt.apply(x, z);
    }

    /**
     * Returns whether {@code pos} is a valid "feet" position (solid block
     * below, clearance at and above).
     */
    public boolean isStandable(BlockPos pos) {
        return isStandable.test(pos);
    }

    /** Inclusive minimum build height of this virtual world. */
    public int minBuildHeight() {
        return minBuildHeight;
    }

    /** Exclusive maximum build height of this virtual world. */
    public int maxBuildHeight() {
        return maxBuildHeight;
    }

    /**
     * Returns a {@link BiomeLookup} adapter that resolves biome ids by
     * looking up {@code (pos.getX(), pos.getZ())} in this stub.
     */
    public BiomeLookup asBiomeLookup() {
        return pos -> biomeAt(pos.getX(), pos.getZ());
    }

    /**
     * Returns a {@link SurfaceLookup} adapter that delegates surface,
     * standability, and build-height queries to this stub.
     */
    public SurfaceLookup asSurfaceLookup() {
        return new SurfaceLookup() {
            @Override
            public BlockPos surfaceAt(int x, int z) {
                return new BlockPos(x, heightAt(x, z), z);
            }

            @Override
            public int minBuildHeight() {
                return BiomeWorldStub.this.minBuildHeight;
            }

            @Override
            public int maxBuildHeight() {
                return BiomeWorldStub.this.maxBuildHeight;
            }

            @Override
            public boolean isStandable(BlockPos pos) {
                return BiomeWorldStub.this.isStandable(pos);
            }
        };
    }

    /** Returns a fresh {@link Builder} pre-populated with sensible defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link BiomeWorldStub}. Defaults: plains biome
     * everywhere, surface y = 64, every position standable, build range
     * {@code [-64, 320)} (matches Vanilla 1.21 overworld).
     */
    public static final class Builder {

        private BiFunction<Integer, Integer, ResourceLocation> biomeAt = (x, z) -> DEFAULT_BIOME;
        private BiFunction<Integer, Integer, Integer> heightAt = (x, z) -> 64;
        private Predicate<BlockPos> isStandable = pos -> true;
        private int minBuildHeight = -64;
        private int maxBuildHeight = 320;

        private Builder() {
        }

        /** Override the {@code (x, z) -> biome id} function. */
        public Builder biomeAt(BiFunction<Integer, Integer, ResourceLocation> fn) {
            this.biomeAt = Objects.requireNonNull(fn, "fn");
            return this;
        }

        /** Convenience: every coordinate resolves to {@code biome}. */
        public Builder constantBiome(ResourceLocation biome) {
            Objects.requireNonNull(biome, "biome");
            this.biomeAt = (x, z) -> biome;
            return this;
        }

        /** Override the {@code (x, z) -> surface y} function. */
        public Builder heightAt(BiFunction<Integer, Integer, Integer> fn) {
            this.heightAt = Objects.requireNonNull(fn, "fn");
            return this;
        }

        /** Convenience: every coordinate has surface y = {@code y}. */
        public Builder constantHeight(int y) {
            this.heightAt = (x, z) -> y;
            return this;
        }

        /** Override the {@code BlockPos -> boolean} standability predicate. */
        public Builder isStandable(Predicate<BlockPos> predicate) {
            this.isStandable = Objects.requireNonNull(predicate, "predicate");
            return this;
        }

        /** Set the inclusive minimum build height. */
        public Builder minBuildHeight(int minBuildHeight) {
            this.minBuildHeight = minBuildHeight;
            return this;
        }

        /** Set the exclusive maximum build height. */
        public Builder maxBuildHeight(int maxBuildHeight) {
            this.maxBuildHeight = maxBuildHeight;
            return this;
        }

        public BiomeWorldStub build() {
            return new BiomeWorldStub(this);
        }
    }
}

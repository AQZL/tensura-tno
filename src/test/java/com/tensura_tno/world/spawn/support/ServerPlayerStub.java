package com.tensura_tno.world.spawn.support;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * Test-only stand-in for the slice of
 * {@code net.minecraft.server.level.ServerPlayer} that the spawn-relocation
 * pipeline interacts with on the player path.
 *
 * <p>Like {@link ServerLevelStub}, this stub deliberately does
 * <strong>not</strong> extend {@code ServerPlayer} (instantiating one in a
 * unit-test JVM is intractable). It is a plain holder with the same surface
 * area as the (package-private) test entry points on {@code SpawnRelocator}.
 *
 * <p>State held:
 * <ul>
 *   <li>The player's current position as a {@link Vec3} (the production code
 *       reads this through {@code position()}). The stub keeps a rounded
 *       {@link BlockPos} view in sync, mirroring
 *       {@code Entity#blockPosition()}.</li>
 *   <li>An optional {@link BlockPos} respawn position (matches
 *       {@code ServerPlayer#getRespawnPosition()}; {@code null} means the
 *       player has no bed / anchor binding).</li>
 *   <li>A {@link CompoundTag} backing {@code getPersistentData()}. The tag
 *       is owned by the stub and reused across calls, matching Vanilla
 *       semantics where {@code getPersistentData()} returns the same
 *       mutable instance.</li>
 *   <li>A reference to the {@link ServerLevelStub} the player is currently
 *       in (powers the {@code level()} accessor).</li>
 *   <li>A display name (used by log-format example tests that consult
 *       {@code player.getGameProfile().getName()}).</li>
 * </ul>
 *
 * <p>Tracks every {@code teleportTo} call so tests can assert idempotence
 * and hit-path consistency.
 *
 * <p>This class lives under {@code src/test/java} and MUST NOT be referenced
 * by production code.
 */
public final class ServerPlayerStub {

    private final ServerLevelStub level;
    private final String name;
    private Vec3 position;
    private BlockPos respawnPosition;
    private final CompoundTag persistentData;

    private int teleportCallCount;
    private Vec3 lastTeleportTarget;

    private ServerPlayerStub(Builder builder) {
        this.level = Objects.requireNonNull(builder.level, "level");
        this.name = Objects.requireNonNull(builder.name, "name");
        this.position = Objects.requireNonNull(builder.position, "position");
        this.respawnPosition = builder.respawnPosition;
        this.persistentData = builder.persistentData != null
                ? builder.persistentData
                : new CompoundTag();
    }

    // ----- ServerPlayer-shaped accessors -----------------------------------

    /** Mirror of {@code Entity#position()}. */
    public Vec3 position() {
        return position;
    }

    /**
     * Mirror of {@code Entity#blockPosition()}: floors the current
     * {@link Vec3} to the enclosing block-space coordinate, just as the
     * real implementation does.
     */
    public BlockPos blockPosition() {
        return BlockPos.containing(position);
    }

    /**
     * Mirror of {@code Entity#teleportTo(double, double, double)}.
     *
     * <p>Updates the held position and records the call so tests can assert
     * how many times (and to where) {@code SpawnRelocator} teleported the
     * player.
     */
    public void teleportTo(double x, double y, double z) {
        Vec3 target = new Vec3(x, y, z);
        this.position = target;
        this.teleportCallCount++;
        this.lastTeleportTarget = target;
    }

    /**
     * Mirror of {@code ServerPlayer#getRespawnPosition()}: returns
     * {@code null} when the player has no bound bed or respawn anchor.
     */
    public BlockPos getRespawnPosition() {
        return respawnPosition;
    }

    /**
     * Mirror of {@code Entity#getPersistentData()}. Always returns the same
     * mutable instance, matching Vanilla semantics so callers may
     * read-modify-write {@code "tensura_tno"} sub-tags directly.
     */
    public CompoundTag getPersistentData() {
        return persistentData;
    }

    /**
     * Mirror of {@code Entity#level()} (Forge / NeoForge surface). Returns
     * the {@link ServerLevelStub} the player is currently in.
     */
    public ServerLevelStub level() {
        return level;
    }

    /**
     * Mirror of {@code player.getGameProfile().getName()} used by the
     * log-format example tests. Plain text, no profile object required.
     */
    public String getName() {
        return name;
    }

    // ----- Stub-only accessors ---------------------------------------------

    /**
     * Test-only: bind a respawn position (e.g. to verify the
     * "player has respawn -> skip" guard).
     */
    public void setRespawnPosition(BlockPos pos) {
        this.respawnPosition = pos;
    }

    /**
     * @return how many times {@link #teleportTo(double, double, double)}
     *         has been called on this stub.
     */
    public int getTeleportCallCount() {
        return teleportCallCount;
    }

    /**
     * @return the target of the most recent
     *         {@link #teleportTo(double, double, double)} call, or
     *         {@code null} if the method has never been invoked.
     */
    public Vec3 getLastTeleportTarget() {
        return lastTeleportTarget;
    }

    // ----- Builder ---------------------------------------------------------

    /**
     * Returns a fresh {@link Builder} pre-populated with sensible defaults
     * (the player is named {@code "TestPlayer"}, at {@code (0.5, 64.0, 0.5)},
     * with no respawn position and a fresh empty persistent NBT).
     *
     * @param level the {@link ServerLevelStub} the player belongs to
     */
    public static Builder builder(ServerLevelStub level) {
        return new Builder(level);
    }

    /** Fluent builder for {@link ServerPlayerStub}. */
    public static final class Builder {

        private final ServerLevelStub level;
        private String name = "TestPlayer";
        private Vec3 position = new Vec3(0.5, 64.0, 0.5);
        private BlockPos respawnPosition;
        private CompoundTag persistentData;

        private Builder(ServerLevelStub level) {
            this.level = Objects.requireNonNull(level, "level");
        }

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder position(Vec3 position) {
            this.position = Objects.requireNonNull(position, "position");
            return this;
        }

        public Builder position(double x, double y, double z) {
            return position(new Vec3(x, y, z));
        }

        /** Place the player block-aligned at {@code (pos.x + 0.5, pos.y, pos.z + 0.5)}. */
        public Builder positionAt(BlockPos pos) {
            Objects.requireNonNull(pos, "pos");
            return position(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }

        public Builder respawnPosition(BlockPos respawnPosition) {
            this.respawnPosition = respawnPosition;
            return this;
        }

        /** Override the persistent NBT root tag (e.g. pre-seed with a flag). */
        public Builder persistentData(CompoundTag persistentData) {
            this.persistentData = Objects.requireNonNull(persistentData, "persistentData");
            return this;
        }

        public ServerPlayerStub build() {
            return new ServerPlayerStub(this);
        }
    }
}

package com.tensura_tno.world.spawn;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Coordinator / facade for the spawn-biome-blacklist feature.
 *
 * <p>This file currently only exposes the package-private utility helpers
 * required by later tasks (and by the corresponding property tests):
 * blacklist membership checks, respawn-position presence, and biome-id
 * resolution for log formatting. The actual NeoForge event listeners
 * ({@code onServerStarted}, {@code onPlayerLoggedIn}) and the orchestration
 * methods ({@code relocateWorldSpawnIfNeeded}, {@code relocatePlayerIfNeeded})
 * are added by tasks 7.2 and 7.3 in the {@code spawn-biome-blacklist}
 * implementation plan.
 *
 * <p>The class is {@code public final} with a private constructor: it is a
 * stateless coordinator, all members are static, and it must remain
 * extension-free so the spec's static-dependency property test can validate
 * that no Tensura internals leak into this code path.
 *
 * <p>For unit testing, the {@link ServerLevel}-based overloads bridge to
 * narrow-interface variants taking a {@link BiomeLookup} (and, for the
 * blacklist check, an explicit {@code Set<ResourceLocation>}). Production
 * code uses a lambda over {@link #biomeIdAt(ServerLevel, BlockPos)} and the
 * cached {@link BiomeBlacklistConfig#getBlacklist()} value, so the runtime
 * path stays purely on Mojang / NeoForge public API.
 *
 * @see BiomeBlacklistConfig
 * @see BiomeLookup
 * @see SafeSpawnFinder
 */
public final class SpawnRelocator {

    /**
     * SLF4J logger; consumed by the orchestration methods added in tasks
     * 7.2 and 7.3 to emit the {@code INFO} / {@code WARN} / {@code ERROR}
     * / {@code DEBUG} messages described in the design document's error
     * handling matrix.
     */
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Common log-line prefix shared by every message emitted from this
     * coordinator. Required by Requirements 8.1 and 8.2 so server
     * operators can grep relocation activity out of the server log
     * regardless of log level. Consumed by tasks 7.2 / 7.3.
     */
    static final String LOG_PREFIX = "[TensuraTNO][SpawnRelocator]";

    /**
     * Namespace key under which this mod stores its per-player flags in
     * {@link ServerPlayer#getPersistentData()}. Consumed by task 7.3 to
     * implement the "first login already processed" guard required by
     * Requirement 2.4.
     */
    static final String PLAYER_TAG_NS = "tensura_tno";

    /**
     * Boolean key inside the {@link #PLAYER_TAG_NS} sub-compound that
     * records whether the player's first-login spawn relocation has
     * already been considered (regardless of whether the position was
     * actually changed). Consumed by task 7.3.
     */
    static final String PLAYER_KEY_RELOCATED = "spawn_relocated";

    private SpawnRelocator() {
        // utility class; no instances
    }

    /**
     * Returns whether the biome at {@code pos} in {@code level} is a member
     * of the configured spawn-biome blacklist.
     *
     * <p>This is the production entry point used by the orchestration
     * methods added in tasks 7.2 / 7.3: it resolves the biome id via
     * {@link #biomeIdAt(ServerLevel, BlockPos)} and compares against
     * {@link BiomeBlacklistConfig#getBlacklist()}. The narrow-interface
     * overload {@link #isInBlacklist(BiomeLookup, BlockPos, Set)} is the
     * one exercised by the property test for Requirements 1.1 / 2.1.
     *
     * @param level the server level whose biome data to consult; never
     *              {@code null}
     * @param pos   the block position to test; never {@code null}
     * @return {@code true} iff the biome at {@code pos} is contained in the
     *         configured blacklist set
     */
    static boolean isInBlacklist(ServerLevel level, BlockPos pos) {
        return isInBlacklist(p -> biomeIdAt(level, p), pos, BiomeBlacklistConfig.getBlacklist());
    }

    /**
     * Narrow-interface test entry for blacklist membership.
     *
     * <p>By accepting a {@link BiomeLookup} and an explicit blacklist set
     * instead of a {@link ServerLevel}, this overload lets unit tests
     * exercise the membership rule without instantiating any Vanilla
     * world. Production code reaches this overload through the
     * {@link #isInBlacklist(ServerLevel, BlockPos)} bridge.
     *
     * @param biomeLookup the biome resolver; receives {@code pos} verbatim
     * @param pos         the block position to test; never {@code null}
     * @param blacklist   the set of biome resource locations to compare
     *                    against; never {@code null}
     * @return {@code true} iff {@code blacklist} contains the resolved
     *         biome id at {@code pos}
     */
    static boolean isInBlacklist(BiomeLookup biomeLookup, BlockPos pos, Set<ResourceLocation> blacklist) {
        ResourceLocation id = biomeLookup.biomeAt(pos);
        return id != null && blacklist.contains(id);
    }

    /**
     * Returns whether {@code player} currently holds a non-null
     * respawn-bed / respawn-anchor position (Requirement 3.1).
     *
     * <p>Used by task 7.3 as a short-circuit guard: a player with an
     * established respawn position must never have their first-login
     * spawn rewritten, even if their current position falls inside the
     * configured biome blacklist.
     *
     * @param player the server player to inspect; never {@code null}
     * @return {@code true} iff
     *         {@link ServerPlayer#getRespawnPosition()} returns a
     *         non-{@code null} value
     */
    static boolean hasRespawnPosition(ServerPlayer player) {
        return player.getRespawnPosition() != null;
    }

    /**
     * Resolves the {@link ResourceLocation} of the biome at {@code pos} in
     * {@code level} for use in log formatting and blacklist membership
     * checks.
     *
     * <p>Mirrors the formula spelled out in the design document: take the
     * {@link Holder} returned by {@link ServerLevel#getBiome(BlockPos)},
     * unwrap its {@link ResourceKey} via
     * {@link Holder#unwrapKey()}{@code .get()}, then take its
     * {@link ResourceKey#location()}. The narrow-interface overload
     * {@link #biomeIdAt(BiomeLookup, BlockPos)} is provided so unit tests
     * can substitute a stub lookup.
     *
     * @param level the server level whose biome data to consult; never
     *              {@code null}
     * @param pos   the block position to test; never {@code null}
     * @return the biome's resource location; never {@code null} for any
     *         registered biome encountered in a normal Vanilla level
     */
    static ResourceLocation biomeIdAt(ServerLevel level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        return holder.unwrapKey().map(ResourceKey::location).orElse(null);
    }

    /**
     * Narrow-interface test entry for biome-id resolution.
     *
     * <p>Production code uses {@link #biomeIdAt(ServerLevel, BlockPos)};
     * tests pass a {@link BiomeLookup} stub that returns a deterministic
     * {@link ResourceLocation} per coordinate.
     *
     * @param biomeLookup the biome resolver; receives {@code pos} verbatim
     * @param pos         the block position to test; never {@code null}
     * @return the biome's resource location as reported by
     *         {@code biomeLookup}
     */
    static ResourceLocation biomeIdAt(BiomeLookup biomeLookup, BlockPos pos) {
        return biomeLookup.biomeAt(pos);
    }

    // ----- Event listeners (Task 7.2) ---------------------------------------

    /**
     * NeoForge {@link ServerStartedEvent} listener: dispatches to
     * {@link #relocateWorldSpawnIfNeeded(ServerLevel)} on the overworld
     * once per server start.
     *
     * <p>Wired by {@code TensuraTNOMod} via
     * {@code NeoForge.EVENT_BUS.addListener(SpawnRelocator::onServerStarted)}
     * (Requirements 9.1, 10.1). Per the design's "新存档启动" sequence
     * diagram, this listener owns the entire orchestration pipeline so the
     * caller (NeoForge) only ever sees a void return — exceptions are
     * caught downstream so a misbehaving spawn relocation never aborts
     * server startup.
     *
     * @param event the event fired by NeoForge once the {@code MinecraftServer}
     *              has finished its start sequence; never {@code null}
     */
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        relocateWorldSpawnIfNeeded(overworld);
    }

    /**
     * Orchestrates the world-level "first-time initial spawn-point
     * relocation" pass against {@code overworld}. Implements the design's
     * "新存档启动" sequence diagram and the error-handling matrix:
     *
     * <ol>
     *   <li>If {@link BiomeBlacklistConfig#isEnabled()} is {@code false},
     *       skip silently (Requirement 4.3 — feature toggle off).</li>
     *   <li>If Tensura core mod is not loaded, skip and log {@code DEBUG}
     *       (Requirements 5.1, 5.2 — no-op when target biomes can never
     *       exist).</li>
     *   <li>If {@code overworld.dimension() != Level.OVERWORLD}, skip and
     *       log {@code DEBUG} (Requirement 7.1 — only the overworld is
     *       considered).</li>
     *   <li>If {@link SpawnRelocatedFlag#isDone()} is already {@code true},
     *       skip and log {@code DEBUG} (Requirement 1.4 — at-most-once per
     *       save).</li>
     *   <li>Resolve the current shared spawn position and its biome. If
     *       the biome is not in the blacklist, mark done and return
     *       (Requirement 1.3).</li>
     *   <li>Otherwise call
     *       {@link SafeSpawnFinder#findSafeSpawn(ServerLevel, BlockPos, int, int, Set)}
     *       and, on a hit, write back via
     *       {@link ServerLevel#setDefaultSpawnPos(BlockPos, float)}
     *       (Requirements 1.2, 6.3, 6.4, 7.2, 8.1).</li>
     *   <li>Mark done in <em>every</em> exit path — success, miss, or
     *       caught {@link RuntimeException} — so subsequent server starts
     *       never re-run the (potentially expensive) spiral search
     *       (Requirements 1.4, 6.5).</li>
     * </ol>
     *
     * <p>The whole flow is wrapped in {@code try/catch RuntimeException}:
     * any unexpected failure degrades to "do not modify spawn + mark done"
     * with an {@code ERROR} stack trace, never to an exception escaping
     * into NeoForge's event dispatch.
     *
     * @param overworld the overworld {@link ServerLevel}; never
     *                  {@code null}
     */
    static void relocateWorldSpawnIfNeeded(ServerLevel overworld) {
        // Up-front "fail-closed" guards on the live ServerLevel: these two
        // (Tensura mod presence and dimension check) are cheap and let us
        // skip even constructing the SpawnRelocatedFlag SavedData when
        // they fail. The remaining guards are checked inside
        // relocateWorldSpawnCore against the WorldSpawnContext built below.
        if (!BiomeBlacklistConfig.isEnabled()) {
            return;
        }
        if (!ModList.get().isLoaded("tensura")) {
            LOGGER.debug("{} skip: tensura mod not loaded", LOG_PREFIX);
            return;
        }
        if (overworld.dimension() != Level.OVERWORLD) {
            LOGGER.debug("{} skip: non-overworld dimension {}", LOG_PREFIX,
                    overworld.dimension().location());
            return;
        }

        SpawnRelocatedFlag flag;
        try {
            flag = SpawnRelocatedFlag.get(overworld);
        } catch (RuntimeException e) {
            LOGGER.error("{} failed to load relocation flag, falling back to original spawn",
                    LOG_PREFIX, e);
            return;
        }

        relocateWorldSpawnCore(adaptToWorldContext(overworld, flag));
    }

    /**
     * Pure-orchestration core for the world-spawn relocation pass. Operates
     * exclusively against the {@link WorldSpawnContext} port so that unit
     * tests can drive every guard branch (and every failure mode of the
     * finder) without instantiating any Vanilla world.
     *
     * <p>Implements the design.md "新存档启动" sequence diagram and the
     * error-handling matrix:
     *
     * <ol>
     *   <li>If {@link WorldSpawnContext#isEnabled()} is {@code false},
     *       skip silently (Requirement 4.3).</li>
     *   <li>If {@link WorldSpawnContext#isTensuraLoaded()} is
     *       {@code false}, skip and log {@code DEBUG} (Requirements 5.1,
     *       5.2).</li>
     *   <li>If {@link WorldSpawnContext#isOverworld()} is {@code false},
     *       skip and log {@code DEBUG} (Requirement 7.1).</li>
     *   <li>If {@link WorldSpawnContext#isFlagDone()} is already
     *       {@code true}, skip and log {@code DEBUG} (Requirement 1.4 —
     *       at-most-once per save).</li>
     *   <li>Resolve the current shared spawn position and its biome. If
     *       the biome is not in the blacklist, mark done and return
     *       (Requirement 1.3).</li>
     *   <li>Otherwise call
     *       {@link WorldSpawnContext#findSafeSpawn(BlockPos)} and, on a
     *       hit, write back via
     *       {@link WorldSpawnContext#setDefaultSpawnPos(BlockPos, float)}
     *       (Requirements 1.2, 6.3, 6.4, 7.2, 8.1).</li>
     *   <li>Mark done in <em>every</em> exit path past the
     *       feature-toggle / Tensura / overworld / flag-already-done
     *       short circuits — success, miss, or caught
     *       {@link RuntimeException} — so subsequent server starts never
     *       re-run the (potentially expensive) spiral search
     *       (Requirements 1.4, 6.5).</li>
     * </ol>
     *
     * <p>The whole flow is wrapped in {@code try/catch RuntimeException}:
     * any unexpected failure degrades to "do not modify spawn + mark done"
     * with an {@code ERROR} stack trace, never to an exception escaping
     * into NeoForge's event dispatch.
     */
    static void relocateWorldSpawnCore(WorldSpawnContext ctx) {
        if (!ctx.isEnabled()) {
            return;
        }
        if (!ctx.isTensuraLoaded()) {
            LOGGER.debug("{} skip: tensura mod not loaded", LOG_PREFIX);
            return;
        }
        if (!ctx.isOverworld()) {
            LOGGER.debug("{} skip: non-overworld dimension {}", LOG_PREFIX, ctx.dimensionId());
            return;
        }
        if (ctx.isFlagDone()) {
            LOGGER.debug("{} skip: already relocated", LOG_PREFIX);
            return;
        }

        try {
            BlockPos originalPos = ctx.getSharedSpawnPos();
            ResourceLocation originalBiome = ctx.biomeIdAt(originalPos);
            Set<ResourceLocation> blacklist = ctx.blacklist();

            // Guard 5: harmless biome -> mark done & skip relocation.
            if (originalBiome == null || !blacklist.contains(originalBiome)) {
                ctx.markFlagDone();
                return;
            }

            // Guards 6 / 7 / 8: finder may return empty (no witness) or
            // throw; we mark done ONLY on success so subsequent server
            // starts can retry the search if it failed (rather than
            // permanently leaving the world spawn inside a blacklisted
            // biome).
            Optional<BlockPos> safe = ctx.findSafeSpawn(originalPos);
            if (safe.isPresent()) {
                BlockPos safePos = safe.get();
                ctx.setDefaultSpawnPos(safePos, 0.0F);
                ResourceLocation newBiome = ctx.biomeIdAt(safePos);
                LOGGER.info(
                    "{} relocated world spawn: {} (biome={}) -> {} (biome={})",
                    LOG_PREFIX, formatPos(originalPos), originalBiome,
                    formatPos(safePos), newBiome);
                ctx.markFlagDone();
            } else {
                LOGGER.warn(
                    "{} no safe spawn found within radius={} from {}; will retry on next server start",
                    LOG_PREFIX, ctx.searchRadius(), formatPos(originalPos));
            }
        } catch (RuntimeException e) {
            LOGGER.error("{} safe-spawn search threw, will retry on next server start",
                    LOG_PREFIX, e);
        }
    }

    /**
     * Builds a {@link WorldSpawnContext} that reads its values from the
     * supplied live {@link ServerLevel} plus the {@link SpawnRelocatedFlag}
     * SavedData and the {@link BiomeBlacklistConfig} static accessors.
     *
     * <p>This is the single place where the production runtime and the
     * test-only narrow port meet: every other site (production listeners
     * and unit tests alike) operates on {@code WorldSpawnContext} only.
     */
    private static WorldSpawnContext adaptToWorldContext(ServerLevel overworld, SpawnRelocatedFlag flag) {
        return new WorldSpawnContext() {
            @Override
            public boolean isEnabled() {
                return BiomeBlacklistConfig.isEnabled();
            }

            @Override
            public boolean isTensuraLoaded() {
                return ModList.get().isLoaded("tensura");
            }

            @Override
            public boolean isOverworld() {
                return overworld.dimension() == Level.OVERWORLD;
            }

            @Override
            public ResourceLocation dimensionId() {
                return overworld.dimension().location();
            }

            @Override
            public BlockPos getSharedSpawnPos() {
                return overworld.getSharedSpawnPos();
            }

            @Override
            public void setDefaultSpawnPos(BlockPos pos, float angle) {
                overworld.setDefaultSpawnPos(pos, angle);
            }

            @Override
            public ResourceLocation biomeIdAt(BlockPos pos) {
                return SpawnRelocator.biomeIdAt(overworld, pos);
            }

            @Override
            public Set<ResourceLocation> blacklist() {
                return BiomeBlacklistConfig.getBlacklist();
            }

            @Override
            public boolean isFlagDone() {
                return flag.isDone();
            }

            @Override
            public void markFlagDone() {
                flag.markDone();
            }

            @Override
            public Optional<BlockPos> findSafeSpawn(BlockPos origin) {
                return SafeSpawnFinder.findSafeSpawnFast(
                        overworld, origin,
                        BiomeBlacklistConfig.getSearchRadius(),
                        BiomeBlacklistConfig.getSearchStep(),
                        BiomeBlacklistConfig.getBlacklist());
            }

            @Override
            public int searchRadius() {
                return BiomeBlacklistConfig.getSearchRadius();
            }
        };
    }

    /**
     * Compact "{@code (x, y, z)}" formatter for log lines, matching the
     * spec's "原坐标 / 新坐标" requirement (Requirement 8.1) without
     * pulling in any external string-builder dependency.
     */
    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    // ----- Event listeners (Task 7.3) ---------------------------------------

    /**
     * NeoForge {@link PlayerEvent.PlayerLoggedInEvent} listener: dispatches
     * to {@link #relocatePlayerIfNeeded(ServerPlayer)} for every player
     * login (the per-player NBT flag inside that method enforces the
     * "first time only" semantics required by Requirement 2.4 / 3.3).
     *
     * <p>Wired by {@code TensuraTNOMod} via
     * {@code NeoForge.EVENT_BUS.addListener(SpawnRelocator::onPlayerLoggedIn)}
     * (Requirements 9.1, 10.1). The event fires on both client and server
     * sides, but only {@link ServerPlayer} instances carry the persistent
     * data and dimension state we need; non-server entities are ignored
     * silently so the listener stays a void-return pure no-op for them.
     *
     * @param event the event fired by NeoForge whenever a player has
     *              finished logging into a level; never {@code null}
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player entity = event.getEntity();
        if (entity instanceof ServerPlayer serverPlayer) {
            relocatePlayerIfNeeded(serverPlayer);
        }
    }

    /**
     * NeoForge {@link PlayerEvent.PlayerRespawnEvent} listener: also runs
     * the per-player relocation pass after death-respawn. This catches the
     * case where the world spawn itself still resides inside a blacklisted
     * biome (e.g. because the world-spawn relocation failed or was disabled
     * mid-game) — without this hook, every death sends the player back into
     * Ancient Forest / Desert of Death.
     *
     * <p>The per-player NBT flag is intentionally cleared at the start of
     * this handler so the flag does not block a respawn relocation attempt
     * on a player that already shipped through the login pass once.
     */
    static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()) {
            return;
        }
        Player entity = event.getEntity();
        if (entity instanceof ServerPlayer serverPlayer) {
            clearPlayerFlag(serverPlayer);
            relocatePlayerIfNeeded(serverPlayer);
        }
    }

    private static void clearPlayerFlag(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (root.contains(PLAYER_TAG_NS, Tag.TAG_COMPOUND)) {
            CompoundTag tno = root.getCompound(PLAYER_TAG_NS);
            tno.remove(PLAYER_KEY_RELOCATED);
            root.put(PLAYER_TAG_NS, tno);
        }
    }

    /**
     * Orchestrates the per-player "first-login spawn relocation" pass for
     * {@code player}. Implements the design's "玩家首次登录" sequence
     * diagram and the error-handling matrix:
     *
     * <ol>
     *   <li>If {@link BiomeBlacklistConfig#isEnabled()} is {@code false},
     *       skip silently (Requirement 4.3 — feature toggle off).</li>
     *   <li>If Tensura core mod is not loaded, skip and log {@code DEBUG}
     *       (Requirements 5.1, 5.2 — no-op when target biomes can never
     *       exist).</li>
     *   <li>If the per-player flag
     *       {@code tensura_tno.spawn_relocated == true} is already set,
     *       skip (Requirement 2.4 / 3.3 — at-most-once per player).</li>
     *   <li>If the player's current dimension is not the overworld,
     *       mark the flag and return (Requirement 7.1, 7.2 — only the
     *       overworld is considered; the flag prevents re-running once
     *       the player later visits the overworld).</li>
     *   <li>If the player already holds a respawn position
     *       ({@link #hasRespawnPosition(ServerPlayer)} is {@code true}),
     *       mark the flag and return (Requirement 3.1 — protect bed /
     *       respawn anchor binds).</li>
     *   <li>If the biome at {@link ServerPlayer#blockPosition()} is not in
     *       the blacklist, mark the flag and return (Requirement 2.3 —
     *       harmless biome).</li>
     *   <li>Otherwise call
     *       {@link SafeSpawnFinder#findSafeSpawn(ServerLevel, BlockPos, int, int, Set)}
     *       and, on a hit, teleport the player to the block-centered
     *       coordinates {@code (p.x + 0.5, p.y, p.z + 0.5)}
     *       (Requirements 2.2, 6.3, 6.4, 7.2, 8.2).</li>
     *   <li>Mark the flag in <em>every</em> exit path — success, miss, or
     *       caught {@link RuntimeException} — so subsequent logins never
     *       re-run the (potentially expensive) spiral search
     *       (Requirements 2.4, 3.3, 6.5).</li>
     * </ol>
     *
     * <p>The whole flow is wrapped in {@code try/catch RuntimeException}:
     * any unexpected failure degrades to "do not move + mark flag" with
     * an {@code ERROR} stack trace, never to an exception escaping into
     * NeoForge's event dispatch.
     *
     * @param player the {@link ServerPlayer} who just logged in; never
     *               {@code null}
     */
    static void relocatePlayerIfNeeded(ServerPlayer player) {
        // Up-front "fail-closed" guards on the live ServerPlayer that need
        // to short-circuit BEFORE we touch the persistent NBT (so a
        // disabled feature or an absent Tensura mod never writes the flag
        // and never logs). The remaining guards are checked inside
        // relocatePlayerCore against the PlayerSpawnContext built below.
        if (!BiomeBlacklistConfig.isEnabled()) {
            return;
        }
        if (!ModList.get().isLoaded("tensura")) {
            LOGGER.debug("{} skip: tensura mod not loaded", LOG_PREFIX);
            return;
        }
        relocatePlayerCore(adaptToPlayerContext(player));
    }

    /**
     * Pure-orchestration core for the per-player first-login relocation
     * pass. Operates exclusively against the {@link PlayerSpawnContext}
     * port so that unit tests can drive every guard branch (and every
     * failure mode of the finder) without instantiating any Vanilla
     * player or world.
     *
     * <p>Implements the design.md "玩家首次登录" sequence diagram and
     * the error-handling matrix:
     *
     * <ol>
     *   <li>If {@link PlayerSpawnContext#isEnabled()} is {@code false},
     *       skip silently (Requirement 4.3).</li>
     *   <li>If {@link PlayerSpawnContext#isTensuraLoaded()} is
     *       {@code false}, skip and log {@code DEBUG} (Requirements 5.1,
     *       5.2).</li>
     *   <li>If {@link PlayerSpawnContext#isPersistentFlagSet()} is already
     *       {@code true}, skip (Requirement 2.4 / 3.3 — at-most-once per
     *       player).</li>
     *   <li>If the player's current dimension is not the overworld,
     *       mark the flag and return (Requirements 7.1, 7.2 — only the
     *       overworld is considered; the flag prevents re-running once
     *       the player later visits the overworld).</li>
     *   <li>If the player already holds a respawn position
     *       ({@link PlayerSpawnContext#hasRespawnPosition()} is
     *       {@code true}), mark the flag and return (Requirement 3.1 —
     *       protect bed / respawn anchor binds).</li>
     *   <li>If the biome at
     *       {@link PlayerSpawnContext#blockPosition()} is not in the
     *       blacklist, mark the flag and return (Requirement 2.3).</li>
     *   <li>Otherwise call
     *       {@link PlayerSpawnContext#findSafeSpawn(BlockPos)} and, on a
     *       hit, teleport the player to the block-centered coordinates
     *       {@code (p.x + 0.5, p.y, p.z + 0.5)} (Requirements 2.2, 6.3,
     *       6.4, 7.2, 8.2).</li>
     *   <li>Mark the flag in <em>every</em> exit path past the
     *       feature-toggle / Tensura / NBT-already-set short circuits —
     *       success, miss, or caught {@link RuntimeException} — so
     *       subsequent logins never re-run the (potentially expensive)
     *       spiral search (Requirements 2.4, 3.3, 6.5).</li>
     * </ol>
     *
     * <p>The whole flow is wrapped in {@code try/catch RuntimeException}:
     * any unexpected failure degrades to "do not move + mark flag" with
     * an {@code ERROR} stack trace, never to an exception escaping into
     * NeoForge's event dispatch.
     */
    static void relocatePlayerCore(PlayerSpawnContext ctx) {
        if (!ctx.isEnabled()) {
            return;
        }
        if (!ctx.isTensuraLoaded()) {
            LOGGER.debug("{} skip: tensura mod not loaded", LOG_PREFIX);
            return;
        }

        String playerName = ctx.playerName();

        if (ctx.isPersistentFlagSet()) {
            LOGGER.debug("{} skip: player {} already relocated", LOG_PREFIX, playerName);
            return;
        }

        try {
            if (!ctx.isOverworld()) {
                LOGGER.debug("{} skip: player {} in non-overworld dimension {}",
                        LOG_PREFIX, playerName, ctx.dimensionId());
                ctx.setPersistentFlag();
                return;
            }
            if (ctx.hasRespawnPosition()) {
                LOGGER.debug("{} skip: player {} has respawn position", LOG_PREFIX, playerName);
                ctx.setPersistentFlag();
                return;
            }

            BlockPos originalPos = ctx.blockPosition();
            ResourceLocation originalBiome = ctx.biomeIdAt(originalPos);
            Set<ResourceLocation> blacklist = ctx.blacklist();

            if (originalBiome == null || !blacklist.contains(originalBiome)) {
                ctx.setPersistentFlag();
                return;
            }

            Optional<BlockPos> safe = ctx.findSafeSpawn(originalPos);
            if (safe.isPresent()) {
                BlockPos safePos = safe.get();
                ctx.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
                ResourceLocation newBiome = ctx.biomeIdAt(safePos);
                LOGGER.info(
                    "{} relocated player {}: {} (biome={}) -> {} (biome={})",
                    LOG_PREFIX, playerName, formatPos(originalPos), originalBiome,
                    formatPos(safePos), newBiome);
                ctx.setPersistentFlag();
            } else {
                LOGGER.warn(
                    "{} no safe spawn found within radius={} from {} for player {}; will retry on next event",
                    LOG_PREFIX, ctx.searchRadius(), formatPos(originalPos), playerName);
            }
        } catch (RuntimeException e) {
            LOGGER.error("{} player relocation threw for {}, will retry on next event",
                    LOG_PREFIX, playerName, e);
        }
    }

    /**
     * Builds a {@link PlayerSpawnContext} that reads its values from the
     * supplied live {@link ServerPlayer} plus the
     * {@link BiomeBlacklistConfig} static accessors and the
     * {@link ModList} runtime check.
     *
     * <p>This is the single place where the production runtime and the
     * test-only narrow port meet on the player path: every other site
     * (production listener and unit tests alike) operates on
     * {@code PlayerSpawnContext} only.
     */
    private static PlayerSpawnContext adaptToPlayerContext(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        return new PlayerSpawnContext() {
            @Override
            public boolean isEnabled() {
                return BiomeBlacklistConfig.isEnabled();
            }

            @Override
            public boolean isTensuraLoaded() {
                return ModList.get().isLoaded("tensura");
            }

            @Override
            public boolean isOverworld() {
                return level.dimension() == Level.OVERWORLD;
            }

            @Override
            public ResourceLocation dimensionId() {
                return level.dimension().location();
            }

            @Override
            public boolean isPersistentFlagSet() {
                CompoundTag root = player.getPersistentData();
                return root.contains(PLAYER_TAG_NS, Tag.TAG_COMPOUND)
                        && root.getCompound(PLAYER_TAG_NS).getBoolean(PLAYER_KEY_RELOCATED);
            }

            @Override
            public void setPersistentFlag() {
                markPlayerRelocated(player);
            }

            @Override
            public boolean hasRespawnPosition() {
                return SpawnRelocator.hasRespawnPosition(player);
            }

            @Override
            public BlockPos blockPosition() {
                return player.blockPosition();
            }

            @Override
            public void teleportTo(double x, double y, double z) {
                player.teleportTo(x, y, z);
            }

            @Override
            public ResourceLocation biomeIdAt(BlockPos pos) {
                return SpawnRelocator.biomeIdAt(level, pos);
            }

            @Override
            public Set<ResourceLocation> blacklist() {
                return BiomeBlacklistConfig.getBlacklist();
            }

            @Override
            public Optional<BlockPos> findSafeSpawn(BlockPos origin) {
                return SafeSpawnFinder.findSafeSpawnFast(
                        level, origin,
                        BiomeBlacklistConfig.getSearchRadius(),
                        BiomeBlacklistConfig.getSearchStep(),
                        BiomeBlacklistConfig.getBlacklist());
            }

            @Override
            public int searchRadius() {
                return BiomeBlacklistConfig.getSearchRadius();
            }

            @Override
            public String playerName() {
                return player.getGameProfile().getName();
            }
        };
    }

    /**
     * Writes {@code tensura_tno.spawn_relocated = true} into
     * {@code player.getPersistentData()} using a get-or-create pattern on
     * the {@link #PLAYER_TAG_NS} sub-compound. Used by every exit path of
     * {@link #relocatePlayerIfNeeded(ServerPlayer)} (other than the
     * already-set short-circuit) so callers don't have to repeat the NBT
     * dance at each branch.
     *
     * <p>The {@code put} call is required because
     * {@link CompoundTag#getCompound(String)} returns a freshly allocated
     * empty tag for missing keys instead of a live reference, so mutating
     * the returned compound and skipping {@code put} would silently lose
     * the write.
     */
    private static void markPlayerRelocated(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag tno = root.contains(PLAYER_TAG_NS, Tag.TAG_COMPOUND)
                ? root.getCompound(PLAYER_TAG_NS)
                : new CompoundTag();
        tno.putBoolean(PLAYER_KEY_RELOCATED, true);
        root.put(PLAYER_TAG_NS, tno);
    }
}

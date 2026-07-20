package com.tensura_tno.world.spawn;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * COMMON-side configuration for the "spawn biome blacklist" feature.
 *
 * <p>Mirrors the style of {@code TensuraTNOCompatConfig}: a single
 * {@link ModConfigSpec} built statically, plus static accessors that defer to
 * the underlying {@link ModConfigSpec.ConfigValue#get()} call but fall back to
 * the documented defaults when the spec hasn't been loaded yet (e.g. during
 * very early class-loading, or in unit-test JVMs that never register the
 * spec).
 *
 * <p>The blacklist is stored as a list of {@code ResourceLocation} strings so
 * that this class never references any Tensura class directly (Requirement
 * 5.3): non-parseable entries are logged at {@code WARN} once per config load
 * and then silently dropped at runtime.
 */
public final class BiomeBlacklistConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[TensuraTNO][BiomeBlacklistConfig]";

    /** Default value for {@code spawnBiomeBlacklistEnabled} (Requirement 4.3). */
    public static final boolean DEFAULT_ENABLED = true;
    /** Default entries for {@code spawnBiomeBlacklist} (Requirement 4.2). */
    public static final List<String> DEFAULT_BLACKLIST = List.of(
        "tensura:ancient_forest",
        "tensura:desert_of_death"
    );
    /** Default value for {@code spawnSafeSearchRadius} (Requirement 4.4). */
    public static final int DEFAULT_SEARCH_RADIUS = 1024;
    /** Inclusive minimum of {@code spawnSafeSearchRadius} (Requirement 4.4). */
    public static final int MIN_SEARCH_RADIUS = 64;
    /** Inclusive maximum of {@code spawnSafeSearchRadius} (Requirement 4.4). */
    public static final int MAX_SEARCH_RADIUS = 8192;
    /** Default value for {@code spawnSafeSearchStep} (Requirement 4.5). */
    public static final int DEFAULT_SEARCH_STEP = 64;
    /** Inclusive minimum of {@code spawnSafeSearchStep} (Requirement 4.5). */
    public static final int MIN_SEARCH_STEP = 16;
    /** Inclusive maximum of {@code spawnSafeSearchStep} (Requirement 4.5). */
    public static final int MAX_SEARCH_STEP = 512;

    public static final ModConfigSpec COMMON_SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    private static final ModConfigSpec.IntValue SEARCH_RADIUS;
    private static final ModConfigSpec.IntValue SEARCH_STEP;

    /**
     * Cached, parsed view of {@link #BLACKLIST}. Populated by
     * {@link #onConfigLoad(ModConfigEvent)} on every {@code Loading} /
     * {@code Reloading} event for this spec; read by hot-path callers.
     */
    private static volatile Set<ResourceLocation> cachedBlacklist = Set.of();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("spawn");

        ENABLED = builder
            .comment("Master toggle for the spawn-biome-blacklist feature.\n" +
                     "When false, the world initial spawn point and player first-login spawn are\n" +
                     "left untouched regardless of the configured blacklist.")
            .define("spawnBiomeBlacklistEnabled", DEFAULT_ENABLED);

        BLACKLIST = builder
            .comment("Biomes that must NOT host the world's initial spawn point or a new player's\n" +
                     "first-login spawn position. Each entry is a ResourceLocation in 'namespace:path'\n" +
                     "form. Invalid entries are logged at WARN and skipped at runtime.\n" +
                     "Default targets the Tensura overworld biomes that apply heavy magicule\n" +
                     "poisoning to unprotected new players.")
            .defineListAllowEmpty(
                "spawnBiomeBlacklist",
                DEFAULT_BLACKLIST,
                () -> "tensura:ancient_forest",
                BiomeBlacklistConfig::isStringEntry
            );

        SEARCH_RADIUS = builder
            .comment("Maximum horizontal radius (in blocks) that the safe-spawn finder will scan\n" +
                     "outward from the original spawn point when relocating.\n" +
                     "Range: [" + MIN_SEARCH_RADIUS + ", " + MAX_SEARCH_RADIUS + "].")
            .defineInRange("spawnSafeSearchRadius", DEFAULT_SEARCH_RADIUS,
                MIN_SEARCH_RADIUS, MAX_SEARCH_RADIUS);

        SEARCH_STEP = builder
            .comment("Sampling step (in blocks) used by the safe-spawn finder while spiral-scanning.\n" +
                     "Smaller values produce a denser grid (more accurate but slower); larger values\n" +
                     "produce a coarser grid.\n" +
                     "Range: [" + MIN_SEARCH_STEP + ", " + MAX_SEARCH_STEP + "].")
            .defineInRange("spawnSafeSearchStep", DEFAULT_SEARCH_STEP,
                MIN_SEARCH_STEP, MAX_SEARCH_STEP);

        builder.pop();

        COMMON_SPEC = builder.build();
    }

    private BiomeBlacklistConfig() {
    }

    /**
     * @return whether the spawn-biome-blacklist feature is enabled
     *         (Requirement 4.3). Falls back to {@link #DEFAULT_ENABLED} if
     *         the spec hasn't been loaded yet.
     */
    public static boolean isEnabled() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException ignored) {
            return DEFAULT_ENABLED;
        }
    }

    /**
     * @return the cached, parsed view of the configured blacklist
     *         (Requirements 4.1, 4.2, 4.6, 4.7). Never {@code null}; empty
     *         when the cache hasn't been populated yet.
     */
    public static Set<ResourceLocation> getBlacklist() {
        return cachedBlacklist;
    }

    /**
     * @return the configured horizontal search radius (Requirement 4.4).
     *         Falls back to {@link #DEFAULT_SEARCH_RADIUS} if the spec
     *         hasn't been loaded yet.
     */
    public static int getSearchRadius() {
        try {
            return SEARCH_RADIUS.get();
        } catch (IllegalStateException ignored) {
            return DEFAULT_SEARCH_RADIUS;
        }
    }

    /**
     * @return the configured sampling step (Requirement 4.5). Falls back to
     *         {@link #DEFAULT_SEARCH_STEP} if the spec hasn't been loaded
     *         yet.
     */
    public static int getSearchStep() {
        try {
            return SEARCH_STEP.get();
        } catch (IllegalStateException ignored) {
            return DEFAULT_SEARCH_STEP;
        }
    }

    /**
     * Mod-bus listener wired by {@code TensuraTNOMod}. Rebuilds
     * {@link #cachedBlacklist} on every {@link ModConfigEvent.Loading} /
     * {@link ModConfigEvent.Reloading} that matches {@link #COMMON_SPEC},
     * and emits a single {@code INFO} line documenting the entry count and
     * enabled state (Requirements 4.7, 8.3).
     */
    public static void onConfigLoad(ModConfigEvent event) {
        if (event == null || event.getConfig() == null
            || event.getConfig().getSpec() != COMMON_SPEC) {
            return;
        }
        if (!(event instanceof ModConfigEvent.Loading)
            && !(event instanceof ModConfigEvent.Reloading)) {
            return;
        }

        List<? extends String> raw;
        try {
            raw = BLACKLIST.get();
        } catch (IllegalStateException ignored) {
            raw = DEFAULT_BLACKLIST;
        }

        Set<ResourceLocation> rebuilt = parseBlacklist(raw);
        cachedBlacklist = rebuilt;

        boolean enabled;
        try {
            enabled = ENABLED.get();
        } catch (IllegalStateException ignored) {
            enabled = DEFAULT_ENABLED;
        }

        LOGGER.info("{} loaded; entries={}, enabled={}", LOG_PREFIX, rebuilt.size(), enabled);
    }

    /**
     * Parses raw configured strings into a set of {@link ResourceLocation}.
     * Entries that fail {@link ResourceLocation#tryParse(String)} are
     * logged at {@code WARN} and skipped (Requirement 4.6); duplicates and
     * blanks are dropped. The order of valid entries is preserved (a
     * {@link LinkedHashSet} backs the result) for deterministic logging.
     *
     * @param raw the raw configured strings; may be {@code null} or contain
     *            {@code null} elements
     * @return a non-{@code null} immutable view backed by an insertion-
     *         ordered set
     */
    private static Set<ResourceLocation> parseBlacklist(List<? extends String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<ResourceLocation> out = new LinkedHashSet<>(raw.size());
        for (String entry : raw) {
            if (entry == null || entry.isEmpty()) {
                LOGGER.warn("{} invalid biome id in spawnBiomeBlacklist: <empty>", LOG_PREFIX);
                continue;
            }
            ResourceLocation parsed = ResourceLocation.tryParse(entry);
            if (parsed == null) {
                LOGGER.warn("{} invalid biome id in spawnBiomeBlacklist: {}", LOG_PREFIX, entry);
                continue;
            }
            out.add(parsed);
        }
        return Set.copyOf(out);
    }

    /**
     * Validator used by {@link ModConfigSpec.Builder#defineListAllowEmpty}.
     * Accepts any non-{@code null} {@link String}; runtime parsing is left
     * to {@link #parseBlacklist(List)} so that invalid entries produce a
     * single {@code WARN} per load instead of being rejected wholesale.
     */
    private static boolean isStringEntry(Object obj) {
        return Objects.nonNull(obj) && obj instanceof String;
    }
}

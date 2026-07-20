package com.tensura_tno.world.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import com.electronwill.nightconfig.core.CommentedConfig;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Property-based tests for {@link BiomeBlacklistConfig}.
 *
 * <p>The production class exposes {@code parseBlacklist} as {@code private}, so the
 * test reaches it through reflection. WARN events are captured with a programmatic
 * Log4j2 {@link AbstractAppender}: {@code LogUtils.getLogger()} returns an SLF4J
 * logger which is bound to Log4j2 in this project through {@code log4j-slf4j2-impl},
 * so attaching to the named Log4j2 logger captures every event the production code
 * emits.
 */
class BiomeBlacklistConfigTest {

    /**
     * Property 7 — parseBlacklist robustness.
     *
     * <p>For any raw {@code List<? extends String>} (including empty strings,
     * uppercase, Chinese characters, lone colons, totally random text, etc.) the
     * private {@code parseBlacklist} must:
     * <ol>
     *   <li>terminate without throwing;</li>
     *   <li>return a set exactly equal to
     *       {@code { ResourceLocation.tryParse(s) | s ∈ raw, tryParse(s) != null }};</li>
     *   <li>emit exactly one WARN log per invalid entry (null / empty / unparseable).</li>
     * </ol>
     *
     * <p><b>Validates: Requirement 4.6</b>
     */
    @net.jqwik.api.Property(tries = 200)
    void parseBlacklist_robustness(@ForAll("rawEntries") List<String> raw) throws Exception {
        Method parse = BiomeBlacklistConfig.class.getDeclaredMethod("parseBlacklist", List.class);
        parse.setAccessible(true);

        CapturingAppender appender = CapturingAppender.attach(BiomeBlacklistConfig.class);
        try {
            Object out;
            try {
                out = parse.invoke(null, raw);
            } catch (Throwable t) {
                // Unwrap reflective wrappers so the assertion message is useful.
                Throwable cause = (t.getCause() != null) ? t.getCause() : t;
                throw new AssertionError("parseBlacklist must never throw, but got: " + cause, cause);
            }

            assertNotNull(out, "parseBlacklist must not return null");

            @SuppressWarnings("unchecked")
            Set<ResourceLocation> result = (Set<ResourceLocation>) out;

            // Compute the expected set and invalid count using the same semantics
            // documented on the production method.
            LinkedHashSet<ResourceLocation> expected = new LinkedHashSet<>();
            int invalidCount = 0;
            for (String s : raw) {
                if (s == null || s.isEmpty()) {
                    invalidCount++;
                    continue;
                }
                ResourceLocation rl;
                try {
                    rl = ResourceLocation.tryParse(s);
                } catch (Throwable ignored) {
                    rl = null;
                }
                if (rl == null) {
                    invalidCount++;
                } else {
                    expected.add(rl);
                }
            }

            assertEquals(expected, result,
                "Result must equal { tryParse(s) | s ∈ raw, tryParse(s) != null }; raw=" + raw);

            long warns = appender.events().stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .count();
            assertEquals((long) invalidCount, warns,
                "Each invalid entry must emit exactly one WARN; raw=" + raw);
        } finally {
            appender.detach();
        }
    }

    @Provide
    Arbitrary<List<String>> rawEntries() {
        Arbitrary<String> valid = Arbitraries.of(
            "tensura:ancient_forest",
            "tensura:desert_of_death",
            "minecraft:plains",
            "minecraft:forest",
            "minecraft:badlands",
            "minecraft:ocean",
            "modid:foo",
            "abc:xyz_123",
            "a.b.c:d-e/f"
        );
        Arbitrary<String> degenerate = Arbitraries.of(
            "", ":", "::", ":foo", "foo:", "   ", "\t", "\n",
            "no_colon_here", "trailing:colon:extra"
        );
        Arbitrary<String> uppercase = Arbitraries.of(
            "Tensura:Ancient_Forest",
            "MINECRAFT:PLAINS",
            "Mixed:CaSe",
            "TENSURA:DESERT_OF_DEATH"
        );
        Arbitrary<String> chinese = Arbitraries.of(
            "中文:测试",
            "tensura:中文",
            "中文:中文",
            "汉字",
            "测试"
        );
        Arbitrary<String> random = Arbitraries.strings().ofMaxLength(24);

        // Weighted mix: bias toward valid IDs so the result set is interesting,
        // but keep meaningful coverage of every degenerate / pathological shape.
        Arbitrary<String> mixed = Arbitraries.frequencyOf(
            Tuple.of(5, valid),
            Tuple.of(2, degenerate),
            Tuple.of(2, uppercase),
            Tuple.of(2, chinese),
            Tuple.of(3, random)
        );

        return mixed.list().ofMaxSize(20);
    }

    // =====================================================================
    // Property 8 — Cache rebuild matches latest configuration
    // =====================================================================

    /**
     * Property 8 — Cache rebuild matches latest configuration.
     *
     * <p>For any sequence of raw lists {@code R₁, R₂, …, Rₙ} dispatched in
     * order to {@link BiomeBlacklistConfig#onConfigLoad(ModConfigEvent)} as
     * a mix of {@code Loading} / {@code Reloading} events, after the
     * {@code n}-th dispatch the cached blacklist must equal
     * {@code parseBlacklist(Rₙ)}; intermediate dispatches must not leak.
     *
     * <p>The check is done by comparing
     * {@link BiomeBlacklistConfig#getBlacklist()} after each dispatch
     * against the same {@code parseBlacklist} invoked through reflection on
     * the same input — both go through identical {@code tryParse} +
     * insertion-order dedup semantics, so the assertion is precisely
     * "cache reflects the latest configuration".
     *
     * <p><b>Validates: Requirement 4.7</b>
     */
    @net.jqwik.api.Property(tries = 100)
    void cacheRebuild_matchesLatestConfiguration(
            @ForAll("rawSequenceWithKind") List<Tuple.Tuple2<List<String>, Boolean>> sequence) throws Exception {
        Method parse = BiomeBlacklistConfig.class.getDeclaredMethod("parseBlacklist", List.class);
        parse.setAccessible(true);

        // Build a real ModConfig once — its only role here is to satisfy
        // event.getConfig().getSpec() == COMMON_SPEC. We construct it via
        // reflection because its constructor is package-private.
        ModConfig modConfig = newModConfig(BiomeBlacklistConfig.COMMON_SPEC);

        for (int i = 0; i < sequence.size(); i++) {
            List<String> raw = sequence.get(i).get1();
            boolean isReloading = sequence.get(i).get2();

            // 1. Load values into the spec via the standard FML pathway:
            //    build an in-memory CommentedConfig, set the path, then
            //    feed it through ModConfigSpec#acceptConfig via the
            //    sealed ILoadedConfig record (which is package-private,
            //    so we reach it through reflection too).
            CommentedConfig backing = CommentedConfig.inMemory();
            backing.set(List.of("spawn", "spawnBiomeBlacklist"), raw);
            // Default the other knobs so isCorrect doesn't trigger
            // a self-correcting overwrite.
            backing.set(List.of("spawn", "spawnBiomeBlacklistEnabled"),
                BiomeBlacklistConfig.DEFAULT_ENABLED);
            backing.set(List.of("spawn", "spawnSafeSearchRadius"),
                BiomeBlacklistConfig.DEFAULT_SEARCH_RADIUS);
            backing.set(List.of("spawn", "spawnSafeSearchStep"),
                BiomeBlacklistConfig.DEFAULT_SEARCH_STEP);

            IConfigSpec.ILoadedConfig loaded = newLoadedConfig(backing, modConfig);
            // Bypass ModConfigSpec#acceptConfig: that method invokes isCorrect()
            // which compares structural metadata (comments / defaults) and may
            // self-correct via LoadedConfig#save(), and save() in turn dispatches
            // a Reloading event through ModConfig#container — which we left null.
            // Setting loadedConfig + afterReload() reproduces the only state the
            // ConfigValue accessors actually need (loaded != null + cleared
            // value caches) without touching the save / event-dispatch path.
            installLoadedConfig(BiomeBlacklistConfig.COMMON_SPEC, loaded);

            // 2. Dispatch the event (alternating Loading / Reloading per
            //    the generator's boolean to exercise both branches).
            ModConfigEvent event = isReloading
                ? new ModConfigEvent.Reloading(modConfig)
                : new ModConfigEvent.Loading(modConfig);
            BiomeBlacklistConfig.onConfigLoad(event);

            // 3. After the i-th dispatch, the cache must reflect Rᵢ.
            @SuppressWarnings("unchecked")
            Set<ResourceLocation> expected = (Set<ResourceLocation>) parse.invoke(null, raw);

            assertNotNull(BiomeBlacklistConfig.getBlacklist(),
                "cached blacklist must never be null after dispatch");
            assertEquals(expected, BiomeBlacklistConfig.getBlacklist(),
                "after dispatch #" + (i + 1) + " (" + (isReloading ? "Reloading" : "Loading")
                    + "), getBlacklist() must equal parseBlacklist(R_n); raw=" + raw);
        }
    }

    @Provide
    Arbitrary<List<Tuple.Tuple2<List<String>, Boolean>>> rawSequenceWithKind() {
        Arbitrary<String> valid = Arbitraries.of(
            "tensura:ancient_forest",
            "tensura:desert_of_death",
            "minecraft:plains",
            "minecraft:forest",
            "minecraft:taiga",
            "minecraft:badlands",
            "modid:foo",
            "abc:xyz_123"
        );
        Arbitrary<String> degenerate = Arbitraries.of("", ":", "no_colon", "trailing:");
        Arbitrary<String> entry = Arbitraries.frequencyOf(
            Tuple.of(7, valid),
            Tuple.of(2, degenerate),
            Tuple.of(1, Arbitraries.strings().ofMaxLength(16))
        );
        Arbitrary<List<String>> rawList = entry.list().ofMaxSize(8);
        Arbitrary<Boolean> kind = Arbitraries.of(true, false);
        Arbitrary<Tuple.Tuple2<List<String>, Boolean>> single =
            net.jqwik.api.Combinators.combine(rawList, kind).as(Tuple::of);
        return single.list().ofMaxSize(6).ofMinSize(1);
    }

    // ---------------------------------------------------------------------
    // FML internals — reflective constructors
    //
    // ModConfig and the LoadedConfig record both live in
    // net.neoforged.fml.config with package-private visibility. We never
    // exercise their save() / event-dispatch surfaces from these tests, so
    // it's safe to skip the public factory pipeline (ModConfigs / NightConfig
    // file parsing) and construct them directly through reflection.
    // ---------------------------------------------------------------------

    private static ModConfig newModConfig(net.neoforged.neoforge.common.ModConfigSpec spec) throws Exception {
        Class<?> iSpec = Class.forName("net.neoforged.fml.config.IConfigSpec");
        Class<?> container = Class.forName("net.neoforged.fml.ModContainer");
        Constructor<?> ctor = ModConfig.class.getDeclaredConstructor(
            ModConfig.Type.class,
            iSpec,
            container,
            String.class,
            ReentrantLock.class
        );
        ctor.setAccessible(true);
        return (ModConfig) ctor.newInstance(
            ModConfig.Type.COMMON,
            spec,
            null, // ModContainer — never touched by onConfigLoad / installLoadedConfig in this test
            "tensura_tno-spawn-test.toml",
            new ReentrantLock()
        );
    }

    private static IConfigSpec.ILoadedConfig newLoadedConfig(CommentedConfig cfg, ModConfig modConfig)
            throws Exception {
        Class<?> loadedConfigClass = Class.forName("net.neoforged.fml.config.LoadedConfig");
        Constructor<?> ctor = loadedConfigClass.getDeclaredConstructor(
            CommentedConfig.class,
            Path.class,
            ModConfig.class
        );
        ctor.setAccessible(true);
        return (IConfigSpec.ILoadedConfig) ctor.newInstance(cfg, null, modConfig);
    }

    /**
     * Equivalent of {@code ModConfigSpec#acceptConfig} but skips the
     * {@code isCorrect} self-correction path that would otherwise invoke
     * {@link IConfigSpec.ILoadedConfig#save()} — and {@code save()} ends
     * up dispatching a Reloading event through the {@link ModConfig}'s
     * {@link net.neoforged.fml.ModContainer}, which is intentionally
     * {@code null} in this test fixture.
     *
     * <p>This sets the spec's private {@code loadedConfig} field directly,
     * then calls the public {@code afterReload()} method to clear cached
     * values so subsequent {@code ConfigValue#get()} calls re-read from
     * the new {@link CommentedConfig}.
     */
    private static void installLoadedConfig(net.neoforged.neoforge.common.ModConfigSpec spec,
                                            IConfigSpec.ILoadedConfig loaded) throws Exception {
        java.lang.reflect.Field loadedConfigField =
            net.neoforged.neoforge.common.ModConfigSpec.class.getDeclaredField("loadedConfig");
        loadedConfigField.setAccessible(true);
        loadedConfigField.set(spec, loaded);
        spec.afterReload();
    }

    // ---------------------------------------------------------------------
    // Log4j2 capture helper
    // ---------------------------------------------------------------------

    /**
     * Programmatic Log4j2 list-appender. The production code uses
     * {@code LogUtils.getLogger()} (SLF4J), which is bound to Log4j2 in this
     * project through {@code log4j-slf4j2-impl}; events therefore reach the
     * Log4j2 logger named after the caller class.
     */
    static final class CapturingAppender extends AbstractAppender {

        private final List<LogEvent> events = new CopyOnWriteArrayList<>();
        private final org.apache.logging.log4j.core.Logger target;

        private CapturingAppender(String name, org.apache.logging.log4j.core.Logger target) {
            super(name, null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
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

    // =====================================================================
    // Example tests — defaults, range enforcement, builder usage, load log
    // =====================================================================

    /**
     * Default value of {@code spawnBiomeBlacklistEnabled} is {@code true}
     * (Requirement 4.3).
     */
    @Test
    void default_enabledIsTrue() {
        assertTrue(BiomeBlacklistConfig.DEFAULT_ENABLED,
            "DEFAULT_ENABLED must be true per requirement 4.3");
    }

    /**
     * Default {@code spawnBiomeBlacklist} contains both seed entries
     * {@code tensura:ancient_forest} and {@code tensura:desert_of_death}
     * (Requirement 4.2).
     */
    @Test
    void default_blacklistContainsAncientForestAndDesertOfDeath() {
        List<String> defaults = BiomeBlacklistConfig.DEFAULT_BLACKLIST;
        assertNotNull(defaults, "DEFAULT_BLACKLIST must not be null");
        assertTrue(defaults.contains("tensura:ancient_forest"),
            "DEFAULT_BLACKLIST must contain tensura:ancient_forest; was=" + defaults);
        assertTrue(defaults.contains("tensura:desert_of_death"),
            "DEFAULT_BLACKLIST must contain tensura:desert_of_death; was=" + defaults);
    }

    /**
     * The {@code spawnSafeSearchRadius} {@link net.neoforged.neoforge.common.ModConfigSpec.ValueSpec}
     * carries a {@link net.neoforged.neoforge.common.ModConfigSpec.Range Range} that:
     * <ul>
     *   <li>matches the documented {@code [MIN_SEARCH_RADIUS, MAX_SEARCH_RADIUS]} bounds;</li>
     *   <li>accepts the configured default;</li>
     *   <li>rejects values immediately outside the bounds (Requirement 4.4).</li>
     * </ul>
     */
    @Test
    void range_radiusEnforcedByBuilder() throws Exception {
        net.neoforged.neoforge.common.ModConfigSpec.IntValue intValue = readIntValue("SEARCH_RADIUS");
        net.neoforged.neoforge.common.ModConfigSpec.ValueSpec valueSpec = intValue.getSpec();
        net.neoforged.neoforge.common.ModConfigSpec.Range<Integer> range = valueSpec.getRange();
        assertNotNull(range, "spawnSafeSearchRadius must be defined with a Range");
        assertEquals(BiomeBlacklistConfig.MIN_SEARCH_RADIUS, range.getMin().intValue(),
            "Range min must equal MIN_SEARCH_RADIUS");
        assertEquals(BiomeBlacklistConfig.MAX_SEARCH_RADIUS, range.getMax().intValue(),
            "Range max must equal MAX_SEARCH_RADIUS");

        // ValueSpec is also the validator used at config-load time.
        assertTrue(valueSpec.test(BiomeBlacklistConfig.DEFAULT_SEARCH_RADIUS),
            "Default radius must pass spec validation");
        assertTrue(valueSpec.test(BiomeBlacklistConfig.MIN_SEARCH_RADIUS),
            "MIN_SEARCH_RADIUS must pass spec validation (inclusive)");
        assertTrue(valueSpec.test(BiomeBlacklistConfig.MAX_SEARCH_RADIUS),
            "MAX_SEARCH_RADIUS must pass spec validation (inclusive)");
        assertFalse(valueSpec.test(BiomeBlacklistConfig.MIN_SEARCH_RADIUS - 1),
            "Below-min radius must be rejected by spec validation");
        assertFalse(valueSpec.test(BiomeBlacklistConfig.MAX_SEARCH_RADIUS + 1),
            "Above-max radius must be rejected by spec validation");
    }

    /**
     * The {@code spawnSafeSearchStep} {@link net.neoforged.neoforge.common.ModConfigSpec.ValueSpec}
     * carries a {@link net.neoforged.neoforge.common.ModConfigSpec.Range Range} that:
     * <ul>
     *   <li>matches the documented {@code [MIN_SEARCH_STEP, MAX_SEARCH_STEP]} bounds;</li>
     *   <li>accepts the configured default;</li>
     *   <li>rejects values immediately outside the bounds (Requirement 4.5).</li>
     * </ul>
     */
    @Test
    void range_stepEnforcedByBuilder() throws Exception {
        net.neoforged.neoforge.common.ModConfigSpec.IntValue intValue = readIntValue("SEARCH_STEP");
        net.neoforged.neoforge.common.ModConfigSpec.ValueSpec valueSpec = intValue.getSpec();
        net.neoforged.neoforge.common.ModConfigSpec.Range<Integer> range = valueSpec.getRange();
        assertNotNull(range, "spawnSafeSearchStep must be defined with a Range");
        assertEquals(BiomeBlacklistConfig.MIN_SEARCH_STEP, range.getMin().intValue(),
            "Range min must equal MIN_SEARCH_STEP");
        assertEquals(BiomeBlacklistConfig.MAX_SEARCH_STEP, range.getMax().intValue(),
            "Range max must equal MAX_SEARCH_STEP");

        assertTrue(valueSpec.test(BiomeBlacklistConfig.DEFAULT_SEARCH_STEP),
            "Default step must pass spec validation");
        assertTrue(valueSpec.test(BiomeBlacklistConfig.MIN_SEARCH_STEP),
            "MIN_SEARCH_STEP must pass spec validation (inclusive)");
        assertTrue(valueSpec.test(BiomeBlacklistConfig.MAX_SEARCH_STEP),
            "MAX_SEARCH_STEP must pass spec validation (inclusive)");
        assertFalse(valueSpec.test(BiomeBlacklistConfig.MIN_SEARCH_STEP - 1),
            "Below-min step must be rejected by spec validation");
        assertFalse(valueSpec.test(BiomeBlacklistConfig.MAX_SEARCH_STEP + 1),
            "Above-max step must be rejected by spec validation");
    }

    /**
     * Verifies through reflection that:
     * <ul>
     *   <li>{@link BiomeBlacklistConfig#COMMON_SPEC} is non-null and is an
     *       instance of {@link net.neoforged.neoforge.common.ModConfigSpec};</li>
     *   <li>the four declared static fields ({@code ENABLED}, {@code BLACKLIST},
     *       {@code SEARCH_RADIUS}, {@code SEARCH_STEP}) exist with their
     *       documented {@code ModConfigSpec} value types;</li>
     *   <li>the underlying spec was constructed via
     *       {@link net.neoforged.neoforge.common.ModConfigSpec.Builder} —
     *       evidenced by every {@code ConfigValue} reporting the same
     *       parent {@link net.neoforged.neoforge.common.ModConfigSpec} as
     *       {@code COMMON_SPEC} (the builder is the only path that wires
     *       this {@code spec} backreference, see
     *       {@code ModConfigSpec.Builder#build}).</li>
     * </ul>
     * This mirrors the {@code TensuraTNOCompatConfig} construction style.
     *
     * <p>Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 10.2.
     */
    @Test
    void example_specBuilderUsage() throws Exception {
        // 1. COMMON_SPEC is a built spec, not null.
        assertNotNull(BiomeBlacklistConfig.COMMON_SPEC, "COMMON_SPEC must not be null");
        assertTrue(BiomeBlacklistConfig.COMMON_SPEC instanceof net.neoforged.neoforge.common.ModConfigSpec,
            "COMMON_SPEC must be an instance of ModConfigSpec");
        // The Builder always populates the structural-spec view; an empty
        // structural spec means build() never ran.
        assertFalse(BiomeBlacklistConfig.COMMON_SPEC.getSpec().isEmpty(),
            "COMMON_SPEC must be backed by a non-empty UnmodifiableConfig produced by Builder#build");

        // 2. Field types match the documented ModConfigSpec value classes.
        assertFieldOfType("ENABLED", net.neoforged.neoforge.common.ModConfigSpec.BooleanValue.class);
        assertFieldOfType("BLACKLIST", net.neoforged.neoforge.common.ModConfigSpec.ConfigValue.class);
        assertFieldOfType("SEARCH_RADIUS", net.neoforged.neoforge.common.ModConfigSpec.IntValue.class);
        assertFieldOfType("SEARCH_STEP", net.neoforged.neoforge.common.ModConfigSpec.IntValue.class);

        // 3. Every ConfigValue points back to COMMON_SPEC. ModConfigSpec.Builder#build()
        //    is the only place this 'spec' backreference is wired (line `values.forEach(v -> v.spec = ret)`),
        //    so observing it here confirms COMMON_SPEC was assembled by the Builder.
        assertSame(BiomeBlacklistConfig.COMMON_SPEC, configValueParent("ENABLED"),
            "ENABLED.spec must match COMMON_SPEC (Builder#build wires it)");
        assertSame(BiomeBlacklistConfig.COMMON_SPEC, configValueParent("BLACKLIST"),
            "BLACKLIST.spec must match COMMON_SPEC (Builder#build wires it)");
        assertSame(BiomeBlacklistConfig.COMMON_SPEC, configValueParent("SEARCH_RADIUS"),
            "SEARCH_RADIUS.spec must match COMMON_SPEC (Builder#build wires it)");
        assertSame(BiomeBlacklistConfig.COMMON_SPEC, configValueParent("SEARCH_STEP"),
            "SEARCH_STEP.spec must match COMMON_SPEC (Builder#build wires it)");
    }

    /**
     * After dispatching a {@link ModConfigEvent.Loading} event whose config
     * is backed by {@link BiomeBlacklistConfig#COMMON_SPEC},
     * {@link BiomeBlacklistConfig#onConfigLoad(ModConfigEvent)} emits a
     * single {@code INFO} line whose message contains both {@code entries=}
     * and {@code enabled=} markers, per Requirement 8.3.
     */
    @Test
    void example_loadInfoLog() throws Exception {
        ModConfig modConfig = newModConfig(BiomeBlacklistConfig.COMMON_SPEC);

        CommentedConfig backing = CommentedConfig.inMemory();
        backing.set(List.of("spawn", "spawnBiomeBlacklistEnabled"),
            BiomeBlacklistConfig.DEFAULT_ENABLED);
        backing.set(List.of("spawn", "spawnBiomeBlacklist"),
            new ArrayList<>(BiomeBlacklistConfig.DEFAULT_BLACKLIST));
        backing.set(List.of("spawn", "spawnSafeSearchRadius"),
            BiomeBlacklistConfig.DEFAULT_SEARCH_RADIUS);
        backing.set(List.of("spawn", "spawnSafeSearchStep"),
            BiomeBlacklistConfig.DEFAULT_SEARCH_STEP);

        IConfigSpec.ILoadedConfig loaded = newLoadedConfig(backing, modConfig);
        installLoadedConfig(BiomeBlacklistConfig.COMMON_SPEC, loaded);

        CapturingAppender appender = CapturingAppender.attach(BiomeBlacklistConfig.class);
        try {
            BiomeBlacklistConfig.onConfigLoad(new ModConfigEvent.Loading(modConfig));

            List<LogEvent> infos = appender.events().stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .toList();
            assertEquals(1, infos.size(),
                "onConfigLoad must emit exactly one INFO event after a Loading dispatch; events=" + infos);

            String message = infos.get(0).getMessage().getFormattedMessage();
            assertTrue(message.contains("entries="),
                "INFO log must contain 'entries=' marker; was=" + message);
            assertTrue(message.contains("enabled="),
                "INFO log must contain 'enabled=' marker; was=" + message);
        } finally {
            appender.detach();
        }
    }

    // ---------------------------------------------------------------------
    // Reflection helpers for the example_specBuilderUsage test
    // ---------------------------------------------------------------------

    /**
     * Reads the named private static {@code IntValue} field from
     * {@link BiomeBlacklistConfig}. Centralised here so the range tests
     * share the same reflection path.
     */
    private static net.neoforged.neoforge.common.ModConfigSpec.IntValue readIntValue(String fieldName)
            throws Exception {
        Field f = BiomeBlacklistConfig.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        Object value = f.get(null);
        assertNotNull(value, fieldName + " must be initialised in <clinit>");
        return (net.neoforged.neoforge.common.ModConfigSpec.IntValue) value;
    }

    /** Asserts a private static field exists with the documented type, and is non-null. */
    private static void assertFieldOfType(String fieldName, Class<?> expectedType) throws Exception {
        Field f = BiomeBlacklistConfig.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        assertTrue(Modifier.isStatic(f.getModifiers()),
            fieldName + " must be static");
        assertTrue(Modifier.isFinal(f.getModifiers()),
            fieldName + " must be final");
        assertTrue(expectedType.isAssignableFrom(f.getType()),
            fieldName + " must be assignable to " + expectedType.getName()
                + "; actual=" + f.getType().getName());
        Object value = f.get(null);
        assertNotNull(value, fieldName + " must be initialised in <clinit>");
    }

    /**
     * Reads {@code ConfigValue.spec} (the package-private backreference set by
     * {@link net.neoforged.neoforge.common.ModConfigSpec.Builder#build()}) for
     * a private static field of {@link BiomeBlacklistConfig}, so the test can
     * assert it points back at {@code COMMON_SPEC}.
     */
    private static net.neoforged.neoforge.common.ModConfigSpec configValueParent(String fieldName)
            throws Exception {
        Field f = BiomeBlacklistConfig.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        Object cv = f.get(null);
        assertNotNull(cv, fieldName + " must be initialised in <clinit>");
        Field specField =
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue.class.getDeclaredField("spec");
        specField.setAccessible(true);
        Object spec = specField.get(cv);
        assertNotNull(spec, fieldName + ".spec must be wired by Builder#build()");
        return (net.neoforged.neoforge.common.ModConfigSpec) spec;
    }
}

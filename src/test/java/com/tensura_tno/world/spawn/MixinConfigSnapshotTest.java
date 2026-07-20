package com.tensura_tno.world.spawn;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Snapshot tests that lock in two static / structural invariants of the
 * spawn-biome-blacklist feature:
 *
 * <ol>
 *   <li>{@link BiomeBlacklistConfig} and {@link SpawnRelocator} compile
 *       without any static dependency on Tensura's
 *       {@code com.github.manasmods.tensura.*} package — i.e. no internal
 *       names starting with {@code com/github/manasmods/tensura/} appear
 *       in their compiled class files' constant pools (Requirement 5.3).
 *   <li>This mod's mixin configuration files do not declare any new
 *       spawn-related mixin entries — the spawn-biome-blacklist feature
 *       must be implemented purely via NeoForge events and SHALL NOT
 *       introduce a new Mixin (Requirement 10.3).
 * </ol>
 *
 * <p>Both tests are simple JUnit 5 example tests; they intentionally do
 * not use jqwik because the assertions are exact / structural rather
 * than universally quantified properties.
 */
class MixinConfigSnapshotTest {

    /**
     * Forbidden internal-name prefix. The slash form is used because the
     * Java class file format stores all type references and class names
     * using {@code /} as the package separator (e.g. type descriptors,
     * CONSTANT_Class entries, method descriptors). Searching for the
     * slash form therefore covers the full surface of the constant pool.
     */
    private static final String FORBIDDEN_TENSURA_PREFIX = "com/github/manasmods/tensura/";

    /**
     * Substring used to detect spawn-related mixin entries. Matched
     * case-insensitively against each declared mixin class name in
     * the {@code mixins} / {@code client} / {@code server} arrays.
     */
    private static final String SPAWN_KEYWORD = "spawn";

    /**
     * Candidate locations relative to the project root where a NeoForge
     * mixin config may live. The current convention in this repo places
     * the file at {@code src/main/resources/tensura_tno.mixins.json};
     * the task spec also references {@code src/main/resources/META-INF/}
     * as a possible location, so we scan both.
     */
    private static final List<String> MIXIN_CONFIG_DIRS = List.of(
            "src/main/resources",
            "src/main/resources/META-INF");

    // ---------------------------------------------------------------------
    // Test 1: no static dependency on com.github.manasmods.tensura.* in
    // BiomeBlacklistConfig / SpawnRelocator class files.
    // Validates Requirement 5.3.
    // ---------------------------------------------------------------------

    /**
     * Asserts that neither {@link BiomeBlacklistConfig} nor
     * {@link SpawnRelocator}'s compiled class file references any internal
     * name starting with {@code com/github/manasmods/tensura/}.
     *
     * <p>This guards Requirement 5.3 (the feature must keep the biome
     * blacklist as plain strings and never link statically against a
     * Tensura class), so that running with the Tensura mod absent does
     * not raise {@code ClassNotFoundException} during class loading.
     *
     * <p>The check uses ASM's {@link ClassReader} to confirm both class
     * files are well-formed JVM class files (asserts on
     * {@code cr.getClassName()}), then performs a constant-pool-wide
     * substring scan: every reference to a Tensura class would be stored
     * in a {@code CONSTANT_Class_info} or {@code CONSTANT_Utf8_info}
     * entry as a UTF-8 byte sequence containing the slash-form internal
     * name, so a verbatim byte-level substring scan is both sufficient
     * and robust.
     */
    @Test
    void example_noStaticTensuraDependency() throws IOException {
        assertClassHasNoTensuraReference(BiomeBlacklistConfig.class);
        assertClassHasNoTensuraReference(SpawnRelocator.class);
    }

    private static void assertClassHasNoTensuraReference(Class<?> clazz) throws IOException {
        byte[] bytes = readClassBytes(clazz);

        // 1. Confirm ASM can parse the class file (and that its declared
        //    internal name is what we expect — i.e. we did not load some
        //    unrelated class through a misconfigured classpath).
        ClassReader cr = new ClassReader(bytes);
        String expectedInternalName = clazz.getName().replace('.', '/');
        assertTrue(cr.getClassName().equals(expectedInternalName),
                "ClassReader reported unexpected internal name for "
                        + clazz.getName() + ": got " + cr.getClassName()
                        + ", expected " + expectedInternalName);

        // 2. Substring scan over the full class file bytes. Every internal
        //    name and type descriptor that references a class lives in
        //    the constant pool as a UTF-8 byte sequence, so a verbatim
        //    byte-level scan covers all reference sites (CONSTANT_Class,
        //    field/method descriptors, generic signatures, NestHost,
        //    InnerClasses, etc.).
        String classBytesAsLatin1 = new String(bytes, StandardCharsets.ISO_8859_1);
        int idx = classBytesAsLatin1.indexOf(FORBIDDEN_TENSURA_PREFIX);
        assertFalse(idx >= 0,
                clazz.getName() + " must not statically reference any class under "
                        + FORBIDDEN_TENSURA_PREFIX
                        + " (Requirement 5.3) but the compiled class file's constant"
                        + " pool contains the substring at byte offset " + idx
                        + ". The blacklist must be held as plain strings only.");
    }

    private static byte[] readClassBytes(Class<?> clazz) throws IOException {
        String resourcePath = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream in = clazz.getResourceAsStream(resourcePath)) {
            assertNotNull(in,
                    "Class file resource not found on test classpath: " + resourcePath);
            return in.readAllBytes();
        }
    }

    // ---------------------------------------------------------------------
    // Test 2: this mod's mixin configs declare no spawn-related mixin
    // entries. Validates Requirement 10.3.
    // ---------------------------------------------------------------------

    /**
     * Asserts that no {@code *.mixins.json} file in this repository
     * declares any mixin class whose simple name contains the substring
     * {@code "spawn"} (case-insensitive) inside the {@code mixins},
     * {@code client}, or {@code server} arrays.
     *
     * <p>This guards Requirement 10.3: the spawn-biome-blacklist feature
     * SHALL NOT introduce a new Mixin. Since the existing mod already
     * ships a mixin config file (for unrelated client / server-side
     * patches), we cannot assert "no mixin config exists" — instead we
     * assert that no entry within those configs is spawn-related, which
     * is the structural property the requirement actually targets.
     *
     * <p>The repo's mixin configs are JSON, so we parse them with Gson
     * (already on the production classpath, see
     * {@code com.tensura_tno.food.FoodEPManager}) rather than relying on
     * regex parsing.
     */
    @Test
    void example_noNewSpawnMixin() throws IOException {
        Path projectRoot = locateProjectRoot();
        List<Path> mixinConfigs = findMixinConfigFiles(projectRoot);

        // Defensive: if we cannot find any *.mixins.json file at all,
        // fail loudly. The existing repo ships at least one, and a future
        // refactor that removes them would silently make this test pass
        // by vacuous truth.
        assertFalse(mixinConfigs.isEmpty(),
                "No *.mixins.json files found under " + MIXIN_CONFIG_DIRS
                        + " (relative to project root " + projectRoot
                        + "). Mixin snapshot test cannot run without at"
                        + " least one config file to scan.");

        List<String> offenders = new ArrayList<>();
        for (Path configFile : mixinConfigs) {
            offenders.addAll(findSpawnRelatedMixinEntries(configFile));
        }

        if (!offenders.isEmpty()) {
            fail("spawn-biome-blacklist must not introduce new spawn-related"
                    + " mixin entries (Requirement 10.3) but the following"
                    + " entries were found in *.mixins.json arrays:\n  - "
                    + String.join("\n  - ", offenders));
        }
    }

    /**
     * Locates the project root directory. Tests are run from either the
     * project root (Gradle CLI) or a build-specific working directory
     * (some IDE configurations). We walk upwards from the current
     * working directory until we find {@code build.gradle}, which is
     * unique to the project root.
     */
    private static Path locateProjectRoot() {
        Path candidate = Paths.get("").toAbsolutePath();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("build.gradle"))
                    && Files.exists(candidate.resolve("src"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        // Fallback: cwd. Caller will fail with a clearer error if the
        // mixin configs cannot be found, which is the actual symptom.
        return Paths.get("").toAbsolutePath();
    }

    private static List<Path> findMixinConfigFiles(Path projectRoot) throws IOException {
        List<Path> result = new ArrayList<>();
        for (String relDir : MIXIN_CONFIG_DIRS) {
            Path dir = projectRoot.resolve(relDir);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.newDirectoryStream(dir, "*.mixins.json")) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) {
                        result.add(entry);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns spawn-related mixin entries (formatted as
     * {@code <file>:<array>:<entry>}) found in the given config file.
     * Empty list if none.
     */
    private static List<String> findSpawnRelatedMixinEntries(Path configFile) throws IOException {
        String content = Files.readString(configFile, StandardCharsets.UTF_8);
        JsonElement root = JsonParser.parseString(content);
        if (!root.isJsonObject()) {
            return List.of();
        }
        JsonObject obj = root.getAsJsonObject();

        List<String> offenders = new ArrayList<>();
        for (String arrayKey : Arrays.asList("mixins", "client", "server")) {
            JsonElement el = obj.get(arrayKey);
            if (el == null || !el.isJsonArray()) {
                continue;
            }
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement item : arr) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                    continue;
                }
                String entry = item.getAsString();
                if (entry.toLowerCase(Locale.ROOT).contains(SPAWN_KEYWORD)) {
                    offenders.add(configFile.getFileName() + ":" + arrayKey + ":" + entry);
                }
            }
        }
        return offenders;
    }
}

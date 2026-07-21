package com.tensura_tno.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinPackageIsolationTest {
    private static final Path CONFIG = Path.of("src/main/resources/tensura_tno.mixins.json");

    @Test
    void definedMixinPackageEmitsNoAuxiliaryClassFiles() throws Exception {
        JsonObject config = JsonParser.parseString(
                Files.readString(CONFIG, StandardCharsets.UTF_8)
        ).getAsJsonObject();
        String mixinPackage = config.get("package").getAsString();
        Path packageDir = Path.of("build/classes/java/main")
                .resolve(mixinPackage.replace('.', '/'));

        assertTrue(Files.isDirectory(packageDir),
                "Compiled Mixin package is missing: " + packageDir);

        for (String section : List.of("mixins", "client", "server")) {
            JsonArray entries = config.getAsJsonArray(section);
            if (entries == null) {
                continue;
            }
            for (var entry : entries) {
                Path classFile = packageDir.resolve(entry.getAsString().replace('.', '/') + ".class");
                assertTrue(Files.isRegularFile(classFile),
                        "Configured Mixin class was not compiled: " + classFile);
            }
        }

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> classes = Files.walk(packageDir)) {
            classes.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .filter(path -> path.getFileName().toString().contains("$"))
                    .map(packageDir::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .sorted()
                    .forEach(offenders::add);
        }

        assertTrue(offenders.isEmpty(), () ->
                "Classes inside a defined Mixin package cannot be loaded directly from transformed target "
                        + "bytecode. Move nested, anonymous, record, enum-switch, and cache helpers outside "
                        + mixinPackage + ". Offenders:\n" + String.join("\n", offenders));
    }
}

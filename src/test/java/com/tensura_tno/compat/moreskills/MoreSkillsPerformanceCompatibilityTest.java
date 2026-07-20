package com.tensura_tno.compat.moreskills;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoreSkillsPerformanceCompatibilityTest {

    private static final Path MIXIN_ROOT = Path.of(
            "src/main/java/com/tensura_tno/mixin/client");

    @Test
    void performanceMixinsAreClientRegisteredAndOptional() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/tensura_tno.mixins.json"), StandardCharsets.UTF_8
        )).getAsJsonObject();
        JsonArray client = config.getAsJsonArray("client");

        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsBlackBloodFogOptimizationMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsPathsPortalStrandPerformanceMixin\"")));

        String fogMixin = read("MoreSkillsBlackBloodFogOptimizationMixin.java");
        String portalMixin = read("MoreSkillsPathsPortalStrandPerformanceMixin.java");
        assertTrue(fogMixin.contains("@Pseudo"));
        assertTrue(portalMixin.contains("@Pseudo"));
        assertTrue(fogMixin.contains("require = 0"));
        assertTrue(portalMixin.contains("require = 0"));
        assertTrue(fogMixin.contains("0.0.4.5"));
        assertTrue(portalMixin.contains("0.0.4.5"));
    }

    @Test
    void blackBloodFogUsesBoundedCachesAndOneBlockStateRead() throws Exception {
        String source = read("MoreSkillsBlackBloodFogOptimizationMixin.java");

        assertTrue(source.contains("TENSURA_TNO$LOCAL_SCAN_INTERVAL_TICKS = 4L"));
        assertTrue(source.contains("TENSURA_TNO$COLUMN_SCAN_INTERVAL_TICKS = 20L"));
        assertTrue(source.contains("BlockPos.MutableBlockPos"));
        assertEquals(1, count(source, "level.getBlockState(cursor)"));
    }

    @Test
    void pathsPortalSearchIsSplitIntoSmallFrameBatches() throws Exception {
        String source = read("MoreSkillsPathsPortalStrandPerformanceMixin.java");

        assertTrue(source.contains("tensuraTno$positionsPerFrame = 1024"));
        assertTrue(source.contains("tensuraTno$totalPositions"));
        assertTrue(source.contains("BlockPos.MutableBlockPos"));
        assertTrue(source.contains("BlockPos.betweenClosed(lower, upper)"),
                "Future MoreSkills versions must retain their original scan path");
    }

    private static String read(String fileName) throws Exception {
        return Files.readString(MIXIN_ROOT.resolve(fileName), StandardCharsets.UTF_8);
    }

    private static int count(String text, String needle) {
        int result = 0;
        int cursor = 0;
        while ((cursor = text.indexOf(needle, cursor)) >= 0) {
            result++;
            cursor += needle.length();
        }
        return result;
    }
}

package com.tensura_tno.compat.moreskills;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoreSkillsPerformanceCompatibilityTest {

    private static final Path MIXIN_ROOT = Path.of(
            "src/main/java/com/tensura_tno/mixin/client");
    private static final Path CLIENT_COMPAT_ROOT = Path.of(
            "src/main/java/com/tensura_tno/client/compat");
    private static final Path EXAMPLE_FX_ROOT = Path.of(
            "例子/moreskills/com/github/wal_bos/moreskills/client/fx");

    @Test
    void performanceMixinsAreClientRegisteredAndOptional() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/tensura_tno.mixins.json"), StandardCharsets.UTF_8
        )).getAsJsonObject();
        JsonArray client = config.getAsJsonArray("client");

        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsBlackBloodFogOptimizationMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsPathsPortalStrandPerformanceMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsElfariaVfxFrameMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsElfariaVfxIteratorMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsElfariaIceSheetOptimizationMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsAlbisVinaImpactOptimizationMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsAlbisVinaBeamOptimizationMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsAsmodayBlackHoleOptimizationMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsAsmodayBlackHoleFrustumCullingMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsAsmodayBlackHoleReflectionCacheMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsAsmodayHotPathCacheMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsAsmodaySpaceOptimizationMixin\"")));
        assertTrue(client.contains(JsonParser.parseString("\"MoreSkillsSatellaMiasmaSurfaceOptimizationMixin\"")));

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
    void satellaDevouringShadowsCachesSurfaceScansWithoutReducingVisualDensity() throws Exception {
        String[] mixinNames = {
                "MoreSkillsSatellaMiasmaSurfaceOptimizationMixin"
        };
        for (String mixinName : mixinNames) {
            String source = read(mixinName + ".java");
            assertTrue(source.contains("@Pseudo"));
            assertTrue(source.contains("require = 0"));
            assertTrue(source.contains("remap = false"));
            assertFalse(source.contains("import com.github.wal_bos"));
            Class<?> mixin = Class.forName(
                    "com.tensura_tno.mixin.client." + mixinName,
                    false,
                    getClass().getClassLoader()
            );
            assertDoesNotThrow(mixin::getDeclaredMethods);
            assertDoesNotThrow(mixin::getDeclaredFields);
        }

        String fixture = readExample("SatellaVfxClient.java");
        assertTrue(fixture.contains("event.getStage() == Stage.AFTER_TRANSLUCENT_BLOCKS"
                + " || event.getStage() == Stage.AFTER_PARTICLES"));
        assertTrue(fixture.contains("private static record BlackMiasma"));
        assertTrue(fixture.contains("private double sampleSurfaceY(double x, double z, double yHint)"));
        assertTrue(fixture.contains("this.level.getBlockState(pos.above()).isAir()"));

        String surface = read("MoreSkillsSatellaMiasmaSurfaceOptimizationMixin.java");
        assertTrue(surface.contains("TENSURA_TNO$CACHE_SIZE = 4096"));
        assertTrue(surface.contains("gameTime - cachedTime <= 4L"));
        assertTrue(surface.contains("slot = slot + 1 & (TENSURA_TNO$CACHE_SIZE - 1)"));
        assertEquals(2, count(surface, "level.getBlockState(cursor)"));
        assertFalse(surface.contains("@Shadow"));
        assertFalse(surface.contains("@ModifyConstant"));
        assertFalse(surface.contains("0.0.4.6"));
    }

    @Test
    void asmodayBlackHoleAndSpaceUseOptionalClientVisualBudgets() throws Exception {
        String[] mixinNames = {
                "MoreSkillsAsmodayBlackHoleOptimizationMixin",
                "MoreSkillsAsmodayBlackHoleFrustumCullingMixin",
                "MoreSkillsAsmodayBlackHoleReflectionCacheMixin",
                "MoreSkillsAsmodayHotPathCacheMixin",
                "MoreSkillsAsmodaySpaceOptimizationMixin"
        };
        for (String mixinName : mixinNames) {
            String source = read(mixinName + ".java");
            assertTrue(source.contains("@Pseudo"));
            assertTrue(source.contains("require = 0"));
            assertTrue(source.contains("remap = false"));
            assertFalse(source.contains("import com.github.wal_bos"));
            Class<?> mixin = Class.forName(
                    "com.tensura_tno.mixin.client." + mixinName,
                    false,
                    getClass().getClassLoader()
            );
            assertDoesNotThrow(mixin::getDeclaredMethods);
        }

        String blackHole = readExample("AsmodayBlackHoleClient.java");
        assertTrue(blackHole.contains("private static void renderBlackSphere"));
        assertTrue(blackHole.contains("private static void renderAccretionVolume"));
        assertTrue(blackHole.contains("private static boolean addCondensedYDiskTube"));

        String space = readExample("AsmodaySpaceClient.java");
        assertTrue(space.contains("private static void renderPhainonVoidDust"));
        assertTrue(space.contains("for(int i = 0; i < 180; ++i)"));
    }

    @Test
    void asmodayFurtherOptimizationsKeepVisibleGeometryUnchanged() throws Exception {
        String fixture = readExample("AsmodayBlackHoleClient.java");
        assertTrue(fixture.contains("private static void renderHole(PoseStack poseStack, Vec3 cam, Camera camera"));
        assertTrue(fixture.contains("float yawCos = (float)Math.cos((double)yaw)"));
        assertTrue(fixture.contains("float yawSin = (float)Math.sin((double)yaw)"));
        assertTrue(fixture.contains("private static void uploadPostUniforms()"));
        assertTrue(fixture.contains("for(Object pass : getPasses(effect))"));
        assertTrue(fixture.contains("private static List<?> getPasses(Object effect)"));
        assertTrue(fixture.contains("private static Object getShader(Object pass)"));
        assertTrue(fixture.contains("private static void setUniform1(Object shader, String name, float value)"));
        assertTrue(fixture.contains("private static void setUniform2(Object shader, String name, float x, float y)"));

        String frustum = read("MoreSkillsAsmodayBlackHoleFrustumCullingMixin.java");
        assertTrue(frustum.contains("MoreSkillsAsmodayFrustumCulling.beginFrame(event)"));
        assertTrue(frustum.contains("MoreSkillsAsmodayFrustumCulling.shouldCull(hole)"));
        assertTrue(frustum.contains("ci.cancel()"));
        assertFalse(frustum.contains("distanceTo"));
        assertFalse(frustum.contains("@ModifyConstant"));
        assertFalse(frustum.contains("record "));
        assertFalse(frustum.contains("new ClassValue"));

        String frustumHelper = readClient("MoreSkillsAsmodayFrustumCulling.java");
        assertTrue(frustumHelper.contains("HORIZONTAL_EXTENT_SCALE = 11.0D"));
        assertTrue(frustumHelper.contains("VERTICAL_EXTENT_SCALE = 6.0D"));
        assertTrue(frustumHelper.contains("return !frustum.isVisible(bounds)"));
        assertFalse(frustumHelper.contains("import com.github.wal_bos"));

        String hotPath = read("MoreSkillsAsmodayHotPathCacheMixin.java");
        assertTrue(hotPath.contains("method = {\"ribbonVertex\", \"yDiskVertex\"}"));
        assertTrue(hotPath.contains("MoreSkillsAsmodayHotPathCache.cos(angle)"));
        assertTrue(hotPath.contains("MoreSkillsAsmodayHotPathCache.sin(angle)"));
        assertFalse(hotPath.contains("ordinal ="));
        assertFalse(hotPath.contains("@ModifyConstant"));

        String hotPathHelper = readClient("MoreSkillsAsmodayHotPathCache.java");
        assertTrue(hotPathHelper.contains("Double.doubleToRawLongBits(angle)"));
        assertTrue(hotPathHelper.contains("Math.cos(angle)"));
        assertTrue(hotPathHelper.contains("Math.sin(angle)"));
        assertFalse(hotPathHelper.contains("import com.github.wal_bos"));

        String reflection = read("MoreSkillsAsmodayBlackHoleReflectionCacheMixin.java");
        assertTrue(reflection.contains("new IdentityHashMap<>()"));
        assertTrue(reflection.contains("tensuraTno$currentEffect != effect"));
        assertTrue(reflection.contains("shaderClass.getMethod(\"safeGetUniform\", String.class)"));
        assertTrue(reflection.contains("resolved.set(value)"));
        assertTrue(reflection.contains("resolved.set(x, y)"));
        assertFalse(reflection.contains("@ModifyConstant"));
    }

    @Test
    void asmodayMixinsDoNotCompileAuxiliaryClassesInsideTheOwnedMixinPackage() throws Exception {
        Path output = Path.of("build/classes/java/main/com/tensura_tno/mixin/client");
        String[] mixins = {
                "MoreSkillsAsmodayBlackHoleFrustumCullingMixin",
                "MoreSkillsAsmodayHotPathCacheMixin"
        };
        for (String mixin : mixins) {
            try (var classes = Files.list(output)) {
                assertFalse(classes.anyMatch(path -> path.getFileName().toString().startsWith(mixin + "$")),
                        mixin + " must not emit helper classes inside the Mixin-owned package");
            }
        }
    }

    @Test
    void albisVinaOptimizationKeepsLifecycleCleanupAndHasNoHardDependency() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/tensura_tno.mixins.json"), StandardCharsets.UTF_8
        )).getAsJsonObject();
        JsonArray common = config.getAsJsonArray("mixins");
        JsonArray client = config.getAsJsonArray("client");

        String[] mixinNames = {
                "MoreSkillsAlbisVinaImpactOptimizationMixin",
                "MoreSkillsAlbisVinaBeamOptimizationMixin"
        };
        for (String mixinName : mixinNames) {
            assertFalse(common.contains(JsonParser.parseString("\"" + mixinName + "\"")));
            assertTrue(client.contains(JsonParser.parseString("\"" + mixinName + "\"")));
            String source = read(mixinName + ".java");
            assertTrue(source.contains("@Pseudo"));
            assertTrue(source.contains("require = 0"));
            assertTrue(source.contains("remap = false"));
            assertFalse(source.contains("import com.github.wal_bos"));

            Class<?> mixin = Class.forName(
                    "com.tensura_tno.mixin.client." + mixinName,
                    false,
                    getClass().getClassLoader()
            );
            assertDoesNotThrow(mixin::getDeclaredMethods);
            assertDoesNotThrow(mixin::getDeclaredFields);
        }

        String source = readExample("AlbisVinaVfxClient.java");
        assertTrue(source.contains("private static void render(RenderLevelStageEvent event)"));
        assertEquals(2, count(source, ".remove();"));
        assertTrue(source.contains("beam.render(matrix, now)"));
        assertTrue(source.contains("impact.render(matrix, now)"));
        assertTrue(source.contains("private double sampleSurfaceY(double x, double z, double yHint)"));

        String impact = read("MoreSkillsAlbisVinaImpactOptimizationMixin.java");
        assertTrue(impact.contains("sampleSurfaceY(DDD)D"));
        assertTrue(impact.contains("TENSURA_TNO$CACHE_SIZE = 4096"));
        assertFalse(impact.contains("0.0.4.5"));
    }

    @Test
    void elfariaVfxOptimizationIsOptionalClientOnlyAndFailOpen() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/tensura_tno.mixins.json"), StandardCharsets.UTF_8
        )).getAsJsonObject();
        JsonArray common = config.getAsJsonArray("mixins");

        String[] mixinNames = {
                "MoreSkillsElfariaVfxFrameMixin",
                "MoreSkillsElfariaVfxIteratorMixin",
                "MoreSkillsElfariaIceSheetOptimizationMixin"
        };
        for (String mixinName : mixinNames) {
            assertFalse(common.contains(JsonParser.parseString("\"" + mixinName + "\"")));
            String source = read(mixinName + ".java");
            assertTrue(source.contains("@Pseudo"));
            assertTrue(source.contains("remap = false"));
            assertTrue(source.contains("require = 0"));
            assertFalse(source.contains("import com.github.wal_bos"));

            Class<?> mixin = Class.forName(
                    "com.tensura_tno.mixin.client." + mixinName,
                    false,
                    getClass().getClassLoader()
            );
            assertDoesNotThrow(mixin::getDeclaredMethods);
            assertDoesNotThrow(mixin::getDeclaredFields);
        }

        String optimizer = readClient("MoreSkillsElfariaVfxOptimizer.java");
        String mesh = readClient("MoreSkillsElfariaIceMeshRenderer.java");
        assertFalse(optimizer.contains("SUPPORTED_VERSION"));
        assertFalse(optimizer.contains("ModList"));
        assertTrue(optimizer.contains("return originalSelection(effects)"));
        assertTrue(mesh.contains("ThreadLocal<DiscScratch>"));
        assertTrue(mesh.contains("buffer.addVertex"));
        assertFalse(optimizer.contains("import com.github.wal_bos"));
        assertFalse(mesh.contains("import com.github.wal_bos"));
    }

    @Test
    void elfariaInjectionPointsMatchTheInspectedSourceFixture() throws Exception {
        String frame = readExample("ElfariaIcemaidenVfxClient.java");
        assertTrue(frame.contains("public static void onClientTick(ClientTickEvent.Post event)"));
        assertTrue(frame.contains("public static void render(RenderLevelStageEvent event)"));

        String[] renderers = {
                "ElfariaMyrdasFridolieteVfxClient.java",
                "ElfariaFruzelCardeneiaVfxClient.java",
                "ElfariaGlaciaLastAlbisVfxClient.java",
                "ElfariaStellasNateaVfxClient.java",
                "ElfariaPassiveIceStepsVfxClient.java",
                "ElfariaArsWeissBloomVfxClient.java",
                "ElfariaOpIceShineVfxClient.java"
        };
        for (String renderer : renderers) {
            String source = readExample(renderer);
            assertTrue(source.contains("public static void render(PoseStack poseStack, float partialTicks)"));
            assertTrue(source.contains(".isEmpty()"));
            assertTrue(source.contains(" : "), renderer + " must retain enhanced-for List iteration");
        }

        String iceSheets = readExample("ElfariaAlbisIceSheetRenderer.java");
        assertTrue(iceSheets.contains("public static void albisDisc(BufferBuilder buffer"));
        assertTrue(iceSheets.contains("public static void albisRectSheet(BufferBuilder buffer"));
        assertTrue(iceSheets.contains("public static void albisDiscLines(BufferBuilder buffer"));
        assertTrue(iceSheets.contains("public static void albisSheen(BufferBuilder buffer"));
        assertTrue(iceSheets.contains("int rings = 18"));
        assertTrue(iceSheets.contains("int seg = 144"));
        assertTrue(iceSheets.contains("int uSteps = 18"));
        assertTrue(iceSheets.contains("int vSteps = 26"));
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
        String batch = readClient("MoreSkillsPathsPortalScanBatch.java");

        assertTrue(source.contains("tensuraTno$positionsPerFrame = 1024"));
        assertTrue(source.contains("tensuraTno$totalPositions"));
        assertTrue(source.contains("MoreSkillsPathsPortalScanBatch.iterable("));
        assertTrue(batch.contains("BlockPos.MutableBlockPos"));
        assertTrue(batch.contains("implements Iterator<BlockPos>"));
        assertTrue(source.contains("BlockPos.betweenClosed(lower, upper)"),
                "Future MoreSkills versions must retain their original scan path");
    }

    private static String read(String fileName) throws Exception {
        return Files.readString(MIXIN_ROOT.resolve(fileName), StandardCharsets.UTF_8);
    }

    private static String readClient(String fileName) throws Exception {
        return Files.readString(CLIENT_COMPAT_ROOT.resolve(fileName), StandardCharsets.UTF_8);
    }

    private static String readExample(String fileName) throws Exception {
        return Files.readString(EXAMPLE_FX_ROOT.resolve(fileName), StandardCharsets.UTF_8);
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

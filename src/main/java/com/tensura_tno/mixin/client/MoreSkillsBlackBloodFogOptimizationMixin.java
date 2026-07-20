package com.tensura_tno.mixin.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reduces the render-thread block scanning performed by MoreSkills 0.0.4.5's
 * black-blood fog handler.
 *
 * <p>The original handler computes its local fog target once for fog colour
 * and again for fog distance every rendered frame. Each computation can read
 * more than eighty thousand block states. This mixin only replaces the target
 * strength scan; MoreSkills remains responsible for fog colour, distance,
 * Blood Arts sight and smoothing.</p>
 *
 * <p>The small nearby scan is refreshed every four client ticks. The much
 * larger 96-block-deep column scan is refreshed once per second. Large camera
 * movement forces an earlier refresh, so teleporting does not retain fog from
 * the old location. Mutable positions and one block-state read per tested
 * coordinate avoid the original allocation and repeated-read overhead.</p>
 */
@Pseudo
@Mixin(
        targets = "com.github.wal_bos.moreskills.client.fx.BlackBloodDomainFogHandler",
        remap = false
)
public abstract class MoreSkillsBlackBloodFogOptimizationMixin {
    @Unique
    private static final String TENSURA_TNO$SUPPORTED_VERSION = "0.0.4.5";
    @Unique
    private static final long TENSURA_TNO$LOCAL_SCAN_INTERVAL_TICKS = 4L;
    @Unique
    private static final long TENSURA_TNO$COLUMN_SCAN_INTERVAL_TICKS = 20L;
    @Unique
    private static final long TENSURA_TNO$LOCAL_FORCE_RESCAN_DISTANCE_SQR = 64L;
    @Unique
    private static final long TENSURA_TNO$COLUMN_FORCE_RESCAN_DISTANCE_SQR = 256L;

    @Unique
    private static final ResourceLocation[] TENSURA_TNO$INFECTED_BLOCK_IDS = {
            ResourceLocation.fromNamespaceAndPath("tensuramoreskills", "black_blood_mire"),
            ResourceLocation.fromNamespaceAndPath("tensuramoreskills", "infected_grass"),
            ResourceLocation.fromNamespaceAndPath("tensuramoreskills", "infected_dirt"),
            ResourceLocation.fromNamespaceAndPath("tensuramoreskills", "infected_wood"),
            ResourceLocation.fromNamespaceAndPath("tensuramoreskills", "infected_leaves"),
            ResourceLocation.fromNamespaceAndPath("tensuramoreskills", "blood_altar")
    };

    @Unique
    private static Boolean tensuraTno$useOptimization;
    @Unique
    private static Block[] tensuraTno$infectedBlocks;
    @Unique
    private static ClientLevel tensuraTno$cachedLevel;
    @Unique
    private static long tensuraTno$lastObservedGameTime = Long.MIN_VALUE;

    @Unique
    private static long tensuraTno$localScanGameTime = Long.MIN_VALUE;
    @Unique
    private static int tensuraTno$localCenterX;
    @Unique
    private static int tensuraTno$localCenterY;
    @Unique
    private static int tensuraTno$localCenterZ;
    @Unique
    private static float tensuraTno$localStrength;

    @Unique
    private static long tensuraTno$columnScanGameTime = Long.MIN_VALUE;
    @Unique
    private static int tensuraTno$columnCenterX;
    @Unique
    private static int tensuraTno$columnCenterY;
    @Unique
    private static int tensuraTno$columnCenterZ;
    @Unique
    private static float tensuraTno$columnStrength;

    /**
     * Target in MoreSkills 0.0.4.5:
     * {@code private static float getLocalBloodFogStrength(ClientLevel, BlockPos)}.
     */
    @Inject(
            method = "getLocalBloodFogStrength(Lnet/minecraft/client/multiplayer/ClientLevel;"
                    + "Lnet/minecraft/core/BlockPos;)F",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void tensuraTno$useCachedBloodFogStrength(
            ClientLevel level,
            BlockPos center,
            CallbackInfoReturnable<Float> cir
    ) {
        if (!tensuraTno$shouldUseOptimization()) {
            return;
        }

        tensuraTno$resetForLevelOrClockChange(level);
        float localStrength = tensuraTno$getCachedLocalStrength(level, center);
        if (localStrength >= 1.0F) {
            cir.setReturnValue(1.0F);
            return;
        }

        float columnStrength = tensuraTno$getCachedColumnStrength(level, center);
        cir.setReturnValue(Mth.clamp(Math.max(localStrength, columnStrength), 0.0F, 1.0F));
    }

    @Unique
    private static boolean tensuraTno$shouldUseOptimization() {
        if (tensuraTno$useOptimization == null) {
            String version = ModList.get().getModContainerById("tensuramoreskills")
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("");
            if (version.startsWith("v")) {
                version = version.substring(1);
            }
            tensuraTno$useOptimization = version.equals(TENSURA_TNO$SUPPORTED_VERSION)
                    || version.startsWith(TENSURA_TNO$SUPPORTED_VERSION + "+");
        }
        return tensuraTno$useOptimization;
    }

    @Unique
    private static void tensuraTno$resetForLevelOrClockChange(ClientLevel level) {
        long gameTime = level.getGameTime();
        if (tensuraTno$cachedLevel != level || gameTime < tensuraTno$lastObservedGameTime) {
            tensuraTno$cachedLevel = level;
            tensuraTno$localScanGameTime = Long.MIN_VALUE;
            tensuraTno$columnScanGameTime = Long.MIN_VALUE;
            tensuraTno$localStrength = 0.0F;
            tensuraTno$columnStrength = 0.0F;
        }
        tensuraTno$lastObservedGameTime = gameTime;
    }

    @Unique
    private static float tensuraTno$getCachedLocalStrength(ClientLevel level, BlockPos center) {
        long gameTime = level.getGameTime();
        if (tensuraTno$scanExpired(
                gameTime,
                tensuraTno$localScanGameTime,
                TENSURA_TNO$LOCAL_SCAN_INTERVAL_TICKS
        ) || tensuraTno$movedTooFar(
                center,
                tensuraTno$localCenterX,
                tensuraTno$localCenterY,
                tensuraTno$localCenterZ,
                TENSURA_TNO$LOCAL_FORCE_RESCAN_DISTANCE_SQR
        )) {
            tensuraTno$localStrength = tensuraTno$scanNearbyBlockStrength(level, center);
            tensuraTno$localScanGameTime = gameTime;
            tensuraTno$localCenterX = center.getX();
            tensuraTno$localCenterY = center.getY();
            tensuraTno$localCenterZ = center.getZ();
        }
        return tensuraTno$localStrength;
    }

    @Unique
    private static float tensuraTno$getCachedColumnStrength(ClientLevel level, BlockPos center) {
        long gameTime = level.getGameTime();
        if (tensuraTno$scanExpired(
                gameTime,
                tensuraTno$columnScanGameTime,
                TENSURA_TNO$COLUMN_SCAN_INTERVAL_TICKS
        ) || tensuraTno$movedTooFar(
                center,
                tensuraTno$columnCenterX,
                tensuraTno$columnCenterY,
                tensuraTno$columnCenterZ,
                TENSURA_TNO$COLUMN_FORCE_RESCAN_DISTANCE_SQR
        )) {
            tensuraTno$columnStrength = tensuraTno$scanHiddenColumnStrength(level, center);
            tensuraTno$columnScanGameTime = gameTime;
            tensuraTno$columnCenterX = center.getX();
            tensuraTno$columnCenterY = center.getY();
            tensuraTno$columnCenterZ = center.getZ();
        }
        return tensuraTno$columnStrength;
    }

    @Unique
    private static boolean tensuraTno$scanExpired(long gameTime, long scanTime, long interval) {
        return scanTime == Long.MIN_VALUE || gameTime < scanTime || gameTime - scanTime >= interval;
    }

    @Unique
    private static boolean tensuraTno$movedTooFar(
            BlockPos center,
            int cachedX,
            int cachedY,
            int cachedZ,
            long maximumDistanceSqr
    ) {
        long dx = (long) center.getX() - cachedX;
        long dy = (long) center.getY() - cachedY;
        long dz = (long) center.getZ() - cachedZ;
        return dx * dx + dy * dy + dz * dz > maximumDistanceSqr;
    }

    @Unique
    private static float tensuraTno$scanNearbyBlockStrength(ClientLevel level, BlockPos center) {
        int weighted = 0;
        int baseX = center.getX();
        int baseY = center.getY();
        int baseZ = center.getZ();
        int chunkCheckY = Mth.clamp(baseY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = -10; x <= 10; x += 2) {
            int worldX = baseX + x;
            for (int z = -10; z <= 10; z += 2) {
                int worldZ = baseZ + z;
                cursor.set(worldX, chunkCheckY, worldZ);
                if (!level.hasChunkAt(cursor)) {
                    continue;
                }

                for (int y = -6; y <= 6; y += 2) {
                    int worldY = baseY + y;
                    int weight = tensuraTno$getLocalWeight(x, y, z);
                    if (tensuraTno$isInfected(level, cursor, worldX, worldY, worldZ)) {
                        weighted += weight;
                    } else if (tensuraTno$isInfected(level, cursor, worldX, worldY - 1, worldZ)
                            || tensuraTno$isInfected(level, cursor, worldX, worldY + 1, worldZ)) {
                        weighted += Math.max(1, weight - 1);
                    }

                    if (weighted >= 165) {
                        return 1.0F;
                    }
                }
            }
        }

        return Mth.clamp((float) weighted / 165.0F, 0.0F, 1.0F);
    }

    @Unique
    private static float tensuraTno$scanHiddenColumnStrength(ClientLevel level, BlockPos center) {
        int weighted = 0;
        int baseX = center.getX();
        int baseZ = center.getZ();

        for (int x = -18; x <= 18; x += 3) {
            for (int z = -18; z <= 18; z += 3) {
                int columnWeight = tensuraTno$getColumnHorizontalWeight(x, z);
                if (tensuraTno$hasInfectedColumnBelow(
                        level,
                        baseX + x,
                        center.getY(),
                        baseZ + z
                )) {
                    weighted += columnWeight;
                    if (weighted >= 180) {
                        return 1.0F;
                    }
                }
            }
        }

        return Mth.clamp((float) weighted / 180.0F, 0.0F, 1.0F);
    }

    @Unique
    private static boolean tensuraTno$hasInfectedColumnBelow(
            ClientLevel level,
            int x,
            int startY,
            int z
    ) {
        int chunkCheckY = Mth.clamp(startY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, chunkCheckY, z);
        if (!level.hasChunkAt(cursor)) {
            return false;
        }

        int minY = level.getMinBuildHeight();
        for (int down = 0; down <= 96; down += 3) {
            int y = startY - down;
            if (y < minY) {
                break;
            }
            if (tensuraTno$isInfected(level, cursor, x, y, z)
                    || tensuraTno$isInfected(level, cursor, x, y - 1, z)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean tensuraTno$isInfected(
            ClientLevel level,
            BlockPos.MutableBlockPos cursor,
            int x,
            int y,
            int z
    ) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return false;
        }

        cursor.set(x, y, z);
        BlockState state = level.getBlockState(cursor);
        Block block = state.getBlock();
        for (Block infectedBlock : tensuraTno$getInfectedBlocks()) {
            if (block == infectedBlock) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static Block[] tensuraTno$getInfectedBlocks() {
        if (tensuraTno$infectedBlocks == null) {
            List<Block> blocks = new ArrayList<>(TENSURA_TNO$INFECTED_BLOCK_IDS.length);
            for (ResourceLocation id : TENSURA_TNO$INFECTED_BLOCK_IDS) {
                BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add);
            }
            tensuraTno$infectedBlocks = blocks.toArray(Block[]::new);
        }
        return tensuraTno$infectedBlocks;
    }

    @Unique
    private static int tensuraTno$getColumnHorizontalWeight(int x, int z) {
        int distance = Math.abs(x) + Math.abs(z);
        if (distance <= 6) {
            return 8;
        }
        if (distance <= 14) {
            return 5;
        }
        return distance <= 24 ? 3 : 1;
    }

    @Unique
    private static int tensuraTno$getLocalWeight(int x, int y, int z) {
        int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
        if (distance <= 4) {
            return 6;
        }
        return distance <= 10 ? 4 : 1;
    }
}

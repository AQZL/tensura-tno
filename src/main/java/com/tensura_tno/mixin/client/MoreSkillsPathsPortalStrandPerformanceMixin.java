package com.tensura_tno.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Constant;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Spreads MoreSkills' Paths portal search over multiple render frames.
 *
 * <p>MoreSkills 0.0.4.5 searches a 65 x 33 x 65 box in one render frame,
 * reading 139,425 block states even when no portal exists.  The original
 * search runs again every 21 rendered frames, which creates both a large
 * render-thread spike and substantial continuous block lookup traffic.</p>
 *
 * <p>This compatibility patch retains the complete original search volume
 * in every dimension (a Paths portal can exist on either side of the
 * teleport), but visits it incrementally from the centre outwards.  Existing
 * strands continue to be validated by the original renderer every frame.</p>
 */
@Pseudo
@Mixin(targets = "com.github.wal_bos.moreskills.client.fx.PathsPortalStrandRenderer", remap = false)
public abstract class MoreSkillsPathsPortalStrandPerformanceMixin {

    @Unique
    private static final String tensuraTno$supportedVersion = "0.0.4.5";
    @Unique
    private static final int tensuraTno$horizontalRadius = 32;
    @Unique
    private static final int tensuraTno$verticalRadius = 16;
    @Unique
    private static final int tensuraTno$verticalDiameter = tensuraTno$verticalRadius * 2 + 1;
    @Unique
    private static final int tensuraTno$columnCount =
        (tensuraTno$horizontalRadius * 2 + 1) * (tensuraTno$horizontalRadius * 2 + 1);
    @Unique
    private static final int tensuraTno$totalPositions = tensuraTno$columnCount * tensuraTno$verticalDiameter;
    @Unique
    private static final int tensuraTno$positionsPerFrame = 1024;

    @Unique
    private static int[] tensuraTno$columnX;
    @Unique
    private static int[] tensuraTno$columnZ;

    @Unique
    private static ClientLevel tensuraTno$scanLevel;
    @Unique
    private static BlockPos tensuraTno$scanCenter;
    @Unique
    private static int tensuraTno$scanIndex;
    @Unique
    private static Boolean tensuraTno$useOptimization;

    @Unique
    private static void tensuraTno$initializeColumns() {
        if (tensuraTno$columnX != null) {
            return;
        }

        tensuraTno$columnX = new int[tensuraTno$columnCount];
        tensuraTno$columnZ = new int[tensuraTno$columnCount];
        int column = 0;
        for (int radius = 0; radius <= tensuraTno$horizontalRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) == radius) {
                        tensuraTno$columnX[column] = x;
                        tensuraTno$columnZ[column] = z;
                        column++;
                    }
                }
            }
        }
    }

    /** Make the original scan gate eligible every frame; the redirect below supplies only one batch. */
    @ModifyConstant(
        method = "tickStrands",
        constant = @Constant(intValue = 20),
        remap = false,
        require = 0
    )
    private static int tensuraTno$scanEveryFrame(int originalCooldown) {
        return tensuraTno$shouldUseOptimization() ? 0 : originalCooldown;
    }

    @Redirect(
        method = "tickStrands",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;",
            remap = true
        ),
        remap = false,
        require = 0
    )
    private static Iterable<BlockPos> tensuraTno$scanPortalBlocksIncrementally(BlockPos lower, BlockPos upper) {
        if (!tensuraTno$shouldUseOptimization()) {
            return BlockPos.betweenClosed(lower, upper);
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return java.util.List.of();
        }

        tensuraTno$initializeColumns();

        BlockPos currentCenter = new BlockPos(
            (lower.getX() + upper.getX()) >> 1,
            (lower.getY() + upper.getY()) >> 1,
            (lower.getZ() + upper.getZ()) >> 1
        );

        if (tensuraTno$mustRestartScan(level, currentCenter)) {
            tensuraTno$scanLevel = level;
            tensuraTno$scanCenter = currentCenter;
            tensuraTno$scanIndex = 0;
        }

        int start = tensuraTno$scanIndex;
        int end = Math.min(start + tensuraTno$positionsPerFrame, tensuraTno$totalPositions);
        tensuraTno$scanIndex = end;
        BlockPos anchor = tensuraTno$scanCenter;

        return () -> new Iterator<>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private int index = start;

            @Override
            public boolean hasNext() {
                return this.index < end;
            }

            @Override
            public BlockPos next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                int positionIndex = this.index++;
                int columnIndex = positionIndex / tensuraTno$verticalDiameter;
                int yOffset = positionIndex % tensuraTno$verticalDiameter - tensuraTno$verticalRadius;
                return this.cursor.set(
                    anchor.getX() + tensuraTno$columnX[columnIndex],
                    anchor.getY() + yOffset,
                    anchor.getZ() + tensuraTno$columnZ[columnIndex]
                );
            }
        };
    }

    @Unique
    private static boolean tensuraTno$mustRestartScan(ClientLevel level, BlockPos currentCenter) {
        if (tensuraTno$scanLevel != level || tensuraTno$scanCenter == null || tensuraTno$scanIndex >= tensuraTno$totalPositions) {
            return true;
        }

        // Do not reset for ordinary movement: that could starve the outer part
        // of the scan.  Re-anchor only after the camera leaves the old box.
        return Math.abs(currentCenter.getX() - tensuraTno$scanCenter.getX()) > tensuraTno$horizontalRadius
            || Math.abs(currentCenter.getY() - tensuraTno$scanCenter.getY()) > tensuraTno$verticalRadius
            || Math.abs(currentCenter.getZ() - tensuraTno$scanCenter.getZ()) > tensuraTno$horizontalRadius;
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
            tensuraTno$useOptimization = version.equals(tensuraTno$supportedVersion)
                    || version.startsWith(tensuraTno$supportedVersion + "+");
        }
        return tensuraTno$useOptimization;
    }
}

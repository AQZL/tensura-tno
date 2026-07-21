package com.tensura_tno.client.compat;

import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.core.BlockPos;

/** Creates incremental Paths portal scan batches outside the Mixin-owned package. */
public final class MoreSkillsPathsPortalScanBatch {
    private MoreSkillsPathsPortalScanBatch() {
    }

    public static Iterable<BlockPos> iterable(
            BlockPos anchor,
            int[] columnX,
            int[] columnZ,
            int verticalDiameter,
            int verticalRadius,
            int start,
            int end
    ) {
        return () -> new BatchIterator(
                anchor,
                columnX,
                columnZ,
                verticalDiameter,
                verticalRadius,
                start,
                end
        );
    }

    private static final class BatchIterator implements Iterator<BlockPos> {
        private final BlockPos anchor;
        private final int[] columnX;
        private final int[] columnZ;
        private final int verticalDiameter;
        private final int verticalRadius;
        private final int end;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private int index;

        private BatchIterator(
                BlockPos anchor,
                int[] columnX,
                int[] columnZ,
                int verticalDiameter,
                int verticalRadius,
                int start,
                int end
        ) {
            this.anchor = anchor;
            this.columnX = columnX;
            this.columnZ = columnZ;
            this.verticalDiameter = verticalDiameter;
            this.verticalRadius = verticalRadius;
            this.index = start;
            this.end = end;
        }

        @Override
        public boolean hasNext() {
            return index < end;
        }

        @Override
        public BlockPos next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            int positionIndex = index++;
            int columnIndex = positionIndex / verticalDiameter;
            int yOffset = positionIndex % verticalDiameter - verticalRadius;
            return cursor.set(
                    anchor.getX() + columnX[columnIndex],
                    anchor.getY() + yOffset,
                    anchor.getZ() + columnZ[columnIndex]
            );
        }
    }
}

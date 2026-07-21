package com.tensura_tno.client.compat;

/** Reuses exact trigonometric results for Asmoday's hottest meshes. */
public final class MoreSkillsAsmodayHotPathCache {
    private static final int CACHE_SIZE = 8192;
    private static final int CACHE_MASK = CACHE_SIZE - 1;
    private static final byte SIN_PRESENT = 1;
    private static final byte COS_PRESENT = 2;

    private static final ThreadLocal<TrigonometryCache> CACHE =
            ThreadLocal.withInitial(TrigonometryCache::new);

    private MoreSkillsAsmodayHotPathCache() {
    }

    public static double sin(double angle) {
        return CACHE.get().sin(angle);
    }

    public static double cos(double angle) {
        return CACHE.get().cos(angle);
    }

    static void resetForTests() {
        CACHE.remove();
    }

    private static int slot(long bits) {
        long mixed = bits;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return (int) mixed & CACHE_MASK;
    }

    private static final class TrigonometryCache {
        private final long[] keys = new long[CACHE_SIZE];
        private final byte[] present = new byte[CACHE_SIZE];
        private final double[] sine = new double[CACHE_SIZE];
        private final double[] cosine = new double[CACHE_SIZE];

        private double sin(double angle) {
            long bits = Double.doubleToRawLongBits(angle);
            int slot = slot(bits);
            byte flags = present[slot];
            if (flags != 0 && keys[slot] == bits && (flags & SIN_PRESENT) != 0) {
                return sine[slot];
            }

            double result = Math.sin(angle);
            if (flags == 0 || keys[slot] != bits) {
                keys[slot] = bits;
                present[slot] = SIN_PRESENT;
            } else {
                present[slot] = (byte) (flags | SIN_PRESENT);
            }
            sine[slot] = result;
            return result;
        }

        private double cos(double angle) {
            long bits = Double.doubleToRawLongBits(angle);
            int slot = slot(bits);
            byte flags = present[slot];
            if (flags != 0 && keys[slot] == bits && (flags & COS_PRESENT) != 0) {
                return cosine[slot];
            }

            double result = Math.cos(angle);
            if (flags == 0 || keys[slot] != bits) {
                keys[slot] = bits;
                present[slot] = COS_PRESENT;
            } else {
                present[slot] = (byte) (flags | COS_PRESENT);
            }
            cosine[slot] = result;
            return result;
        }
    }
}

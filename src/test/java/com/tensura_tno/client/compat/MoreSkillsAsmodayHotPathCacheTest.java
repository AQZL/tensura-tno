package com.tensura_tno.client.compat;

import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoreSkillsAsmodayHotPathCacheTest {
    @Test
    void cachedTrigonometryIsRawBitEquivalentToMath() {
        MoreSkillsAsmodayHotPathCache.resetForTests();
        double[] edgeCases = {
                0.0D,
                -0.0D,
                Math.PI,
                -Math.PI,
                Double.MIN_VALUE,
                Double.MAX_VALUE,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NaN,
                Double.longBitsToDouble(0x7ff8000000000042L)
        };
        for (double value : edgeCases) {
            assertTrigMatches(value);
            assertTrigMatches(value);
        }

        Random random = new Random(0x41534D4F444159L);
        for (int index = 0; index < 20_000; index++) {
            double value = Double.longBitsToDouble(random.nextLong());
            assertTrigMatches(value);
            if ((index & 7) == 0) {
                assertTrigMatches(value);
            }
        }
    }

    private static void assertTrigMatches(double value) {
        assertEquals(
                Double.doubleToRawLongBits(Math.sin(value)),
                Double.doubleToRawLongBits(MoreSkillsAsmodayHotPathCache.sin(value))
        );
        assertEquals(
                Double.doubleToRawLongBits(Math.cos(value)),
                Double.doubleToRawLongBits(MoreSkillsAsmodayHotPathCache.cos(value))
        );
    }
}

package com.mandelbulb.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MathUtilsTest {

    @Test
    void integerPowerMatchesMathPow() {
        for (int n = 0; n <= 20; n++) {
            for (double b : new double[] {0, 0.5, 1.1, -1.3, 2.0, 7.25}) {
                assertEquals(Math.pow(b, n), MathUtils.ipow(b, n), Math.abs(Math.pow(b, n)) * 1e-13 + 1e-300, "b=" + b + " n=" + n);
            }
        }
    }

    @Test
    void clampAndSanitize() {
        assertEquals(1, MathUtils.clamp(5.0, 0, 1));
        assertEquals(0, MathUtils.clamp(-5.0, 0, 1));
        assertEquals(3, MathUtils.sanitize(Double.NaN, 0, 10, 3));
        assertEquals(3, MathUtils.sanitize(Double.POSITIVE_INFINITY, 0, 10, 3));
        assertEquals(10, MathUtils.sanitize(99, 0, 10, 3));
    }

    @Test
    void mirrorWrapIsContinuousAndPeriodic() {
        assertEquals(0.25, MathUtils.mirrorWrap(0.25), 1e-12);
        assertEquals(0.75, MathUtils.mirrorWrap(1.25), 1e-12);
        assertEquals(0.25, MathUtils.mirrorWrap(2.25), 1e-12);
        assertEquals(0.5, MathUtils.mirrorWrap(-0.5), 1e-12);
        for (double t = -3; t < 3; t += 0.01) {
            assertEquals(MathUtils.mirrorWrap(t), MathUtils.mirrorWrap(t + 1e-9), 1e-8, "continuous at " + t);
        }
    }

    @Test
    void catmullRomInterpolatesEndpoints() {
        assertEquals(2, MathUtils.catmullRom(1, 2, 5, 9, 0), 1e-12);
        assertEquals(5, MathUtils.catmullRom(1, 2, 5, 9, 1), 1e-12);
    }

    @Test
    void wrapAngle() {
        assertEquals(0, MathUtils.wrapAngle(MathUtils.TAU), 1e-12);
        assertEquals(-Math.PI / 2, MathUtils.wrapAngle(3 * Math.PI / 2), 1e-12);
    }
}

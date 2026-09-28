package com.mandelbulb.fractal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MandelbulbTest {

    /** Textbook distance estimator using acos/atan2/sin/cos, as a reference for the fast path. */
    private static double referenceDE(double px, double py, double pz, double n, int maxIter, double bailout) {
        double x = px, y = py, z = pz, dr = 1, r = 0;
        double[] out = new double[6];
        for (int i = 0; i < maxIter; i++) {
            r = Math.sqrt(x * x + y * y + z * z);
            if (r > bailout) break;
            dr = Math.pow(r, n - 1) * n * dr + 1;
            Mandelbulb.sphericalPower(x, y, z, n, out);
            x = out[0] + px;
            y = out[1] + py;
            z = out[2] + pz;
        }
        r = Math.sqrt(x * x + y * y + z * z);
        return 0.5 * Math.log(r) * r / dr;
    }

    @Test
    void sphericalPowerMatchesDefinition() {
        double[] out = new double[6];
        double x = 0.3, y = -0.4, z = 0.5, n = 8;
        Mandelbulb.sphericalPower(x, y, z, n, out);
        double r = Math.sqrt(x * x + y * y + z * z);
        double theta = Math.acos(z / r), phi = Math.atan2(y, x);
        assertEquals(Math.pow(r, n) * Math.sin(n * theta) * Math.cos(n * phi), out[0], 1e-15);
        assertEquals(Math.pow(r, n) * Math.sin(n * theta) * Math.sin(n * phi), out[1], 1e-15);
        assertEquals(Math.pow(r, n) * Math.cos(n * theta), out[2], 1e-15);
        // |v^n| = |v|^n
        assertEquals(Math.pow(r, n), Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]), 1e-12);
    }

    @Test
    void equatorialPointsMapToThePoleForPowerTwo() {
        // On the equator theta = pi/2, so n theta = pi: sin(pi) = 0 and cos(pi) = -1.
        // With this (acos-based) convention v^2 of an equatorial unit vector is the south pole.
        double[] out = new double[6];
        Mandelbulb.sphericalPower(0.6, 0.8, 0, 2, out);
        assertEquals(0.0, out[0], 1e-12);
        assertEquals(0.0, out[1], 1e-12);
        assertEquals(-1.0, out[2], 1e-12);
    }

    @Test
    void polarAngleMultipliesAsInComplexExponentiation() {
        // In the x-z half plane (phi = 0) the map acts on (sin theta, cos theta) like
        // complex exponentiation acts on (Im, Re): the angle multiplies by n, the length is powered.
        double[] out = new double[6];
        double theta = 0.3, r = 0.9, n = 5;
        Mandelbulb.sphericalPower(r * Math.sin(theta), 0, r * Math.cos(theta), n, out);
        assertEquals(Math.pow(r, n) * Math.sin(n * theta), out[0], 1e-12);
        assertEquals(0.0, out[1], 1e-12);
        assertEquals(Math.pow(r, n) * Math.cos(n * theta), out[2], 1e-12);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 8, 9, 12, 16, 20})
    void integerFastPathEqualsTrigonometricReference(int power) {
        Mandelbulb mb = new Mandelbulb(power, 12, 4);
        assertTrue(mb.usesIntegerFastPath());
        Random rnd = new Random(power);
        for (int i = 0; i < 400; i++) {
            double x = rnd.nextDouble() * 3 - 1.5, y = rnd.nextDouble() * 3 - 1.5, z = rnd.nextDouble() * 3 - 1.5;
            double expected = referenceDE(x, y, z, power, 12, 4);
            double actual = mb.distance(x, y, z);
            assertEquals(expected, actual, 1e-9 * Math.max(1, Math.abs(expected)), "at " + x + "," + y + "," + z);
        }
    }

    @Test
    void fractionalPowerIsFiniteAndContinuous() {
        Mandelbulb frac = new Mandelbulb(7.5, 15, 4);
        assertFalse(frac.usesIntegerFastPath());
        Random rnd = new Random(7);
        for (int i = 0; i < 300; i++) {
            double x = rnd.nextGaussian(), y = rnd.nextGaussian(), z = rnd.nextGaussian();
            double d = frac.distance(x, y, z);
            assertTrue(Double.isFinite(d));
            assertEquals(referenceDE(x, y, z, 7.5, 15, 4), d, 1e-9 * Math.max(1, Math.abs(d)));
        }
        // An almost-integer power (trig path) must agree with the integer fast path.
        Mandelbulb almost = new Mandelbulb(8 + 1e-7, 15, 4);
        Mandelbulb exact = new Mandelbulb(8, 15, 4);
        assertFalse(almost.usesIntegerFastPath());
        for (int i = 0; i < 200; i++) {
            double x = rnd.nextDouble() * 2.4 - 1.2, y = rnd.nextDouble() * 2.4 - 1.2, z = rnd.nextDouble() * 2.4 - 1.2;
            double a = almost.distance(x, y, z), b = exact.distance(x, y, z);
            assertEquals(b, a, 1e-4 * Math.max(1e-3, Math.abs(b)));
        }
    }

    @Test
    void originIsInsideTheSet() {
        Mandelbulb mb = new Mandelbulb(8, 50, 4);
        assertTrue(mb.contains(0, 0, 0));
        assertEquals(50, mb.escapeIterations(0, 0, 0));
        double d = mb.distance(0, 0, 0);
        assertTrue(Double.isFinite(d));
        assertTrue(d <= 0, "origin is not outside the surface");
    }

    @Test
    void farPointEscapesImmediatelyWithLargePositiveDistance() {
        Mandelbulb mb = new Mandelbulb(8, 15, 4);
        assertEquals(0, mb.escapeIterations(10, 0, 0));
        double d = mb.distance(10, 0, 0);
        // With r > bailout at the first test, DE = 0.5 r log r.
        assertEquals(0.5 * 10 * Math.log(10), d, 1e-12);
        assertFalse(mb.contains(10, 0, 0));
    }

    @Test
    void outsideTheEscapeBoundEveryPointEscapes() {
        // |c| > 2^(1/(n-1)) guarantees escape (see boundingRadius derivation).
        for (int n : new int[] {2, 3, 8, 16}) {
            Mandelbulb mb = new Mandelbulb(n, 200, 4);
            double bound = Math.pow(2, 1.0 / (n - 1)) * 1.001;
            Random rnd = new Random(n);
            for (int i = 0; i < 200; i++) {
                double x = rnd.nextGaussian(), y = rnd.nextGaussian(), z = rnd.nextGaussian();
                double len = Math.sqrt(x * x + y * y + z * z);
                assertFalse(mb.contains(x / len * bound, y / len * bound, z / len * bound), "n=" + n);
            }
            assertTrue(mb.boundingRadius() >= bound);
        }
    }

    @Test
    void verySmallRadiusIsNumericallySafe() {
        Mandelbulb mb = new Mandelbulb(8, 30, 4);
        for (double tiny : new double[] {1e-300, 1e-200, 1e-160, 1e-20, Double.MIN_VALUE}) {
            double d = mb.distance(tiny, 0, 0);
            assertTrue(Double.isFinite(d), "finite at " + tiny);
            assertTrue(Double.isFinite(mb.distance(0, 0, tiny)));
            assertTrue(Double.isFinite(mb.distance(tiny, tiny, tiny)));
        }
    }

    @Test
    void highIterationCountsStayFinite() {
        Mandelbulb mb = new Mandelbulb(8, 1000, 4);
        Mandelbulb mb20 = new Mandelbulb(20, 1000, 100);
        Random rnd = new Random(3);
        for (int i = 0; i < 300; i++) {
            double x = rnd.nextGaussian() * 0.7, y = rnd.nextGaussian() * 0.7, z = rnd.nextGaussian() * 0.7;
            assertTrue(Double.isFinite(mb.distance(x, y, z)));
            assertTrue(Double.isFinite(mb20.distance(x, y, z)));
        }
    }

    @Test
    void polarAxisPointsAreHandled() {
        // rho = 0: phi is undefined; must not produce NaN.
        Mandelbulb mb = new Mandelbulb(8, 20, 4);
        for (double z = -1.5; z <= 1.5; z += 0.1) {
            assertTrue(Double.isFinite(mb.distance(0, 0, z)), "z=" + z);
        }
    }

    @Test
    void distanceEstimateIsConservative() {
        // Sphere-tracing correctness: within DE(p) * scale around p there should be no point of
        // the set. The Mandelbulb DE is approximate (see class docs: bounded interior orbits
        // with |z| > 1 report positive values), so we require violations to be rare rather than
        // absent. Measured: 0% at 15 iterations, ~0.55% at 20, independent of the ball radius.
        Mandelbulb mb = new Mandelbulb(8, 20, 4);
        Random rnd = new Random(11);
        int balls = 0, samples = 0, violations = 0;
        while (balls < 500) {
            double x = rnd.nextDouble() * 2.6 - 1.3, y = rnd.nextDouble() * 2.6 - 1.3, z = rnd.nextDouble() * 2.6 - 1.3;
            double d = mb.distance(x, y, z);
            if (d <= 1e-3 || d > 0.5) continue;
            balls++;
            for (int k = 0; k < 20; k++) {
                double gx = rnd.nextGaussian(), gy = rnd.nextGaussian(), gz = rnd.nextGaussian();
                double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
                double s = 0.85 * d * rnd.nextDouble();
                samples++;
                if (mb.contains(x + gx / gl * s, y + gy / gl * s, z + gz / gl * s)) violations++;
            }
        }
        assertTrue(violations < samples * 0.01, violations + " of " + samples + " samples inside DE balls");
        // At the default 15 iterations the estimate is fully conservative on this sample.
        Mandelbulb mb15 = new Mandelbulb(8, 15, 4);
        rnd = new Random(5);
        balls = 0;
        while (balls < 300) {
            double x = rnd.nextDouble() * 2.6 - 1.3, y = rnd.nextDouble() * 2.6 - 1.3, z = rnd.nextDouble() * 2.6 - 1.3;
            double d = mb15.distance(x, y, z);
            if (d <= 1e-3 || d > 0.5) continue;
            balls++;
            for (int k = 0; k < 10; k++) {
                double gx = rnd.nextGaussian(), gy = rnd.nextGaussian(), gz = rnd.nextGaussian();
                double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
                double s = 0.85 * d * rnd.nextDouble();
                assertFalse(mb15.contains(x + gx / gl * s, y + gy / gl * s, z + gz / gl * s));
            }
        }
    }

    @Test
    void distanceDecreasesTowardsTheSurface() {
        Mandelbulb mb = new Mandelbulb(8, 20, 4);
        double prev = Double.MAX_VALUE;
        for (double x = 3; x > 1.3; x -= 0.1) {
            double d = mb.distance(x, 0.1, 0.05);
            assertTrue(d < prev, "monotone along the ray at x=" + x);
            prev = d;
        }
    }

    @Test
    void orbitTrapRecordsOrbitStatistics() {
        Mandelbulb mb = new Mandelbulb(8, 20, 4);
        OrbitTrap trap = new OrbitTrap();
        // |p| = 1.19 > 2^(1/7): guaranteed to escape.
        mb.distance(1.0, -0.5, 0.4, trap);
        // The starting point itself is part of the orbit.
        assertTrue(trap.minAbsX <= 1.0 && trap.minAbsY <= 0.5 && trap.minAbsZ <= 0.4);
        assertTrue(trap.minRadius <= Math.sqrt(1.0 + 0.25 + 0.16) + 1e-12);
        assertTrue(trap.escaped);
        assertTrue(trap.smoothIteration >= 0 && trap.smoothIteration <= 20);
        for (TrapType t : TrapType.values()) {
            assertTrue(Double.isFinite(trap.value(t)) && trap.value(t) >= 0, t.name());
        }
    }

    @Test
    void juliaModeUsesConstantAndStaysFinite() {
        FractalParameters p = new FractalParameters();
        p.juliaMode = true;
        p.juliaX = 0.45;
        p.juliaY = -0.35;
        p.juliaZ = 0.3;
        Mandelbulb julia = new Mandelbulb(p);
        Mandelbulb classic = new Mandelbulb(new FractalParameters());
        assertNotEquals(classic.distance(0.5, 0.5, 0.2), julia.distance(0.5, 0.5, 0.2));
        assertTrue(Double.isFinite(julia.distance(0, 0, 0)));
        assertTrue(julia.boundingRadius() > 1);
    }
}

package com.mandelbulb.fractal;

import com.mandelbulb.math.MathUtils;
import com.mandelbulb.util.Settings;

/**
 * Parameters of the Mandelbulb iteration itself (as opposed to how it is rendered).
 */
public final class FractalParameters extends Settings {

    public static final double MIN_POWER = 2.0;
    public static final double MAX_POWER = 20.0;
    public static final int MIN_ITERATIONS = 2;
    public static final int MAX_ITERATIONS = 1000;
    public static final double MIN_BAILOUT = 2.0;
    public static final double MAX_BAILOUT = 100.0;

    /** Exponent n of the spherical power map. Non-integer values are allowed. */
    public double power = 8.0;
    /**
     * Maximum number of iterations. The distance estimate converges quickly for points outside
     * the set, so 10-20 is enough for a normal view; more only matters in deep zooms where
     * finer surface detail becomes visible.
     */
    public int maxIterations = 15;
    /** Escape radius R: a point is considered escaped once |z| > R. */
    public double bailout = 4.0;

    /** Julia mode: iterate z -> z^n + k with a fixed k instead of k = starting point. */
    public boolean juliaMode = false;
    public double juliaX = 0.35;
    public double juliaY = -0.45;
    public double juliaZ = 0.25;

    /** Object rotation (degrees): about the vertical Z axis, then the X axis. */
    public double rotationZ = 0.0;
    public double rotationX = 0.0;

    public FractalParameters copy() {
        return deepCopy(this);
    }

    @Override
    public void validate() {
        power = MathUtils.sanitize(power, MIN_POWER, MAX_POWER, 8.0);
        maxIterations = MathUtils.clamp(maxIterations, MIN_ITERATIONS, MAX_ITERATIONS);
        bailout = MathUtils.sanitize(bailout, MIN_BAILOUT, MAX_BAILOUT, 4.0);
        juliaX = MathUtils.sanitize(juliaX, -2, 2, 0);
        juliaY = MathUtils.sanitize(juliaY, -2, 2, 0);
        juliaZ = MathUtils.sanitize(juliaZ, -2, 2, 0);
        rotationZ = MathUtils.sanitize(rotationZ, -360, 360, 0);
        rotationX = MathUtils.sanitize(rotationX, -360, 360, 0);
    }
}

package com.mandelbulb.fractal;

/**
 * Mutable accumulator for statistics of one orbit z0, z1, z2, ... of the Mandelbulb iteration.
 *
 * <p>An <em>orbit trap</em> records how close the orbit ever came to some geometric primitive
 * (a plane, a point, a sphere, ...). Neighbouring surface points have similar orbits, so these
 * minima vary smoothly over the surface and produce the characteristic organic colour patterns.
 *
 * <p>One instance is owned by each render thread and reused for every pixel (no allocation).
 */
public final class OrbitTrap {

    public static final double POINT_X = 0.5, POINT_Y = 0.5, POINT_Z = 0.5;
    public static final double SHELL_RADIUS = 1.0;

    public double minAbsX;
    public double minAbsY;
    public double minAbsZ;
    public double minRadius;
    public double minPointDistance;
    public double minShellDistance;
    public double minAxisDistance;

    /** Iteration index at which the orbit escaped (or maxIterations if it never did). */
    public int iterations;
    /** |z| after the last iteration. */
    public double finalRadius;
    public boolean escaped;
    /**
     * Continuous ("normalised") iteration count: iterations + 1 - log(log|z| / log R) / log n.
     * It interpolates between integer counts so iteration-based colouring has no hard bands.
     */
    public double smoothIteration;

    public OrbitTrap() {
        reset();
    }

    public void reset() {
        minAbsX = minAbsY = minAbsZ = Double.MAX_VALUE;
        minRadius = minPointDistance = minShellDistance = minAxisDistance = Double.MAX_VALUE;
        iterations = 0;
        finalRadius = 0;
        escaped = false;
        smoothIteration = 0;
    }

    /** Folds one orbit point (with precomputed radius r) into the trap minima. */
    void accumulate(double x, double y, double z, double r) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ax < minAbsX) minAbsX = ax;
        if (ay < minAbsY) minAbsY = ay;
        if (az < minAbsZ) minAbsZ = az;
        if (r < minRadius) minRadius = r;
        double dx = x - POINT_X, dy = y - POINT_Y, dz = z - POINT_Z;
        double dp = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dp < minPointDistance) minPointDistance = dp;
        double shell = Math.abs(r - SHELL_RADIUS);
        if (shell < minShellDistance) minShellDistance = shell;
        // Distance to the x, y and z axes respectively.
        double axis = Math.min(Math.sqrt(y * y + z * z), Math.min(Math.sqrt(x * x + z * z), Math.sqrt(x * x + y * y)));
        if (axis < minAxisDistance) minAxisDistance = axis;
    }

    public double value(TrapType type) {
        return switch (type) {
            case X_PLANE -> minAbsX;
            case Y_PLANE -> minAbsY;
            case Z_PLANE -> minAbsZ;
            case ORIGIN -> minRadius;
            case POINT -> minPointDistance;
            case SHELL -> minShellDistance;
            case AXES -> minAxisDistance;
            case PLANES -> Math.min(minAbsX, Math.min(minAbsY, minAbsZ));
        };
    }

    public void copyFrom(OrbitTrap o) {
        minAbsX = o.minAbsX;
        minAbsY = o.minAbsY;
        minAbsZ = o.minAbsZ;
        minRadius = o.minRadius;
        minPointDistance = o.minPointDistance;
        minShellDistance = o.minShellDistance;
        minAxisDistance = o.minAxisDistance;
        iterations = o.iterations;
        finalRadius = o.finalRadius;
        escaped = o.escaped;
        smoothIteration = o.smoothIteration;
    }
}

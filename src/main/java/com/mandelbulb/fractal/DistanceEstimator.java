package com.mandelbulb.fractal;

/**
 * A signed-distance-like function for a fractal: a conservative estimate of the distance from a
 * point to the fractal surface. Positive outside, approximately zero on the surface, and
 * zero/negative inside.
 *
 * <p>This is the only thing the ray marcher needs to know about the geometry, so other
 * distance-estimated fractals (Mandelbox, Julia bulbs, ...) could be plugged in here. Methods
 * take primitive coordinates because they are called hundreds of times per pixel.
 */
public interface DistanceEstimator {

    /** Distance estimate at (x, y, z) in fractal (object) space. */
    double distance(double x, double y, double z);

    /**
     * Same as {@link #distance(double, double, double)} but additionally records orbit
     * statistics (iteration count, orbit traps) into {@code trap}. Used once per shaded pixel.
     */
    double distance(double x, double y, double z, OrbitTrap trap);

    /**
     * Radius of a sphere around the origin that is guaranteed to contain the visible surface.
     * The renderer clips rays against it so it never marches through empty space far away.
     */
    double boundingRadius();
}

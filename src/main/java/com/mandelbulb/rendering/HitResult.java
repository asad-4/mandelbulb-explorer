package com.mandelbulb.rendering;

/** Mutable result of one ray march (reused per thread). */
public final class HitResult {
    public boolean hit;
    /** The march started inside the fractal (camera is inside the surface). */
    public boolean inside;
    /** Distance along the ray. */
    public double t;
    /** Object-space hit position. */
    public double x, y, z;
    /** Raw distance estimate at the hit point. */
    public double distance;
    /** Hit threshold that was used at t (pixel footprint based). */
    public double epsilon;
    public int steps;
    /**
     * Smallest (DE / t) seen along the ray: the angular closeness of the ray to the surface,
     * used for the silhouette glow of rays that just miss.
     */
    public double minAngularDistance;

    public void reset() {
        hit = false;
        inside = false;
        t = 0;
        x = y = z = 0;
        distance = 0;
        epsilon = 0;
        steps = 0;
        minAngularDistance = Double.MAX_VALUE;
    }
}

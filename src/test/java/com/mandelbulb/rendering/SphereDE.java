package com.mandelbulb.rendering;

import com.mandelbulb.fractal.DistanceEstimator;
import com.mandelbulb.fractal.OrbitTrap;

/** Exact signed distance to a sphere: a DE with known answers for testing the renderer. */
final class SphereDE implements DistanceEstimator {
    final double cx, cy, cz, radius;

    SphereDE(double cx, double cy, double cz, double radius) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
    }

    @Override
    public double distance(double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) - radius;
    }

    @Override
    public double distance(double x, double y, double z, OrbitTrap trap) {
        return distance(x, y, z);
    }

    @Override
    public double boundingRadius() {
        return Math.sqrt(cx * cx + cy * cy + cz * cz) + radius + 0.1;
    }
}

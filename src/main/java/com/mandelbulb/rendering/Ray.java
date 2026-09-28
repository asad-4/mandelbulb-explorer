package com.mandelbulb.rendering;

/**
 * Mutable ray (origin + unit direction) in fractal object space. One instance per render thread
 * is reused for every pixel, so casting a ray allocates nothing.
 */
public final class Ray {
    public double ox, oy, oz;
    public double dx, dy, dz;

    public Ray set(double ox, double oy, double oz, double dx, double dy, double dz) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        return this;
    }

    /** Normalises the direction in place; returns false for a degenerate direction. */
    public boolean normalizeDirection() {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(len > 1e-300) || !Double.isFinite(len)) {
            return false;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        return true;
    }
}

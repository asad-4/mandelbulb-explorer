package com.mandelbulb.rendering;

import com.mandelbulb.fractal.DistanceEstimator;

/**
 * Distance-field ambient occlusion.
 *
 * <p>Sample the DE at points p + n h_i marching out along the normal. On an unoccluded, flat
 * surface DE(p + n h) = h. When other geometry is nearby, the distance field is smaller than h;
 * the deficit (h - DE) measures how much of the hemisphere is blocked. Nearer samples are
 * weighted more strongly because nearby geometry blocks a larger solid angle:
 * <pre>
 *   occlusion = sum_i w_i * max(0, h_i - DE(p + n h_i)) / sum_i w_i h_i,   w_i = 0.7^i
 *   AO        = 1 - strength * occlusion
 * </pre>
 * The sample radius is proportional to the hit distance so the effect looks the same at every
 * zoom level - a fractal has crevices at every scale.
 */
public final class AmbientOcclusion {

    private final DistanceEstimator de;
    private final int samples;
    private final double radiusFactor;
    private final double strength;

    public AmbientOcclusion(DistanceEstimator de, int samples, double radiusFactor, double strength) {
        this.de = de;
        this.samples = samples;
        this.radiusFactor = radiusFactor;
        this.strength = strength;
    }

    /** @return ambient visibility in [0,1] (1 = fully open) */
    public double compute(double x, double y, double z, double nx, double ny, double nz,
                          double hitDistance, double eps, RenderContext ctx) {
        double radius = Math.max(radiusFactor * hitDistance, eps * 4);
        double occ = 0, norm = 0, w = 1;
        for (int i = 1; i <= samples; i++) {
            double h = radius * i / samples;
            double d = de.distance(x + nx * h, y + ny * h, z + nz * h);
            if (h > d) {
                occ += w * (h - d);
            }
            norm += w * h;
            w *= 0.7;
        }
        ctx.deEvaluations += samples;
        double ao = 1 - strength * Math.min(1, 1.6 * occ / norm);
        return ao < 0 ? 0 : (ao > 1 ? 1 : ao);
    }
}

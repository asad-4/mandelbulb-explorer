package com.mandelbulb.rendering;

import com.mandelbulb.fractal.DistanceEstimator;

/**
 * Shadow rays with a soft-shadow approximation (Quilez's distance-field penumbra).
 *
 * <p>A shadow ray is marched from the surface (offset along the normal so it does not
 * immediately re-hit its own starting point) towards the light. A hit means full shadow. For
 * rays that pass close to geometry without touching it, the ratio DE(t) / t is the (tangent of
 * the) angle by which the ray cleared the occluder; an area light of angular size ~1/k would be
 * partly hidden, so visibility is estimated as
 * <pre>
 *   visibility = min over the ray of clamp(k * DE(t) / t)
 * </pre>
 * with k = 1 / softness. Softness 0 gives hard shadows (only the binary hit test).
 */
public final class ShadowRenderer {

    private final DistanceEstimator de;
    private final RayMarcher marcher;
    private final int maxSteps;
    private final double k;
    private final double stepScale;

    public ShadowRenderer(DistanceEstimator de, RayMarcher marcher, int maxSteps, double softness, double stepScale) {
        this.de = de;
        this.marcher = marcher;
        this.maxSteps = maxSteps;
        this.k = softness <= 1e-4 ? 1e4 : 1.0 / softness;
        this.stepScale = stepScale;
    }

    /**
     * @param lightDistance distance to the light (infinite for directional lights)
     * @return light visibility in [0,1]
     */
    public double visibility(double ox, double oy, double oz, double lx, double ly, double lz,
                             double lightDistance, double eps, RenderContext ctx) {
        ctx.shadowRays++;
        double tMax = Math.min(lightDistance, marcher.exitDistance(ox, oy, oz, lx, ly, lz));
        double t = eps * 2;
        double res = 1.0;
        double maxStep = Math.max(tMax / 8, eps);
        int i = 0;
        for (; i < maxSteps && t < tMax; i++) {
            double h = de.distance(ox + lx * t, oy + ly * t, oz + lz * t) * stepScale;
            if (h < eps * 0.5) {
                res = 0;
                i++;
                break;
            }
            double s = k * h / t;
            if (s < res) res = s;
            if (res < 0.002) {
                res = 0;
                i++;
                break;
            }
            t += Math.min(Math.max(h, eps * 0.5), maxStep);
        }
        ctx.deEvaluations += i;
        // Smooth the penumbra edge (cubic ease) - linear falloff looks artificially hard.
        res = res < 0 ? 0 : (res > 1 ? 1 : res);
        return res * res * (3 - 2 * res);
    }
}

package com.mandelbulb.rendering;

import com.mandelbulb.fractal.DistanceEstimator;

/**
 * Sphere tracing (ray marching with a distance estimator).
 *
 * <p>If DE(p) is a lower bound on the distance from p to the surface, then the open ball of that
 * radius around p is empty, so the ray can safely advance by DE(p). Repeating this converges
 * towards the first intersection: the steps are large in empty space and shrink automatically
 * near the surface. We stop when DE drops below a threshold epsilon.
 *
 * <p>Refinements implemented here:
 * <ul>
 *   <li><b>Bounding sphere</b>: the ray is clipped to a sphere that contains the fractal, so rays
 *       that miss it cost nothing and the march starts at the sphere instead of the camera.</li>
 *   <li><b>DE scale</b>: the Mandelbulb DE is only approximately a lower bound (the power map is
 *       not conformal), so steps are multiplied by a factor &lt;= 1 to avoid overshooting.</li>
 *   <li><b>Pixel-footprint epsilon</b>: epsilon grows linearly with t so the surface is resolved
 *       to about one pixel. Resolving finer than a pixel wastes steps and causes aliasing; a
 *       fixed epsilon would be too coarse up close and too fine far away.</li>
 *   <li><b>Over-relaxation</b> (Keinert et al., "Enhanced Sphere Tracing", 2014): steps are
 *       lengthened by omega &gt; 1. The step was safe iff the unbounding spheres at the previous
 *       and current point overlap (r_prev + r_cur &gt;= step). If they don't, the relaxed step
 *       may have jumped over geometry, so we return to the previous point and continue with
 *       plain sphere tracing. This is the adaptive step scaling with overshoot protection.</li>
 *   <li><b>Bisection</b>: if a step ends up inside the surface (negative DE), the crossing is
 *       refined by bisection so the hit lies on the outside.</li>
 * </ul>
 *
 * <p>Immutable and shared by all threads; per-ray state lives in {@link RenderContext}.
 */
public final class RayMarcher {

    private final DistanceEstimator de;
    private final int maxSteps;
    private final double stepScale;
    private final double relaxation;
    private final double hitEpsilon;
    private final double minStep;
    private final double pixelAngle;
    private final double boundRadius2;

    /**
     * @param pixelAngle angular size of one pixel (radians) divided by the detail factor; the hit
     *                   threshold at distance t is {@code t * pixelAngle}
     */
    public RayMarcher(DistanceEstimator de, int maxSteps, double stepScale, double relaxation,
                      double hitEpsilon, double minStep, double pixelAngle) {
        this.de = de;
        this.maxSteps = maxSteps;
        this.stepScale = stepScale;
        this.relaxation = relaxation;
        this.hitEpsilon = hitEpsilon;
        this.minStep = minStep;
        this.pixelAngle = pixelAngle;
        double r = de.boundingRadius();
        this.boundRadius2 = r * r;
    }

    public double epsilonAt(double t, double epsScale) {
        return Math.max(hitEpsilon, t * pixelAngle * epsScale);
    }

    /**
     * Marches {@code ray} (unit direction, object space) between {@code tNear} and {@code tFar}.
     *
     * @param epsScale multiplier on the pixel-footprint epsilon (coarse preview passes use their
     *                 block size, which makes previews proportionally cheaper)
     */
    public void march(Ray ray, double tNear, double tFar, double epsScale, HitResult out, RenderContext ctx) {
        out.reset();
        ctx.primaryRays++;
        final double ox = ray.ox, oy = ray.oy, oz = ray.oz;
        final double dx = ray.dx, dy = ray.dy, dz = ray.dz;

        // Ray / bounding sphere: |o + t d|^2 = R^2  =>  t^2 + 2bt + c = 0.
        double b = ox * dx + oy * dy + oz * dz;
        double c = ox * ox + oy * oy + oz * oz - boundRadius2;
        double disc = b * b - c;
        if (disc <= 0) {
            return;
        }
        double sq = Math.sqrt(disc);
        double tEnd = Math.min(tFar, -b + sq);
        double t = Math.max(tNear, -b - sq);
        if (tEnd <= t) {
            return;
        }

        double omega = relaxation;
        double tPrev = t;
        double prevD = 0;
        double stepLen = 0;
        double lastD = Double.MAX_VALUE;
        int steps = 0;
        boolean hit = false;

        while (steps < maxSteps) {
            steps++;
            double px = ox + dx * t, py = oy + dy * t, pz = oz + dz * t;
            double d = de.distance(px, py, pz) * stepScale;
            ctx.deEvaluations++;
            lastD = d;

            if (steps == 1 && d < 0) {
                // Started inside the fractal: nothing sensible to see.
                out.inside = true;
                hit = true;
                break;
            }
            if (omega > 1 && d + prevD < stepLen) {
                // Relaxed step was unsafe: go back to the last safe point and take the plain
                // (provably safe) sphere-tracing step from there instead.
                omega = 1;
                t = tPrev + prevD;
                stepLen = prevD;
                continue;
            }

            double eps = Math.max(hitEpsilon, t * pixelAngle * epsScale);
            if (d < eps) {
                if (d < 0 && t > tPrev) {
                    t = bisect(ox, oy, oz, dx, dy, dz, tPrev, t, ctx);
                }
                hit = true;
                break;
            }
            double angular = d / t;
            if (angular < out.minAngularDistance) {
                out.minAngularDistance = angular;
            }

            tPrev = t;
            prevD = d;
            stepLen = Math.max(d * omega, minStep);
            t += stepLen;
            if (t > tEnd) {
                if (omega > 1 && tPrev + prevD <= tEnd) {
                    // The relaxed step left the valid range before its safety check could run;
                    // only the plain step is guaranteed not to skip geometry near the far end.
                    omega = 1;
                    t = tPrev + prevD;
                    stepLen = prevD;
                    continue;
                }
                break;
            }
        }

        if (!hit && steps >= maxSteps) {
            // Out of steps. In deep crevices the ray creeps along the surface; if it is still
            // very close, treating it as a hit avoids speckled holes in the image.
            double eps = Math.max(hitEpsilon, t * pixelAngle * epsScale);
            hit = lastD < eps * 20;
        }

        out.steps = steps;
        ctx.marchSteps += steps;
        if (steps > ctx.maxMarchSteps) {
            ctx.maxMarchSteps = steps;
        }
        if (hit) {
            ctx.hits++;
            out.hit = true;
            out.t = t;
            out.x = ox + dx * t;
            out.y = oy + dy * t;
            out.z = oz + dz * t;
            out.distance = lastD / stepScale;
            out.epsilon = Math.max(hitEpsilon, t * pixelAngle * epsScale);
        }
    }

    /** Finds the outside/inside crossing between tOut (DE &gt; 0) and tIn (DE &lt; 0). */
    private double bisect(double ox, double oy, double oz, double dx, double dy, double dz,
                          double tOut, double tIn, RenderContext ctx) {
        for (int i = 0; i < 10; i++) {
            double tm = 0.5 * (tOut + tIn);
            double d = de.distance(ox + dx * tm, oy + dy * tm, oz + dz * tm);
            ctx.deEvaluations++;
            if (d < 0) tIn = tm;
            else tOut = tm;
        }
        return tOut;
    }

    /**
     * Distance along the ray at which it leaves the bounding sphere (for secondary rays that
     * start inside it).
     */
    public double exitDistance(double ox, double oy, double oz, double dx, double dy, double dz) {
        double b = ox * dx + oy * dy + oz * dz;
        double c = ox * ox + oy * oy + oz * oz - boundRadius2;
        double disc = b * b - c;
        return disc <= 0 ? 0 : -b + Math.sqrt(disc);
    }
}

package com.mandelbulb.rendering;

import com.mandelbulb.fractal.DistanceEstimator;

/**
 * Surface normals from the gradient of the distance field.
 *
 * <p>The surface is the level set DE = 0, and the gradient of a scalar field is perpendicular
 * to its level sets, so N = grad DE / |grad DE|. The gradient is approximated numerically:
 * <pre>
 *   central:     N_x = DE(p + e x) - DE(p - e x)   (and likewise y, z)       6 evaluations
 *   tetrahedral: N   = sum_i k_i DE(p + e k_i),   k_i = (+,-,-), (-,-,+), (-,+,-), (+,+,+)
 *                                                          4 evaluations, first-order accurate
 * </pre>
 * The offset e is tied to the pixel footprint: a smaller e would resolve sub-pixel detail that
 * then aliases into noisy shading, a larger one blurs visible detail.
 *
 * <p>The same samples give the Laplacian of the DE for free, which approximates the mean
 * curvature of the surface (used by the curvature colour mode):
 * <pre>
 *   central:     lap = (sum of 6 samples - 6 DE(p)) / e^2
 *   tetrahedral: lap = (sum of 4 samples - 4 DE(p)) / (2 e^2)
 * </pre>
 */
public final class SurfaceNormal {

    private final DistanceEstimator de;
    private final NormalMethod method;
    private final double minEpsilon;

    public SurfaceNormal(DistanceEstimator de, NormalMethod method, double minEpsilon) {
        this.de = de;
        this.method = method;
        this.minEpsilon = minEpsilon;
    }

    /**
     * Computes the unit normal at (x, y, z) into {@code ctx.nx/ny/nz} and the DE Laplacian into
     * {@code ctx.laplacian}.
     *
     * @param d0 DE at the point itself (already known from the march)
     */
    public void compute(double x, double y, double z, double eps, double d0, RenderContext ctx) {
        double e = Math.max(eps, minEpsilon);
        double nx, ny, nz;
        if (method == NormalMethod.TETRAHEDRAL) {
            double a = de.distance(x + e, y - e, z - e);
            double b = de.distance(x - e, y - e, z + e);
            double c = de.distance(x - e, y + e, z - e);
            double d = de.distance(x + e, y + e, z + e);
            ctx.deEvaluations += 4;
            nx = a - b - c + d;
            ny = -a - b + c + d;
            nz = -a + b - c + d;
            ctx.laplacian = (a + b + c + d - 4 * d0) / (2 * e * e);
        } else {
            double xp = de.distance(x + e, y, z), xm = de.distance(x - e, y, z);
            double yp = de.distance(x, y + e, z), ym = de.distance(x, y - e, z);
            double zp = de.distance(x, y, z + e), zm = de.distance(x, y, z - e);
            ctx.deEvaluations += 6;
            nx = xp - xm;
            ny = yp - ym;
            nz = zp - zm;
            ctx.laplacian = (xp + xm + yp + ym + zp + zm - 6 * d0) / (e * e);
        }
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-300 && Double.isFinite(len)) {
            ctx.nx = nx / len;
            ctx.ny = ny / len;
            ctx.nz = nz / len;
        } else {
            // Degenerate gradient (flat DE or overflow): fall back to the radial direction,
            // which is the correct normal for the bulb's overall shape.
            double r = Math.sqrt(x * x + y * y + z * z);
            if (r > 1e-300) {
                ctx.nx = x / r;
                ctx.ny = y / r;
                ctx.nz = z / r;
            } else {
                ctx.nx = 0;
                ctx.ny = 0;
                ctx.nz = 1;
            }
        }
        if (!Double.isFinite(ctx.laplacian)) {
            ctx.laplacian = 0;
        }
    }
}

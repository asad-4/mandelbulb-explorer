package com.mandelbulb.rendering;

import com.mandelbulb.fractal.Mandelbulb;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceNormalTest {

    @ParameterizedTest
    @EnumSource(NormalMethod.class)
    void sphereNormalIsRadial(NormalMethod method) {
        SphereDE sphere = new SphereDE(0, 0, 0, 2);
        SurfaceNormal n = new SurfaceNormal(sphere, method, 1e-9);
        RenderContext ctx = new RenderContext();
        // Central differences are second-order accurate, tetrahedral only first-order.
        double tol = method == NormalMethod.CENTRAL ? 1e-8 : 1e-5;
        double[][] dirs = {{1, 0, 0}, {0, -1, 0}, {0, 0, 1}, {0.6, 0.0, 0.8}, {-0.48, 0.6, 0.64}};
        for (double[] d : dirs) {
            double x = d[0] * 2, y = d[1] * 2, z = d[2] * 2;
            n.compute(x, y, z, 1e-5, sphere.distance(x, y, z), ctx);
            assertEquals(d[0], ctx.nx, tol);
            assertEquals(d[1], ctx.ny, tol);
            assertEquals(d[2], ctx.nz, tol);
            // Laplacian of |p| - R is 2/|p| = 1 on the sphere of radius 2.
            assertEquals(1.0, ctx.laplacian, 1e-2);
        }
    }

    @Test
    void normalIsUnitLengthOnMandelbulbSurface() {
        Mandelbulb mb = new Mandelbulb(8, 15, 4);
        RayMarcher m = new RayMarcher(mb, 500, 0.85, 1.0, 1e-7, 1e-10, 1e-4);
        SurfaceNormal n = new SurfaceNormal(mb, NormalMethod.CENTRAL, 1e-9);
        RenderContext ctx = new RenderContext();
        ctx.ray.set(0.3, -3, 0.2, 0, 1, 0);
        m.march(ctx.ray, 0, 20, 1, ctx.hit, ctx);
        assertTrue(ctx.hit.hit);
        n.compute(ctx.hit.x, ctx.hit.y, ctx.hit.z, ctx.hit.epsilon, ctx.hit.distance, ctx);
        assertEquals(1.0, Math.sqrt(ctx.nx * ctx.nx + ctx.ny * ctx.ny + ctx.nz * ctx.nz), 1e-12);
        // Facing the camera (which looks along +y).
        assertTrue(ctx.ny < 0);
    }

    @Test
    void degenerateGradientFallsBackToRadialDirection() {
        com.mandelbulb.fractal.DistanceEstimator flat = new com.mandelbulb.fractal.DistanceEstimator() {
            public double distance(double x, double y, double z) {
                return 0;
            }

            public double distance(double x, double y, double z, com.mandelbulb.fractal.OrbitTrap t) {
                return 0;
            }

            public double boundingRadius() {
                return 1;
            }
        };
        RenderContext ctx = new RenderContext();
        new SurfaceNormal(flat, NormalMethod.CENTRAL, 1e-9).compute(0, 0, 3, 1e-4, 0, ctx);
        assertEquals(1, ctx.nz, 1e-12);
    }
}

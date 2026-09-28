package com.mandelbulb.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShadingTest {

    @Test
    void openSurfaceHasNoOcclusion() {
        SphereDE sphere = new SphereDE(0, 0, 0, 1);
        AmbientOcclusion ao = new AmbientOcclusion(sphere, 6, 0.1, 1.0);
        double v = ao.compute(0, 0, 1, 0, 0, 1, 1.0, 1e-6, new RenderContext());
        assertEquals(1.0, v, 1e-9);
    }

    @Test
    void creviceIsOccluded() {
        // Point on sphere A whose normal points straight into nearby sphere B.
        com.mandelbulb.fractal.DistanceEstimator two = new com.mandelbulb.fractal.DistanceEstimator() {
            final SphereDE a = new SphereDE(0, 0, 0, 1), b = new SphereDE(0, 0, 2.1, 1);

            public double distance(double x, double y, double z) {
                return Math.min(a.distance(x, y, z), b.distance(x, y, z));
            }

            public double distance(double x, double y, double z, com.mandelbulb.fractal.OrbitTrap t) {
                return distance(x, y, z);
            }

            public double boundingRadius() {
                return 5;
            }
        };
        AmbientOcclusion ao = new AmbientOcclusion(two, 6, 0.15, 1.0);
        double v = ao.compute(0, 0, 1, 0, 0, 1, 1.0, 1e-6, new RenderContext());
        assertTrue(v < 0.6, "occluded, got " + v);
    }

    @Test
    void shadowRayDetectsOccluder() {
        SphereDE occluder = new SphereDE(0, 0, 2, 0.5);
        RayMarcher m = new RayMarcher(occluder, 200, 1, 1, 1e-7, 1e-9, 1e-4);
        ShadowRenderer shadows = new ShadowRenderer(occluder, m, 200, 0.0, 1.0);
        RenderContext ctx = new RenderContext();
        // Light straight above: blocked by the sphere at z = 2.
        assertEquals(0.0, shadows.visibility(0, 0, 0, 0, 0, 1, Double.MAX_VALUE, 1e-5, ctx), 1e-9);
        // Light sideways: unobstructed.
        assertEquals(1.0, shadows.visibility(0, 0, 0, 1, 0, 0, Double.MAX_VALUE, 1e-5, ctx), 1e-9);
        // Point light below the occluder: not blocked.
        assertEquals(1.0, shadows.visibility(0, 0, 0, 0, 0, 1, 1.0, 1e-5, ctx), 1e-9);
        assertEquals(3, ctx.shadowRays);
    }

    @Test
    void softShadowGivesPenumbra() {
        SphereDE occluder = new SphereDE(0, 0, 2, 0.5);
        RayMarcher m = new RayMarcher(occluder, 200, 1, 1, 1e-7, 1e-9, 1e-4);
        ShadowRenderer soft = new ShadowRenderer(occluder, m, 400, 0.5, 1.0);
        // Ray grazing past the sphere edge: partially lit.
        double dx = 0.3, dz = 1, len = Math.sqrt(dx * dx + dz * dz);
        double v = soft.visibility(0, 0, 0, dx / len, 0, dz / len, Double.MAX_VALUE, 1e-5, new RenderContext());
        assertTrue(v > 0 && v < 1, "penumbra, got " + v);
    }
}

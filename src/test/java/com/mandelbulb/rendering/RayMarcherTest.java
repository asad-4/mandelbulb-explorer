package com.mandelbulb.rendering;

import com.mandelbulb.fractal.Mandelbulb;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RayMarcherTest {

    private static RayMarcher marcher(com.mandelbulb.fractal.DistanceEstimator de, double relaxation) {
        return new RayMarcher(de, 500, 1.0, relaxation, 1e-9, 1e-12, 1e-6);
    }

    @Test
    void hitsSphereAtAnalyticDistance() {
        SphereDE sphere = new SphereDE(0, 0, 0, 1);
        for (double relax : new double[] {1.0, 1.25, 1.6}) {
            RenderContext ctx = new RenderContext();
            ctx.ray.set(0, -5, 0, 0, 1, 0);
            marcher(sphere, relax).march(ctx.ray, 0, 100, 1, ctx.hit, ctx);
            assertTrue(ctx.hit.hit, "relaxation " + relax);
            assertEquals(4.0, ctx.hit.t, 1e-4, "relaxation " + relax);
            assertEquals(-1.0, ctx.hit.y, 1e-4);
            assertTrue(ctx.hit.steps > 0 && ctx.hit.steps < 100);
        }
    }

    @Test
    void missingRayReportsNoHit() {
        SphereDE sphere = new SphereDE(0, 0, 0, 1);
        RenderContext ctx = new RenderContext();
        ctx.ray.set(0, -5, 1.05, 0, 1, 0);
        marcher(sphere, 1.25).march(ctx.ray, 0, 100, 1, ctx.hit, ctx);
        assertFalse(ctx.hit.hit);
        // Closest approach 0.05 at distance ~5 -> angular distance ~0.01 (drives the glow).
        assertEquals(0.01, ctx.hit.minAngularDistance, 0.003);
        // A ray that misses the bounding sphere entirely is not marched at all.
        RenderContext far = new RenderContext();
        far.ray.set(0, -5, 1.5, 0, 1, 0);
        marcher(sphere, 1.25).march(far.ray, 0, 100, 1, far.hit, far);
        assertEquals(0, far.deEvaluations);
    }

    @Test
    void rayPointingAwayFromBoundingSphereCostsNothing() {
        SphereDE sphere = new SphereDE(0, 0, 0, 1);
        RenderContext ctx = new RenderContext();
        ctx.ray.set(0, -5, 0, 0, -1, 0);
        marcher(sphere, 1.0).march(ctx.ray, 0, 100, 1, ctx.hit, ctx);
        assertFalse(ctx.hit.hit);
        assertEquals(0, ctx.deEvaluations);
    }

    @Test
    void farClipStopsTheRay() {
        SphereDE sphere = new SphereDE(0, 0, 0, 1);
        RenderContext ctx = new RenderContext();
        ctx.ray.set(0, -5, 0, 0, 1, 0);
        marcher(sphere, 1.0).march(ctx.ray, 0, 3.5, 1, ctx.hit, ctx);
        assertFalse(ctx.hit.hit);
    }

    @Test
    void startingInsideIsDetected() {
        SphereDE sphere = new SphereDE(0, 0, 0, 1);
        RenderContext ctx = new RenderContext();
        ctx.ray.set(0, 0, 0, 0, 1, 0);
        marcher(sphere, 1.0).march(ctx.ray, 0, 100, 1, ctx.hit, ctx);
        assertTrue(ctx.hit.hit);
        assertTrue(ctx.hit.inside);
    }

    @Test
    void overRelaxationDoesNotSkipThinGeometry() {
        // A small sphere just off the ray's path near a large one: relaxed steps must not jump it.
        SphereDE small = new SphereDE(0, 0.2, 0, 0.05);
        RenderContext ctx = new RenderContext();
        ctx.ray.set(0, -3, 0, 0, 1, 0);
        new RayMarcher(small, 1000, 1.0, 1.9, 1e-9, 1e-12, 1e-6).march(ctx.ray, 0, 100, 1, ctx.hit, ctx);
        assertTrue(ctx.hit.hit);
        assertEquals(3.15, ctx.hit.t, 1e-4);
    }

    @Test
    void mandelbulbRayFromOutsideHitsSurface() {
        Mandelbulb mb = new Mandelbulb(8, 15, 4);
        RayMarcher m = new RayMarcher(mb, 500, 0.85, 1.25, 1e-7, 1e-10, 1e-4);
        RenderContext ctx = new RenderContext();
        ctx.ray.set(0.05, -3, 0.1, 0, 1, 0);
        m.march(ctx.ray, 0, 20, 1, ctx.hit, ctx);
        assertTrue(ctx.hit.hit);
        assertFalse(ctx.hit.inside);
        // The hit lies on the surface: |DE| within the hit threshold (scaled back).
        assertTrue(Math.abs(mb.distance(ctx.hit.x, ctx.hit.y, ctx.hit.z)) < ctx.hit.epsilon * 2);
        // ...and nothing was skipped: every earlier sample along the ray is outside.
        for (double t = 0; t < ctx.hit.t - 1e-3; t += 1e-3) {
            assertTrue(mb.distance(0.05, -3 + t, 0.1) > 0, "surface skipped at t=" + t);
        }
        assertTrue(ctx.deEvaluations > 0);
        assertEquals(ctx.hit.steps, ctx.marchSteps);
    }
}

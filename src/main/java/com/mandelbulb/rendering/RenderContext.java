package com.mandelbulb.rendering;

import com.mandelbulb.fractal.OrbitTrap;

/**
 * Per-thread scratch space and statistics.
 *
 * <p>Every mutable object the pixel loop needs lives here, created once per worker. The hot path
 * therefore performs no heap allocation at all; multi-value "return values" (normals, colours)
 * are written into these fields. Statistics are plain longs because the context is confined to
 * one thread; they are summed after the pass completes.
 */
public final class RenderContext {

    public final Ray ray = new Ray();
    public final HitResult hit = new HitResult();
    public final OrbitTrap trap = new OrbitTrap();

    // Scratch outputs.
    double nx, ny, nz;
    double laplacian;
    double r, g, b;
    double ar, ag, ab;

    // Statistics.
    public long primaryRays;
    public long shadowRays;
    public long deEvaluations;
    public long marchSteps;
    public long maxMarchSteps;
    public long hits;
    public long pixels;

    public void resetStats() {
        primaryRays = shadowRays = deEvaluations = marchSteps = maxMarchSteps = hits = pixels = 0;
    }
}

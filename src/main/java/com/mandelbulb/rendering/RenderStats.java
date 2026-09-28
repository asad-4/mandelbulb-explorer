package com.mandelbulb.rendering;

/** Statistics of one render (summed over all passes and threads). */
public record RenderStats(
        long elapsedNanos,
        int width,
        int height,
        int supersampling,
        long pixels,
        long primaryRays,
        long shadowRays,
        long deEvaluations,
        long marchSteps,
        long maxMarchSteps,
        long hits,
        int threads,
        int passes,
        boolean complete) {

    public double seconds() {
        return elapsedNanos / 1e9;
    }

    public double averageSteps() {
        return primaryRays == 0 ? 0 : (double) marchSteps / primaryRays;
    }

    public double raysPerSecond() {
        return elapsedNanos == 0 ? 0 : primaryRays / seconds();
    }

    public double deEvaluationsPerSecond() {
        return elapsedNanos == 0 ? 0 : deEvaluations / seconds();
    }

    static RenderStats sum(RenderContext[] contexts, long elapsed, int w, int h, int ss, int threads, int passes, boolean complete) {
        long pixels = 0, rays = 0, shadow = 0, de = 0, steps = 0, max = 0, hits = 0;
        for (RenderContext c : contexts) {
            if (c == null) continue;
            pixels += c.pixels;
            rays += c.primaryRays;
            shadow += c.shadowRays;
            de += c.deEvaluations;
            steps += c.marchSteps;
            max = Math.max(max, c.maxMarchSteps);
            hits += c.hits;
        }
        return new RenderStats(elapsed, w, h, ss, pixels, rays, shadow, de, steps, max, hits, threads, passes, complete);
    }
}

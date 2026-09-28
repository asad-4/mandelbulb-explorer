package com.mandelbulb.rendering;

import com.mandelbulb.scene.FractalPreset;
import com.mandelbulb.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

class RendererTest {

    @Test
    void mandelbulbCoversCentreAndBackgroundCoversCorners() {
        Scene scene = new Scene();
        Renderer r = new Renderer(scene, 64, 48);
        RenderContext ctx = new RenderContext();
        r.renderPixel(32, 24, 1, 1, ctx);
        assertEquals(1, ctx.hits, "centre ray hits the fractal");
        ctx.resetStats();
        r.renderPixel(0, 0, 1, 1, ctx);
        assertEquals(0, ctx.hits, "corner ray misses");
        assertNotNull(r.pick(32, 24));
        assertNull(r.pick(0, 0));
    }

    @Test
    void supersamplingCastsSquaredSampleCount() {
        Renderer r = new Renderer(new Scene(), 32, 32);
        RenderContext ctx = new RenderContext();
        r.renderPixel(16, 16, 3, 1, ctx);
        assertEquals(9, ctx.primaryRays);
        assertEquals(1, ctx.pixels);
    }

    @Test
    void outputIsOpaqueAndDeterministic() {
        Scene scene = new Scene();
        Renderer r = new Renderer(scene, 40, 30);
        RenderContext a = new RenderContext(), b = new RenderContext();
        for (int y = 0; y < 30; y += 3) {
            for (int x = 0; x < 40; x += 3) {
                int c1 = r.renderPixel(x, y, 1, 1, a);
                int c2 = r.renderPixel(x, y, 1, 1, b);
                assertEquals(c1, c2);
                assertEquals(0xFF, c1 >>> 24);
            }
        }
    }

    @Test
    void multithreadedRenderMatchesSingleThreadedRender() {
        Scene scene = new Scene();
        int[] single, multi;
        try (RenderEngine one = new RenderEngine(1)) {
            single = one.render(scene, 150, 100, 1, f -> { }, () -> false).pixels();
        }
        try (RenderEngine eight = new RenderEngine(8)) {
            RenderResult r = eight.render(scene, 150, 100, 1, f -> { }, () -> false);
            multi = r.pixels();
            assertEquals(150L * 100, r.stats().pixels());
            assertTrue(r.stats().deEvaluations() > r.stats().primaryRays());
            assertTrue(r.stats().averageSteps() > 1);
            assertEquals(8, r.stats().threads());
        }
        assertArrayEquals(single, multi, "tiles must be independent and race-free");
    }

    @Test
    void cancelledBlockingRenderThrows() {
        try (RenderEngine engine = new RenderEngine(4)) {
            assertThrows(CancellationException.class,
                    () -> engine.render(new Scene(), 400, 300, 2, f -> { }, () -> true));
        }
    }

    @Test
    void everyPresetRenders() {
        try (RenderEngine engine = new RenderEngine(4)) {
            for (FractalPreset p : FractalPreset.builtIns()) {
                Scene s = new Scene();
                p.applyTo(s);
                RenderResult r = engine.render(s, 48, 36, 1, f -> { }, () -> false);
                assertTrue(r.stats().hits() > 0, p.name() + " shows the fractal");
            }
        }
    }

    @Test
    void passScheduleIsCoarseToFine() {
        List<RenderEngine.Pass> passes = RenderEngine.planPasses(1920, 1080, 2, 1, true);
        assertTrue(passes.size() >= 3);
        for (int i = 1; i < passes.size(); i++) {
            assertTrue(passes.get(i).block() <= passes.get(i - 1).block());
        }
        RenderEngine.Pass last = passes.get(passes.size() - 1);
        assertEquals(1, last.block());
        assertEquals(2, last.supersampling());
        // Non-progressive: just the final pass. Playback: stop at block 2.
        assertEquals(1, RenderEngine.planPasses(1920, 1080, 1, 1, false).size());
        List<RenderEngine.Pass> preview = RenderEngine.planPasses(1920, 1080, 4, 2, true);
        assertEquals(2, preview.get(preview.size() - 1).block());
    }

    @Test
    void tilesCoverImageExactlyOnceCentreFirst() {
        int w = 300, h = 170;
        int[][] tiles = RenderEngine.tiles(w, h);
        boolean[] covered = new boolean[w * h];
        for (int[] t : tiles) {
            for (int y = t[1]; y < Math.min(t[1] + RenderEngine.TILE_SIZE, h); y++) {
                for (int x = t[0]; x < Math.min(t[0] + RenderEngine.TILE_SIZE, w); x++) {
                    assertFalse(covered[y * w + x], "overlap");
                    covered[y * w + x] = true;
                }
            }
        }
        for (boolean c : covered) assertTrue(c);
        int[] first = tiles[0];
        assertTrue(Math.abs(first[0] + 32 - w / 2.0) <= 64 && Math.abs(first[1] + 32 - h / 2.0) <= 64);
    }
}

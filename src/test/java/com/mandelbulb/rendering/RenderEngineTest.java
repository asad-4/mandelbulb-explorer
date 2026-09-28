package com.mandelbulb.rendering;

import com.mandelbulb.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RenderEngineTest {

    @Test
    void progressiveRenderCompletesAndPublishes() throws Exception {
        try (RenderEngine engine = new RenderEngine(4)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<RenderStats> stats = new AtomicReference<>();
            engine.addListener(new RenderListener() {
                @Override
                public void renderFinished(FrameBuffer frame, RenderStats s) {
                    stats.set(s);
                    done.countDown();
                }
            });
            long gen = engine.submit(new RenderRequest(new Scene(), 160, 120, 2, 1, true));
            assertTrue(done.await(60, TimeUnit.SECONDS));
            FrameBuffer f = engine.displayFrame();
            assertNotNull(f);
            assertEquals(gen, f.generation());
            assertTrue(f.isComplete());
            assertTrue(stats.get().complete());
            assertTrue(stats.get().passes() > 1);
        }
    }

    @Test
    void newestRequestWinsAndStaleJobsNeverPublish() throws Exception {
        try (RenderEngine engine = new RenderEngine(4)) {
            CountDownLatch done = new CountDownLatch(1);
            engine.addListener(new RenderListener() {
                @Override
                public void renderFinished(FrameBuffer frame, RenderStats s) {
                    done.countDown();
                }
            });
            // Many rapid requests (like a mouse drag); only the last may end up on screen.
            long last = 0;
            for (int i = 0; i < 25; i++) {
                Scene s = new Scene();
                s.fractal.power = 4 + i * 0.1;
                last = engine.submit(new RenderRequest(s, 320, 240, 1, 1, true));
            }
            assertTrue(done.await(60, TimeUnit.SECONDS));
            assertEquals(last, engine.displayFrame().generation());
            // Give any stragglers time to (wrongly) publish, then check again.
            Thread.sleep(200);
            assertEquals(last, engine.displayFrame().generation());
            assertFalse(engine.isBusy());
        }
    }

    @Test
    void cancelKeepsLastFrameAndStopsWork() throws Exception {
        try (RenderEngine engine = new RenderEngine(2)) {
            CountDownLatch firstPass = new CountDownLatch(1);
            engine.addListener(new RenderListener() {
                @Override
                public void passCompleted(FrameBuffer frame, int pass, int passCount, RenderStats s) {
                    firstPass.countDown();
                }
            });
            engine.submit(new RenderRequest(new Scene(), 1600, 1200, 4, 1, true));
            assertTrue(firstPass.await(60, TimeUnit.SECONDS));
            engine.cancel();
            FrameBuffer shown = engine.displayFrame();
            assertNotNull(shown);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (engine.activeWorkers() > 0 && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(0, engine.activeWorkers(), "workers stop promptly after cancel");
            assertFalse(shown.isComplete());
            assertSame(shown, engine.displayFrame());
        }
    }

    @Test
    void threadCountCanChange() {
        try (RenderEngine engine = new RenderEngine(2)) {
            engine.setThreadCount(3);
            assertEquals(3, engine.threadCount());
            RenderResult r = engine.render(new Scene(), 64, 48, 1, f -> { }, () -> false);
            assertEquals(3, r.stats().threads());
        }
    }
}

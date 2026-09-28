package com.mandelbulb.rendering;

import com.mandelbulb.scene.Scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Progressive, tile-based, multithreaded CPU renderer.
 *
 * <h2>Threads</h2>
 * A single coordinator thread runs one job at a time; each job renders a sequence of passes and
 * each pass is split into 64x64 tiles. A fixed pool of N worker threads pulls tiles from a shared
 * atomic counter (work stealing without queues), so load balances automatically even though
 * tiles on the fractal cost far more than background tiles. Each worker owns one
 * {@link RenderContext}; there is no locking in the pixel loop.
 *
 * <h2>Progressive refinement</h2>
 * Passes go from coarse to fine: pixel blocks of 16/8/4/2 (one sample per block, painted over the
 * whole block, with a proportionally larger hit epsilon so previews are cheap), then the full
 * resolution pass with supersampling. Each pass overwrites the previous one tile by tile, so the
 * image sharpens in place. Tiles are ordered from the centre outwards.
 *
 * <h2>Cancellation and stale frames</h2>
 * Every request increments a generation counter and cancels the running job; workers check the
 * cancel flag once per pixel row, so a stale job stops within milliseconds. Each job renders into
 * its own {@link FrameBuffer}, and a buffer is only published for display if its generation is
 * still the latest one (checked under the engine lock). Consequently a stale job can never
 * overwrite a newer frame; until the new job's first preview is ready the old image stays up,
 * which avoids flicker during camera drags.
 */
public final class RenderEngine implements RenderBackend, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RenderEngine.class.getName());
    static final int TILE_SIZE = 64;
    /** Target sample count of the first (coarsest) preview pass: fast enough to feel live. */
    private static final int FIRST_PASS_SAMPLES = 12_000;

    private final ExecutorService coordinator;
    private ExecutorService workers;
    private int threadCount;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicInteger activeWorkers = new AtomicInteger();
    private final List<RenderListener> listeners = new CopyOnWriteArrayList<>();
    private volatile Job current;
    private volatile FrameBuffer displayFrame;
    private volatile RenderStats lastStats;

    public RenderEngine(int threads) {
        this.coordinator = Executors.newSingleThreadExecutor(daemonFactory("mandelbulb-coordinator", Thread.NORM_PRIORITY));
        this.threadCount = Math.max(1, threads);
        this.workers = newWorkerPool(threadCount);
    }

    private static ThreadFactory daemonFactory(String name, int priority) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + "-" + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(priority);
            return t;
        };
    }

    private static ExecutorService newWorkerPool(int threads) {
        // Slightly below normal priority keeps the UI thread responsive under full load.
        return Executors.newFixedThreadPool(threads, daemonFactory("mandelbulb-worker", Thread.NORM_PRIORITY - 1));
    }

    @Override
    public String name() {
        return "CPU ray marcher";
    }

    public void addListener(RenderListener l) {
        listeners.add(l);
    }

    public void removeListener(RenderListener l) {
        listeners.remove(l);
    }

    /** Changes the worker count; running tasks finish on the old pool. */
    public synchronized void setThreadCount(int threads) {
        threads = Math.max(1, Math.min(256, threads));
        if (threads == threadCount) return;
        ExecutorService old = workers;
        workers = newWorkerPool(threads);
        threadCount = threads;
        old.shutdown();
    }

    public synchronized int threadCount() {
        return threadCount;
    }

    private synchronized ExecutorService workers() {
        return workers;
    }

    public int activeWorkers() {
        return activeWorkers.get();
    }

    /** Starts rendering {@code request}, cancelling whatever was running. */
    public synchronized long submit(RenderRequest request) {
        long gen = generation.incrementAndGet();
        Job old = current;
        if (old != null) old.cancel();
        Job job = new Job(gen, request, null, null);
        current = job;
        coordinator.execute(job::runInteractive);
        return gen;
    }

    /** Cancels the running job; the last displayed frame stays visible. */
    public synchronized void cancel() {
        generation.incrementAndGet();
        Job old = current;
        if (old != null) old.cancel();
        current = null;
    }

    public boolean isBusy() {
        Job j = current;
        return j != null && !j.finished && !j.cancelled;
    }

    /** Latest frame safe to display (never older than a newer published frame). */
    public FrameBuffer displayFrame() {
        return displayFrame;
    }

    public RenderStats lastStats() {
        return lastStats;
    }

    public RenderProgress progress() {
        Job j = current;
        if (j == null) {
            return RenderProgress.IDLE;
        }
        return j.progress();
    }

    private synchronized boolean publish(Job job) {
        if (job.generation == generation.get()) {
            displayFrame = job.frame;
            return true;
        }
        return false;
    }

    private boolean isLatest(Job job) {
        return job.generation == generation.get();
    }

    @Override
    public RenderResult render(Scene scene, int width, int height, int supersampling,
                               DoubleConsumer progress, BooleanSupplier cancelled) {
        RenderRequest req = new RenderRequest(scene, width, height, supersampling, 1, false);
        Job job = new Job(-1, req, progress, cancelled);
        job.execute();
        if (job.isCancelled()) {
            throw new CancellationException("Render cancelled");
        }
        if (job.failure != null) {
            if (job.failure instanceof RuntimeException re) throw re;
            if (job.failure instanceof Error e) throw e;
            throw new IllegalStateException(job.failure);
        }
        return new RenderResult(job.frame.pixels(), width, height, job.stats);
    }

    @Override
    public void close() {
        cancel();
        coordinator.shutdownNow();
        workers().shutdownNow();
    }

    /** One pass of the progressive schedule. */
    record Pass(int block, int supersampling, double epsScale) {
    }

    /**
     * Pass schedule: halve the block size from a start size chosen so the first pass has about
     * {@link #FIRST_PASS_SAMPLES} samples, down to {@code finalBlock}.
     */
    static List<Pass> planPasses(int width, int height, int supersampling, int finalBlock, boolean progressive) {
        List<Pass> passes = new ArrayList<>();
        if (progressive) {
            int block = 1;
            while (block < 16 && (double) width * height / ((double) block * block) > FIRST_PASS_SAMPLES) {
                block *= 2;
            }
            for (int b = block; b > Math.max(1, finalBlock); b /= 2) {
                passes.add(new Pass(b, 1, b));
            }
        }
        if (finalBlock <= 1) {
            passes.add(new Pass(1, supersampling, 1));
        } else {
            passes.add(new Pass(finalBlock, 1, finalBlock));
        }
        return passes;
    }

    /** Tile origins ordered by distance from the image centre (the interesting part first). */
    static int[][] tiles(int width, int height) {
        int tx = (width + TILE_SIZE - 1) / TILE_SIZE, ty = (height + TILE_SIZE - 1) / TILE_SIZE;
        int[][] tiles = new int[tx * ty][];
        int k = 0;
        for (int y = 0; y < ty; y++) {
            for (int x = 0; x < tx; x++) {
                tiles[k++] = new int[] {x * TILE_SIZE, y * TILE_SIZE};
            }
        }
        double cx = width * 0.5, cy = height * 0.5;
        Arrays.sort(tiles, Comparator.comparingDouble(t -> {
            double dx = t[0] + TILE_SIZE * 0.5 - cx, dy = t[1] + TILE_SIZE * 0.5 - cy;
            return dx * dx + dy * dy;
        }));
        return tiles;
    }

    private final class Job {
        final long generation;
        final RenderRequest request;
        final DoubleConsumer progressListener;
        final BooleanSupplier externalCancel;
        volatile boolean cancelled;
        volatile boolean finished;
        volatile int passIndex;
        volatile int passCount;
        volatile int block = 1;
        volatile int passSupersampling = 1;
        final AtomicLong workDone = new AtomicLong();
        long workTotal = 1;
        long startNanos;
        FrameBuffer frame;
        RenderStats stats;
        Throwable failure;

        Job(long generation, RenderRequest request, DoubleConsumer progressListener, BooleanSupplier externalCancel) {
            this.generation = generation;
            this.request = request;
            this.progressListener = progressListener;
            this.externalCancel = externalCancel;
        }

        void cancel() {
            cancelled = true;
        }

        boolean isCancelled() {
            return cancelled || (externalCancel != null && externalCancel.getAsBoolean());
        }

        RenderProgress progress() {
            return new RenderProgress(generation, passIndex, passCount, block, passSupersampling,
                    Math.min(1.0, workDone.get() / (double) workTotal), !finished && !cancelled,
                    request.width(), request.height(), startNanos == 0 ? 0 : (System.nanoTime() - startNanos) / 1e9);
        }

        void runInteractive() {
            if (!isLatest(this) || isCancelled()) {
                // Superseded before it even started: skip entirely.
                finished = true;
                return;
            }
            execute();
            if (failure != null && isLatest(this)) {
                for (RenderListener l : listeners) l.renderFailed(failure);
            }
        }

        void execute() {
            startNanos = System.nanoTime();
            int w = request.width(), h = request.height();
            try {
                Renderer renderer = new Renderer(request.scene(), w, h);
                frame = new FrameBuffer(w, h, generation);
                List<Pass> passes = planPasses(w, h, request.supersampling(), request.finalBlock(), request.progressive());
                passCount = passes.size();
                workTotal = 0;
                for (Pass p : passes) {
                    workTotal += passWork(p, w, h);
                }
                int threads = threadCount();
                RenderContext[] contexts = new RenderContext[threads];
                for (int i = 0; i < threads; i++) contexts[i] = new RenderContext();
                int[][] tiles = tiles(w, h);

                for (int i = 0; i < passes.size(); i++) {
                    Pass pass = passes.get(i);
                    passIndex = i + 1;
                    block = pass.block();
                    passSupersampling = pass.supersampling();
                    runPass(renderer, pass, tiles, contexts);
                    if (isCancelled()) {
                        return;
                    }
                    stats = RenderStats.sum(contexts, System.nanoTime() - startNanos, w, h,
                            request.supersampling(), threads, i + 1, i == passes.size() - 1);
                    if (generation >= 0) {
                        if (i == 0 && !publish(this)) {
                            return;
                        }
                        frame.touch();
                        for (RenderListener l : listeners) l.passCompleted(frame, i + 1, passes.size(), stats);
                    }
                }
                frame.markComplete();
                if (generation >= 0 && isLatest(this)) {
                    lastStats = stats;
                    for (RenderListener l : listeners) l.renderFinished(frame, stats);
                }
            } catch (OutOfMemoryError e) {
                frame = null;
                failure = new IllegalStateException("Out of memory rendering " + w + "×" + h
                        + ". Lower the resolution or supersampling, or start the JVM with a larger -Xmx.", e);
                LOG.log(Level.SEVERE, "Out of memory during render", e);
            } catch (RuntimeException e) {
                failure = e;
                LOG.log(Level.SEVERE, "Render failed", e);
            } finally {
                finished = true;
            }
        }

        private long passWork(Pass p, int w, int h) {
            long samples = ((long) (w + p.block() - 1) / p.block()) * ((h + p.block() - 1) / p.block());
            return samples * p.supersampling() * p.supersampling();
        }

        private void runPass(Renderer renderer, Pass pass, int[][] tiles, RenderContext[] contexts) {
            AtomicInteger next = new AtomicInteger();
            List<Callable<Void>> tasks = new ArrayList<>(contexts.length);
            for (RenderContext ctx : contexts) {
                tasks.add(() -> {
                    activeWorkers.incrementAndGet();
                    try {
                        int k;
                        while (!isCancelled() && (k = next.getAndIncrement()) < tiles.length) {
                            renderTile(renderer, pass, tiles[k], ctx);
                        }
                    } finally {
                        activeWorkers.decrementAndGet();
                    }
                    return null;
                });
            }
            List<Future<Void>> futures;
            while (true) {
                try {
                    futures = workers().invokeAll(tasks);
                    break;
                } catch (RejectedExecutionException e) {
                    // Pool was swapped by setThreadCount; retry on the new one.
                    if (isCancelled()) return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancel();
                    return;
                }
            }
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof Error err) throw err;
                    if (cause instanceof RuntimeException re) throw re;
                    throw new IllegalStateException(cause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancel();
                    return;
                }
            }
        }

        private void renderTile(Renderer renderer, Pass pass, int[] tile, RenderContext ctx) {
            final int w = frame.width(), h = frame.height();
            final int[] px = frame.pixels();
            final int b = pass.block();
            final int ss = pass.supersampling();
            final int x1 = Math.min(tile[0] + TILE_SIZE, w), y1 = Math.min(tile[1] + TILE_SIZE, h);
            long samples = 0;
            for (int y = tile[1]; y < y1; y += b) {
                if (isCancelled()) return;
                for (int x = tile[0]; x < x1; x += b) {
                    int c;
                    if (b == 1) {
                        c = renderer.renderPixel(x, y, ss, pass.epsScale(), ctx);
                    } else {
                        c = renderer.renderBlock(x, y, b, pass.epsScale(), ctx);
                    }
                    int yEnd = Math.min(y + b, h), xEnd = Math.min(x + b, w);
                    for (int yy = y; yy < yEnd; yy++) {
                        int row = yy * w;
                        for (int xx = x; xx < xEnd; xx++) {
                            px[row + xx] = c;
                        }
                    }
                    samples += (long) ss * ss;
                }
            }
            long done = workDone.addAndGet(samples);
            frame.touch();
            if (progressListener != null) {
                progressListener.accept(Math.min(1.0, done / (double) workTotal));
            }
        }
    }
}

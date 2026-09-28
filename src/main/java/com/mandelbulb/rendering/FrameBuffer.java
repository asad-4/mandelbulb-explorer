package com.mandelbulb.rendering;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Packed-ARGB image owned by exactly one render job.
 *
 * <p>Workers write disjoint tiles concurrently; the UI polls {@link #version()} and copies the
 * pixels when it changed. A reader may briefly see a half-written tile, which is harmless for a
 * progressive display, and the final copy after {@link #isComplete()} is exact. Because every job
 * gets its own buffer, a cancelled (stale) job can never draw into a newer frame.
 */
public final class FrameBuffer {

    private final int[] pixels;
    private final int width;
    private final int height;
    private final long generation;
    private final AtomicInteger version = new AtomicInteger();
    private volatile boolean complete;

    public FrameBuffer(int width, int height, long generation) {
        this.width = width;
        this.height = height;
        this.generation = generation;
        this.pixels = new int[Math.multiplyExact(width, height)];
    }

    public int[] pixels() {
        return pixels;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public long generation() {
        return generation;
    }

    public int version() {
        return version.get();
    }

    void touch() {
        version.incrementAndGet();
    }

    public boolean isComplete() {
        return complete;
    }

    void markComplete() {
        complete = true;
        version.incrementAndGet();
    }
}

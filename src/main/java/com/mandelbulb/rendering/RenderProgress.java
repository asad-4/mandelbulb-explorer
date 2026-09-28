package com.mandelbulb.rendering;

/** Snapshot of the interactive render's progress, polled by the UI. */
public record RenderProgress(long generation, int pass, int passCount, int block, int supersampling,
                             double fraction, boolean running, int width, int height, double elapsedSeconds) {

    public static final RenderProgress IDLE = new RenderProgress(0, 0, 0, 1, 1, 0, false, 0, 0, 0);
}

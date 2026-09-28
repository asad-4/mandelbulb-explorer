package com.mandelbulb.rendering;

/** Callbacks from the render coordinator thread. Implementations must be thread-safe. */
public interface RenderListener {

    /** A pass of the current (non-stale) job finished; {@code frame} is on screen now. */
    default void passCompleted(FrameBuffer frame, int pass, int passCount, RenderStats statsSoFar) {
    }

    /** The job finished all passes. */
    default void renderFinished(FrameBuffer frame, RenderStats stats) {
    }

    /** The job failed (e.g. out of memory). */
    default void renderFailed(Throwable error) {
    }
}

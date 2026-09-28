package com.mandelbulb.rendering;

import com.mandelbulb.scene.Scene;

/**
 * An interactive render request.
 *
 * @param scene         snapshot (the engine does not copy it again; do not mutate afterwards)
 * @param supersampling s for the final pass (s x s samples per pixel)
 * @param finalBlock    stop refining at this block size (1 = full quality); used for fast
 *                      animation playback
 * @param progressive   render coarse previews first
 */
public record RenderRequest(Scene scene, int width, int height, int supersampling, int finalBlock, boolean progressive) {

    public RenderRequest {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid render size " + width + "x" + height);
        }
        supersampling = Math.max(1, Math.min(4, supersampling));
        finalBlock = Integer.highestOneBit(Math.max(1, Math.min(16, finalBlock)));
    }
}

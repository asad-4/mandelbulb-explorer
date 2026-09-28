package com.mandelbulb.rendering;

import com.mandelbulb.scene.Scene;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/**
 * Extension point for alternative renderer implementations.
 *
 * <p>Export, benchmarking and animation recording only depend on this interface. The CPU
 * {@link RenderEngine} implements it today; a GPU backend (e.g. an OpenGL/Vulkan compute or
 * fragment shader port of {@link Renderer}, fed with the same {@link Scene} snapshot as uniforms)
 * could be dropped in without touching the UI or the scene model.
 */
public interface RenderBackend {

    String name();

    /**
     * Renders a complete image at full quality, blocking the calling thread.
     *
     * @param progress  receives completion fraction in [0,1] (may be called from worker threads)
     * @param cancelled polled regularly; when it returns true the render stops
     * @throws CancellationException if cancelled
     */
    RenderResult render(Scene scene, int width, int height, int supersampling,
                        DoubleConsumer progress, BooleanSupplier cancelled);
}

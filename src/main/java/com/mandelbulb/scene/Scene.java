package com.mandelbulb.scene;

import com.mandelbulb.camera.Camera;
import com.mandelbulb.fractal.FractalParameters;
import com.mandelbulb.rendering.RenderSettings;
import com.mandelbulb.util.Settings;

/**
 * Complete description of what to render: fractal, camera, lights, colouring and quality.
 *
 * <p>The renderer only ever sees deep copies ({@link #copy()}) of a Scene, taken on the UI thread
 * when a render is requested. Render threads therefore never observe a half-edited scene.
 */
public final class Scene extends Settings {

    public FractalParameters fractal = new FractalParameters();
    public RenderSettings render = new RenderSettings();
    public LightingSettings lighting = new LightingSettings();
    public ColorSettings color = new ColorSettings();
    public Camera camera = Camera.defaultCamera();

    public Scene copy() {
        return deepCopy(this);
    }

    @Override
    public void validate() {
        fractal.validate();
        render.validate();
        lighting.validate();
        color.validate();
        camera = camera == null ? Camera.defaultCamera() : camera.sanitized();
    }
}

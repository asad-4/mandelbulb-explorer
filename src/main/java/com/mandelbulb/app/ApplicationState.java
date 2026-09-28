package com.mandelbulb.app;

import com.mandelbulb.animation.AnimationController;
import com.mandelbulb.camera.Camera;
import com.mandelbulb.camera.CameraController;
import com.mandelbulb.fractal.FractalParameters;
import com.mandelbulb.fractal.Mandelbulb;
import com.mandelbulb.math.Vector3;
import com.mandelbulb.rendering.RenderEngine;
import com.mandelbulb.rendering.RenderRequest;
import com.mandelbulb.rendering.Renderer;
import com.mandelbulb.rendering.ResolutionMode;
import com.mandelbulb.scene.FractalPreset;
import com.mandelbulb.scene.Scene;

import java.util.ArrayList;
import java.util.List;

/**
 * Central application model shared by all UI components.
 *
 * <p>Holds the live (editable) scene, the camera controller, the render engine and the animation
 * controller, and decides <em>when</em> to render. UI code edits the scene and calls
 * {@link #changed()}; the next {@link #pulse(double)} (one per UI frame) coalesces all changes of
 * that frame into a single render request built from a deep-copied snapshot. This class is only
 * used from the JavaFX application thread and contains no JavaFX types itself.
 */
public final class ApplicationState {

    private final Scene scene = new Scene();
    private final CameraController camera = new CameraController();
    private final AnimationController animation = new AnimationController();
    private final PresetStore presets = new PresetStore();
    private final RenderEngine engine;
    private final List<Runnable> sceneReplacedListeners = new ArrayList<>();

    private boolean renderRequested = true;
    private boolean paused;
    private boolean overlayVisible = true;
    private boolean lightIndicatorVisible = true;
    private int viewportWidth = 960;
    private int viewportHeight = 640;
    private double hiDpiScale = 1.0;
    private FractalParameters cachedParams;
    private Mandelbulb cachedFractal;

    public ApplicationState() {
        scene.validate();
        engine = new RenderEngine(scene.render.effectiveThreads());
        camera.setSurfaceDistance(this::worldDistance);
        camera.setCamera(scene.camera, true);
        camera.setHome(scene.camera);
        presets.loadBookmarks(camera);
    }

    public Scene scene() {
        return scene;
    }

    public CameraController camera() {
        return camera;
    }

    public RenderEngine engine() {
        return engine;
    }

    public AnimationController animation() {
        return animation;
    }

    public PresetStore presets() {
        return presets;
    }

    /** Call after editing any scene parameter. */
    public void changed() {
        scene.validate();
        engine.setThreadCount(scene.render.effectiveThreads());
        requestRender();
    }

    public void requestRender() {
        renderRequested = true;
    }

    public boolean isPaused() {
        return paused;
    }

    /** Pausing stops refinement immediately but keeps the current image. */
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            engine.cancel();
        } else {
            requestRender();
        }
    }

    public boolean isOverlayVisible() {
        return overlayVisible;
    }

    public void setOverlayVisible(boolean v) {
        overlayVisible = v;
    }

    public boolean isLightIndicatorVisible() {
        return lightIndicatorVisible;
    }

    public void setLightIndicatorVisible(boolean v) {
        lightIndicatorVisible = v;
    }

    public void setViewportSize(int width, int height, double hiDpiScale) {
        width = Math.max(16, width);
        height = Math.max(16, height);
        if (width != viewportWidth || height != viewportHeight || hiDpiScale != this.hiDpiScale) {
            viewportWidth = width;
            viewportHeight = height;
            this.hiDpiScale = hiDpiScale;
            if (scene.render.resolutionMode == ResolutionMode.VIEWPORT) {
                requestRender();
            }
        }
    }

    public int viewportWidth() {
        return viewportWidth;
    }

    public int viewportHeight() {
        return viewportHeight;
    }

    /** Size of the interactive render in pixels. */
    public int[] renderSize() {
        if (scene.render.resolutionMode == ResolutionMode.FIXED) {
            return new int[] {scene.render.fixedResolution.width, scene.render.fixedResolution.height};
        }
        double s = scene.render.viewportScale;
        return new int[] {Math.max(16, (int) Math.round(viewportWidth * s)), Math.max(16, (int) Math.round(viewportHeight * s))};
    }

    /** Deep copy of what is currently being displayed (camera and animation applied). */
    public Scene snapshot() {
        Scene s = scene.copy();
        s.camera = camera.current();
        if (animation.isActive()) {
            s = animation.sceneAt(s, animation.time());
        }
        return s;
    }

    /**
     * Per-frame update: camera smoothing, animation clock and (coalesced) render submission.
     *
     * @param dt seconds since the previous pulse
     */
    public void pulse(double dt) {
        if (camera.update(dt)) {
            requestRender();
        }
        if (animation.state() == AnimationController.State.PLAYING) {
            animation.advance(dt);
            // Real-time playback: always show the newest animation time the CPU can keep up with.
            if (!engine.isBusy()) {
                requestRender();
            }
        }
        if (renderRequested && !paused) {
            renderRequested = false;
            submitRender();
        }
    }

    private void submitRender() {
        int[] size = renderSize();
        Scene snap = snapshot();
        boolean playing = animation.state() == AnimationController.State.PLAYING;
        int finalBlock = playing ? animation.settings().playbackBlock : 1;
        int ss = playing ? 1 : snap.render.supersampling;
        // During playback every frame is rendered in one pass at the playback quality: restarting
        // the coarse-to-fine sequence for each frame would make the animation flicker.
        engine.submit(new RenderRequest(snap, size[0], size[1], ss, finalBlock, snap.render.progressive && !playing));
    }

    /** Applies a built-in preset; the camera glides to the preset's view. */
    public void applyPreset(FractalPreset preset) {
        preset.applyTo(scene);
        camera.setCamera(scene.camera, false);
        sceneReplaced();
    }

    /** Replaces all settings (e.g. from a custom preset) and moves the camera. */
    public void applyScene(Scene s) {
        s.validate();
        scene.copyFrom(s);
        camera.setCamera(scene.camera, false);
        sceneReplaced();
    }

    private void sceneReplaced() {
        cachedParams = null;
        changed();
        for (Runnable r : sceneReplacedListeners) r.run();
    }

    public void addSceneReplacedListener(Runnable r) {
        sceneReplacedListeners.add(r);
    }

    /** Current scene including the live camera, for saving presets. */
    public Scene sceneWithCamera() {
        Scene s = scene.copy();
        s.camera = camera.goal();
        return s;
    }

    /**
     * Distance estimate at a world-space point, honouring object rotation; used to scale camera
     * speeds. The Mandelbulb instance is cached until the fractal parameters change.
     */
    public double worldDistance(Vector3 world) {
        FractalParameters p = scene.fractal;
        if (cachedFractal == null || cachedParams == null || !sameFractal(cachedParams, p)) {
            cachedParams = p.copy();
            cachedFractal = new Mandelbulb(cachedParams);
        }
        double az = Math.toRadians(p.rotationZ), ax = Math.toRadians(p.rotationX);
        // Inverse object rotation (R = Rz * Rx, so R^-1 = Rx^-1 * Rz^-1).
        double cz = Math.cos(-az), sz = Math.sin(-az);
        double x1 = cz * world.x() - sz * world.y(), y1 = sz * world.x() + cz * world.y(), z1 = world.z();
        double cx = Math.cos(-ax), sx = Math.sin(-ax);
        double y2 = cx * y1 - sx * z1, z2 = sx * y1 + cx * z1;
        return cachedFractal.distance(x1, y2, z2);
    }

    private static boolean sameFractal(FractalParameters a, FractalParameters b) {
        return a.power == b.power && a.maxIterations == b.maxIterations && a.bailout == b.bailout
                && a.juliaMode == b.juliaMode && a.juliaX == b.juliaX && a.juliaY == b.juliaY && a.juliaZ == b.juliaZ;
    }

    /**
     * Casts a ray through a point of the rendered image (render-pixel coordinates).
     *
     * @return world-space surface point or null
     */
    public Vector3 pick(double renderX, double renderY) {
        int[] size = renderSize();
        Renderer r = new Renderer(snapshot(), size[0], size[1]);
        return r.pick(renderX, renderY);
    }

    public Camera currentCamera() {
        return camera.current();
    }

    public void shutdown() {
        presets.saveBookmarks(camera);
        engine.close();
    }
}

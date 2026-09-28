package com.mandelbulb.animation;

import com.mandelbulb.camera.Camera;
import com.mandelbulb.export.ImageExporter;
import com.mandelbulb.math.MathUtils;
import com.mandelbulb.rendering.RenderBackend;
import com.mandelbulb.rendering.RenderResult;
import com.mandelbulb.scene.Scene;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/**
 * Time-based animation of scene parameters (toolkit independent).
 *
 * <p>The animated scene is a pure function of (base scene, time): {@link #sceneAt(Scene, double)}
 * never mutates the user's scene. Interactive playback advances time by wall-clock seconds;
 * recording steps time by exactly 1/fps per frame, so exported sequences are deterministic and
 * independent of how long each frame took to render.
 */
public final class AnimationController {

    public enum State { STOPPED, PLAYING, PAUSED }

    private final AnimationSettings settings = new AnimationSettings();
    private final CameraPath path = new CameraPath();
    private volatile State state = State.STOPPED;
    private double time;

    public AnimationSettings settings() {
        return settings;
    }

    public CameraPath path() {
        return path;
    }

    public State state() {
        return state;
    }

    public boolean isActive() {
        return state != State.STOPPED;
    }

    public double time() {
        return time;
    }

    public void play() {
        state = State.PLAYING;
    }

    public void pause() {
        if (state == State.PLAYING) state = State.PAUSED;
    }

    public void stop() {
        state = State.STOPPED;
        time = 0;
    }

    public void setTime(double t) {
        time = Math.max(0, t);
    }

    /** Advances playback time (only while playing). */
    public void advance(double seconds) {
        if (state == State.PLAYING) {
            time += MathUtils.clamp(seconds, 0, 0.5);
            if (settings.flyThrough && !settings.loop && time > settings.flyDuration) {
                time = settings.flyDuration;
                state = State.PAUSED;
            }
        }
    }

    /** Length of a recording in seconds. */
    public double recordingDuration() {
        return settings.flyThrough && path.size() >= 2 ? settings.flyDuration : settings.duration;
    }

    /** The animated scene at time t, derived from {@code base} (which is not modified). */
    public Scene sceneAt(Scene base, double t) {
        AnimationSettings a = settings;
        Scene s = base.copy();
        if (a.rotateObject) {
            s.fractal.rotationZ = wrapDegrees(base.fractal.rotationZ + a.rotationSpeed * t);
        }
        if (a.orbitCamera) {
            s.camera = s.camera.orbit(Math.toRadians(a.orbitSpeed * t), 0);
        }
        if (a.animatePower) {
            double phase = 0.5 - 0.5 * Math.cos(MathUtils.TAU * t / a.powerPeriod);
            s.fractal.power = MathUtils.lerp(a.powerMin, a.powerMax, phase);
        }
        if (a.animateLight) {
            s.lighting.key.azimuth = wrapDegrees(base.lighting.key.azimuth + a.lightSpeed * t);
        }
        if (a.animatePalette) {
            s.color.paletteOffset = base.color.paletteOffset + a.paletteSpeed * t;
        }
        if (a.flyThrough && path.size() >= 2) {
            double u = t / a.flyDuration;
            u = a.loop ? u - Math.floor(u) : Math.min(1, u);
            Camera c = path.sample(u, a.easing);
            if (c != null) s.camera = c;
        }
        s.validate();
        return s;
    }

    private static double wrapDegrees(double d) {
        d = d % 360;
        return d > 180 ? d - 360 : (d < -180 ? d + 360 : d);
    }

    /** Receives progress of a recording. */
    public interface RecordListener {
        void frameStarted(int frame, int frameCount);

        void progress(double overallFraction);
    }

    /**
     * Renders the animation to a numbered PNG sequence ({@code frame_00000.png}, ...), which any
     * video tool can encode, e.g. {@code ffmpeg -framerate 30 -i frame_%05d.png -pix_fmt yuv420p out.mp4}.
     *
     * @return number of frames written
     */
    public int record(RenderBackend backend, Scene base, File directory, int width, int height, int supersampling,
                      RecordListener listener, BooleanSupplier cancelled) throws IOException {
        int frames = Math.max(1, (int) Math.round(recordingDuration() * settings.fps));
        for (int i = 0; i < frames; i++) {
            if (cancelled.getAsBoolean()) throw new CancellationException("Recording cancelled");
            final int frame = i;
            listener.frameStarted(i + 1, frames);
            Scene scene = sceneAt(base, i / (double) settings.fps);
            DoubleConsumer progress = f -> listener.progress((frame + f) / frames);
            RenderResult r = backend.render(scene, width, height, supersampling, progress, cancelled);
            File file = new File(directory, String.format(Locale.ROOT, "frame_%05d.png", i));
            ImageExporter.write(r.pixels(), r.width(), r.height(), file, ImageExporter.Format.PNG, 1f);
        }
        listener.progress(1.0);
        return frames;
    }
}

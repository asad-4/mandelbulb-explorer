package com.mandelbulb.animation;

import com.mandelbulb.math.MathUtils;
import com.mandelbulb.util.Settings;

/** Which scene parameters are animated over time, and how fast. */
public final class AnimationSettings extends Settings {

    public boolean rotateObject = true;
    /** Degrees per second about the vertical axis. */
    public double rotationSpeed = 12;

    public boolean orbitCamera = false;
    public double orbitSpeed = 10;

    public boolean animatePower = false;
    public double powerMin = 6;
    public double powerMax = 10;
    /** Seconds for a full min -&gt; max -&gt; min cycle. */
    public double powerPeriod = 20;

    public boolean animateLight = false;
    public double lightSpeed = 30;

    public boolean animatePalette = false;
    /** Palette offset per second. */
    public double paletteSpeed = 0.05;

    public boolean flyThrough = false;
    public double flyDuration = 12;
    public Easing easing = Easing.EASE_IN_OUT_SINE;
    public boolean loop = true;

    /** Frames per second for recording (and the playback frame budget). */
    public int fps = 30;
    /** Length of a recording in seconds (fly-throughs record exactly one flight). */
    public double duration = 10;
    /** Playback quality: preview block size (1 = full, 2 = half, 4 = quarter resolution). */
    public int playbackBlock = 2;

    public AnimationSettings copy() {
        return deepCopy(this);
    }

    @Override
    public void validate() {
        rotationSpeed = MathUtils.sanitize(rotationSpeed, -360, 360, 12);
        orbitSpeed = MathUtils.sanitize(orbitSpeed, -360, 360, 10);
        powerMin = MathUtils.sanitize(powerMin, 2, 20, 6);
        powerMax = MathUtils.sanitize(powerMax, 2, 20, 10);
        powerPeriod = MathUtils.sanitize(powerPeriod, 0.5, 600, 20);
        lightSpeed = MathUtils.sanitize(lightSpeed, -720, 720, 30);
        paletteSpeed = MathUtils.sanitize(paletteSpeed, -5, 5, 0.05);
        flyDuration = MathUtils.sanitize(flyDuration, 0.5, 600, 12);
        if (easing == null) easing = Easing.EASE_IN_OUT_SINE;
        fps = MathUtils.clamp(fps, 1, 120);
        duration = MathUtils.sanitize(duration, 0.1, 3600, 10);
        playbackBlock = Integer.highestOneBit(MathUtils.clamp(playbackBlock, 1, 8));
    }
}

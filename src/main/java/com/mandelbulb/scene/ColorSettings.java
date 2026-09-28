package com.mandelbulb.scene;

import com.mandelbulb.fractal.TrapType;
import com.mandelbulb.math.MathUtils;
import com.mandelbulb.rendering.ColorMode;
import com.mandelbulb.rendering.Palette;
import com.mandelbulb.util.Settings;

/** Procedural surface colouring. */
public final class ColorSettings extends Settings {

    public Palette palette = Palette.GOLD;
    public ColorMode mode = ColorMode.COMBINED;
    /** Shift of the palette lookup (animatable, wraps around). */
    public double paletteOffset = 0.0;
    /** How many times the palette repeats over the value range. */
    public double paletteScale = 1.0;
    public boolean reversePalette = false;

    public boolean orbitTrapEnabled = true;
    public TrapType trapType = TrapType.ORIGIN;
    /** How strongly the orbit-trap colour is blended over the base colour mode. */
    public double trapBlend = 0.55;
    /** Sensitivity of the trap value -&gt; palette mapping. */
    public double trapScale = 1.0;

    public double saturation = 1.0;
    public double brightness = 1.0;

    public ColorSettings copy() {
        return deepCopy(this);
    }

    @Override
    public void validate() {
        if (palette == null) palette = Palette.GOLD;
        if (mode == null) mode = ColorMode.COMBINED;
        if (trapType == null) trapType = TrapType.ORIGIN;
        paletteOffset = MathUtils.sanitize(paletteOffset, -1000, 1000, 0);
        paletteScale = MathUtils.sanitize(paletteScale, 0.05, 20, 1);
        trapBlend = MathUtils.sanitize(trapBlend, 0, 1, 0.55);
        trapScale = MathUtils.sanitize(trapScale, 0.05, 20, 1);
        saturation = MathUtils.sanitize(saturation, 0, 2, 1);
        brightness = MathUtils.sanitize(brightness, 0.1, 3, 1);
    }
}

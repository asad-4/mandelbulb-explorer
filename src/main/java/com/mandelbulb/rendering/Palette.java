package com.mandelbulb.rendering;

import com.mandelbulb.math.Color3;
import com.mandelbulb.math.MathUtils;

/**
 * Colour gradients. Each palette is a list of sRGB stops; {@link #buildLinearLut(int)} converts
 * it into a dense lookup table in linear space so the renderer can sample it with one
 * interpolation and no allocation.
 */
public enum Palette {
    FIRE("Fire", "#1a0500", "#6b0f00", "#c62e00", "#ff7a00", "#ffc640", "#fff3c0"),
    ICE("Ice", "#0b1a2e", "#1f4e79", "#4f9dd6", "#a8dcf5", "#eaf8ff"),
    PLASMA("Plasma", "#0d0887", "#6a00a8", "#b12a90", "#e16462", "#fca636", "#f0f921"),
    OCEAN("Ocean", "#021526", "#03346e", "#0e7490", "#22b8b0", "#a7f3d0"),
    EMERALD("Emerald", "#022c22", "#065f46", "#10b981", "#6ee7b7", "#ecfdf5"),
    PURPLE("Purple", "#1e0536", "#4c1d95", "#7c3aed", "#c084fc", "#f3e8ff"),
    GOLD("Gold", "#2b1700", "#7a4a00", "#c8891a", "#f2c14e", "#fff1c1"),
    MONOCHROME("Monochrome", "#202020", "#707070", "#c0c0c0", "#f5f5f5"),
    RAINBOW("Rainbow", "#ff2020", "#ff9020", "#ffee20", "#30e040", "#20e0e0", "#3050ff", "#a030ff"),
    ELECTRIC_BLUE("Electric Blue", "#000814", "#001d3d", "#0055ff", "#00b4ff", "#7df9ff", "#ffffff"),
    LAVA("Lava", "#1a0a05", "#5c0f00", "#d63a00", "#ffae00", "#fff0b0"),
    SUNSET("Sunset", "#2d0b3a", "#8e2c6b", "#e8505b", "#f9a54a", "#fde9a8"),
    COPPER("Copper", "#1b0f0a", "#6e3b1f", "#b87333", "#e8b07c", "#fbe6d0"),
    BONE("Bone", "#3a3530", "#8f8577", "#d8cfc0", "#fbf7f0");

    private final String label;
    private final Color3[] stops;

    Palette(String label, String... hexStops) {
        this.label = label;
        this.stops = new Color3[hexStops.length];
        for (int i = 0; i < hexStops.length; i++) {
            stops[i] = Color3.fromHex(hexStops[i]);
        }
    }

    public Color3[] stops() {
        return stops.clone();
    }

    /** sRGB colour at t in [0,1] (linear interpolation between stops). */
    public Color3 sample(double t) {
        t = MathUtils.saturate(t) * (stops.length - 1);
        int i = Math.min((int) t, stops.length - 2);
        double f = t - i;
        // Smoothstep between stops removes the visible "kinks" of piecewise-linear gradients.
        f = f * f * (3 - 2 * f);
        return stops[i].lerp(stops[i + 1], f);
    }

    /** Interleaved linear RGB table of {@code size} entries covering t in [0,1]. */
    public double[] buildLinearLut(int size) {
        double[] lut = new double[size * 3];
        for (int i = 0; i < size; i++) {
            Color3 c = sample(i / (double) (size - 1)).toLinear();
            lut[i * 3] = c.r();
            lut[i * 3 + 1] = c.g();
            lut[i * 3 + 2] = c.b();
        }
        return lut;
    }

    @Override
    public String toString() {
        return label;
    }
}

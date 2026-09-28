package com.mandelbulb.math;

import java.util.Locale;

/**
 * Immutable RGB colour with components in [0,1], stored in sRGB (display) space as the user
 * picks it. {@link #toLinear()} converts to the linear space in which lighting is computed.
 */
public record Color3(double r, double g, double b) {

    public static final Color3 BLACK = new Color3(0, 0, 0);
    public static final Color3 WHITE = new Color3(1, 1, 1);

    public static Color3 fromHex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) {
            throw new IllegalArgumentException("Expected #RRGGBB but got " + hex);
        }
        int v = Integer.parseInt(h, 16);
        return new Color3(((v >> 16) & 0xFF) / 255.0, ((v >> 8) & 0xFF) / 255.0, (v & 0xFF) / 255.0);
    }

    public String toHex() {
        return String.format(Locale.ROOT, "#%02X%02X%02X", to8(r), to8(g), to8(b));
    }

    private static int to8(double c) {
        return (int) Math.round(MathUtils.saturate(c) * 255);
    }

    /** sRGB electro-optical transfer function (exact piecewise form). */
    public static double srgbToLinear(double c) {
        c = MathUtils.saturate(c);
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double linearToSrgb(double c) {
        c = MathUtils.saturate(c);
        return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
    }

    public Color3 toLinear() {
        return new Color3(srgbToLinear(r), srgbToLinear(g), srgbToLinear(b));
    }

    public Color3 lerp(Color3 o, double t) {
        return new Color3(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t);
    }

    public Color3 scale(double s) {
        return new Color3(r * s, g * s, b * s);
    }

    public Color3 sanitized() {
        return new Color3(MathUtils.sanitize(r, 0, 1, 0), MathUtils.sanitize(g, 0, 1, 0), MathUtils.sanitize(b, 0, 1, 0));
    }
}

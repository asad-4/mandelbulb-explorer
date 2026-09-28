package com.mandelbulb.math;

/** Small scalar helpers shared by the renderer, camera and UI. All methods are allocation-free. */
public final class MathUtils {

    public static final double TAU = Math.PI * 2;

    private MathUtils() {
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static double saturate(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = saturate((x - edge0) / (edge1 - edge0));
        return t * t * (3 - 2 * t);
    }

    public static double fract(double v) {
        return v - Math.floor(v);
    }

    /** Returns {@code v} if it is a finite number, otherwise {@code fallback}. */
    public static double finiteOr(double v, double fallback) {
        return Double.isFinite(v) ? v : fallback;
    }

    /** Clamps to [min, max] and replaces NaN/infinity by {@code fallback}. */
    public static double sanitize(double v, double min, double max, double fallback) {
        return Double.isFinite(v) ? clamp(v, min, max) : fallback;
    }

    /** Wraps an angle in radians into (-pi, pi]. */
    public static double wrapAngle(double a) {
        a = a % TAU;
        if (a <= -Math.PI) a += TAU;
        if (a > Math.PI) a -= TAU;
        return a;
    }

    /** Uniform Catmull-Rom spline between p1 and p2. */
    public static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t
                + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /**
     * Integer power by binary exponentiation; exact for small exponents and much faster than
     * {@link Math#pow} for the integer Mandelbulb powers.
     */
    public static double ipow(double base, int exp) {
        double result = 1;
        double b = base;
        int e = exp;
        while (e > 0) {
            if ((e & 1) != 0) result *= b;
            e >>= 1;
            if (e > 0) b *= b;
        }
        return result;
    }

    /** Triangle-wave wrap into [0,1]; used for palettes so that repeating them has no seam. */
    public static double mirrorWrap(double t) {
        double f = t - 2 * Math.floor(t * 0.5);
        return f > 1 ? 2 - f : f;
    }
}

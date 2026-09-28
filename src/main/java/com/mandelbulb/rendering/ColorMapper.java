package com.mandelbulb.rendering;

import com.mandelbulb.fractal.OrbitTrap;
import com.mandelbulb.fractal.TrapType;
import com.mandelbulb.math.MathUtils;
import com.mandelbulb.scene.ColorSettings;

/**
 * Procedural surface colouring driven by mathematical properties of the hit point.
 *
 * <p>Every mode reduces the surface point to a scalar (continuous iteration count, orbit-trap
 * distance, height, curvature, ...) which is mapped through a palette. The palette is sampled
 * from a dense linear-space lookup table with linear interpolation and mirrored wrapping, so
 * there are no hard bands or seams. Orbit-trap colour can additionally be blended over any mode.
 */
public final class ColorMapper {

    static final int LUT_SIZE = 1024;
    private static final double ITERATION_FREQUENCY = 0.2;

    private final double[] lut;
    private final ColorMode mode;
    private final boolean trapEnabled;
    private final TrapType trapType;
    private final double trapBlend;
    private final double trapScale;
    private final double paletteScale;
    private final double paletteOffset;
    private final boolean reverse;
    private final double saturation;
    private final double brightness;
    private final double invBound;
    private final double invMaxSteps;

    public ColorMapper(ColorSettings c, int maxIterations, double boundingRadius, int maxSteps) {
        this.lut = c.palette.buildLinearLut(LUT_SIZE);
        this.mode = c.mode;
        this.trapEnabled = c.orbitTrapEnabled;
        this.trapType = c.trapType;
        this.trapBlend = c.trapBlend;
        this.trapScale = c.trapScale;
        this.paletteScale = c.paletteScale;
        this.paletteOffset = c.paletteOffset;
        this.reverse = c.reversePalette;
        this.saturation = c.saturation;
        this.brightness = c.brightness;
        // The visible bulb is ~1.2 in radius; normalise positions against that, not the
        // (generous) bounding sphere, so height/radial gradients use the full palette.
        this.invBound = 1.0 / Math.min(boundingRadius, 1.25);
        this.invMaxSteps = 1.0 / maxSteps;
    }

    /** Palette lookup of value v (after scale/offset/wrap) into ctx.ar/ag/ab (linear RGB). */
    public void palette(double v, RenderContext ctx) {
        double f = MathUtils.mirrorWrap(v * paletteScale + paletteOffset);
        if (reverse) f = 1 - f;
        double pos = f * (LUT_SIZE - 1);
        int i = (int) pos;
        if (i >= LUT_SIZE - 1) i = LUT_SIZE - 2;
        if (i < 0) i = 0;
        double w = pos - i;
        int j = i * 3;
        ctx.ar = lut[j] + (lut[j + 3] - lut[j]) * w;
        ctx.ag = lut[j + 1] + (lut[j + 4] - lut[j + 1]) * w;
        ctx.ab = lut[j + 2] + (lut[j + 5] - lut[j + 2]) * w;
    }

    /**
     * Maps a raw trap distance to a palette coordinate: normalised by the range the trap takes on
     * the surface (so every trap type uses the whole palette), then scaled by the user factor.
     */
    private double trapValue(OrbitTrap trap) {
        return trapType.normalize(trap.value(trapType)) * trapScale;
    }

    /**
     * Computes the albedo (linear RGB) of the surface point into ctx.ar/ag/ab.
     *
     * @param trap      orbit statistics of the hit point
     * @param curvature dimensionless curvature estimate (DE Laplacian times hit distance)
     */
    public void albedo(OrbitTrap trap, double x, double y, double z, double nx, double ny, double nz,
                       double curvature, int steps, RenderContext ctx) {
        boolean blendTrap = trapEnabled;
        switch (mode) {
            case ORBIT_TRAP -> {
                palette(trapValue(trap), ctx);
                blendTrap = false;
            }
            case TRAP_RGB -> {
                // Classic orbit-trap colouring: closeness to the three coordinate planes drives
                // the three channels, modulated by the palette of the origin trap.
                double k = 2.2 * trapScale;
                double cr = MathUtils.saturate(trap.minAbsX * k);
                double cg = MathUtils.saturate(trap.minAbsY * k);
                double cb = MathUtils.saturate(trap.minAbsZ * k);
                palette(TrapType.ORIGIN.normalize(trap.minRadius) * trapScale, ctx);
                ctx.ar *= 0.35 + 0.65 * cr * cr;
                ctx.ag *= 0.35 + 0.65 * cg * cg;
                ctx.ab *= 0.35 + 0.65 * cb * cb;
                blendTrap = false;
            }
            // Surface points escape after a few iterations; ~5 iterations span the palette once.
            case ITERATION -> palette(trap.smoothIteration * ITERATION_FREQUENCY, ctx);
            case COMBINED -> palette(trap.smoothIteration * ITERATION_FREQUENCY * 0.75 + 0.5 * trapValue(trap), ctx);
            case HEIGHT -> palette(z * invBound * 0.5 + 0.5, ctx);
            case RADIAL -> palette(Math.sqrt(x * x + y * y + z * z) * invBound, ctx);
            case CURVATURE -> palette(0.5 + Math.atan(curvature * 0.15) / Math.PI, ctx);
            case STEPS -> palette(Math.sqrt(steps * invMaxSteps), ctx);
            case SOLID -> palette(0.6, ctx);
            case NORMAL -> {
                ctx.ar = srgbApprox(0.5 + 0.5 * nx);
                ctx.ag = srgbApprox(0.5 + 0.5 * ny);
                ctx.ab = srgbApprox(0.5 + 0.5 * nz);
                blendTrap = false;
            }
            case POSITION -> {
                ctx.ar = srgbApprox(MathUtils.saturate(0.5 + 0.5 * x * invBound));
                ctx.ag = srgbApprox(MathUtils.saturate(0.5 + 0.5 * y * invBound));
                ctx.ab = srgbApprox(MathUtils.saturate(0.5 + 0.5 * z * invBound));
                blendTrap = false;
            }
        }
        if (blendTrap && trapBlend > 0) {
            double br = ctx.ar, bg = ctx.ag, bb = ctx.ab;
            palette(trapValue(trap), ctx);
            ctx.ar = br + (ctx.ar - br) * trapBlend;
            ctx.ag = bg + (ctx.ag - bg) * trapBlend;
            ctx.ab = bb + (ctx.ab - bb) * trapBlend;
        }
        if (saturation != 1.0) {
            double l = 0.2126 * ctx.ar + 0.7152 * ctx.ag + 0.0722 * ctx.ab;
            ctx.ar = Math.max(0, l + (ctx.ar - l) * saturation);
            ctx.ag = Math.max(0, l + (ctx.ag - l) * saturation);
            ctx.ab = Math.max(0, l + (ctx.ab - l) * saturation);
        }
        ctx.ar *= brightness;
        ctx.ag *= brightness;
        ctx.ab *= brightness;
    }

    /** Cheap sRGB -&gt; linear (gamma 2) for the debug modes that bypass the palette. */
    private static double srgbApprox(double c) {
        return c * c;
    }
}

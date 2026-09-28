package com.mandelbulb.rendering;

import com.mandelbulb.math.MathUtils;
import com.mandelbulb.util.Settings;

/** How the fractal is rendered: resolution, ray marching, normals, AO, shadows, threading. */
public final class RenderSettings extends Settings {

    // --- Resolution -----------------------------------------------------------------------
    public ResolutionMode resolutionMode = ResolutionMode.VIEWPORT;
    public ResolutionPreset fixedResolution = ResolutionPreset.HD_720;
    /** Render resolution relative to the viewport size (VIEWPORT mode). */
    public double viewportScale = 1.0;
    /** Supersampling factor s: s x s stratified samples per pixel in the final pass. */
    public int supersampling = 1;
    /** Show coarse previews first and refine (otherwise render the final pass directly). */
    public boolean progressive = true;

    // --- Threads --------------------------------------------------------------------------
    public boolean autoThreads = true;
    public int threadCount = Runtime.getRuntime().availableProcessors();

    // --- Ray marching ---------------------------------------------------------------------
    /** Maximum sphere-tracing steps per primary ray. */
    public int maxSteps = 220;
    /** Maximum ray length (world units); also limited by the camera far plane. */
    public double maxDistance = 20.0;
    /** Absolute lower bound for the hit threshold (world units). */
    public double hitEpsilon = 1e-7;
    /**
     * Distance-estimator precision: the hit threshold is (pixel footprint at distance t) / detail.
     * Higher values resolve finer surface detail at the cost of more steps.
     */
    public double detail = 1.0;
    /** Minimum step length, guarantees progress where the DE is (numerically) zero. */
    public double minStep = 1e-8;
    /** Distance-estimator scale ("fudge factor"): step = DE * scale. &lt; 1 compensates DE error. */
    public double stepScale = 0.85;
    /**
     * Adaptive over-relaxation factor (1 = plain sphere tracing). Steps are lengthened by this
     * factor while it is provably safe and automatically fall back when it would overshoot.
     */
    public double relaxation = 1.25;

    // --- Normals --------------------------------------------------------------------------
    public NormalMethod normalMethod = NormalMethod.CENTRAL;
    /** Minimum finite-difference offset; the actual offset also scales with pixel footprint. */
    public double normalEpsilon = 1e-7;

    // --- Ambient occlusion ----------------------------------------------------------------
    public boolean aoEnabled = true;
    public double aoStrength = 1.0;
    /** AO sampling radius as a fraction of the hit distance (keeps AO consistent when zooming). */
    public double aoRadius = 0.06;
    public int aoSamples = 5;

    // --- Shadows --------------------------------------------------------------------------
    public boolean shadowsEnabled = true;
    public double shadowStrength = 0.85;
    /** Penumbra width; 0 gives hard shadows, larger values softer ones. */
    public double shadowSoftness = 0.08;
    public int shadowSteps = 64;

    public RenderSettings copy() {
        return deepCopy(this);
    }

    public int effectiveThreads() {
        return autoThreads ? Runtime.getRuntime().availableProcessors() : threadCount;
    }

    @Override
    public void validate() {
        if (resolutionMode == null) resolutionMode = ResolutionMode.VIEWPORT;
        if (fixedResolution == null) fixedResolution = ResolutionPreset.HD_720;
        if (normalMethod == null) normalMethod = NormalMethod.CENTRAL;
        viewportScale = MathUtils.sanitize(viewportScale, 0.1, 2.0, 1.0);
        supersampling = MathUtils.clamp(supersampling, 1, 4);
        threadCount = MathUtils.clamp(threadCount, 1, 256);
        maxSteps = MathUtils.clamp(maxSteps, 8, 5000);
        maxDistance = MathUtils.sanitize(maxDistance, 0.5, 500, 20);
        hitEpsilon = MathUtils.sanitize(hitEpsilon, 1e-12, 1e-1, 1e-7);
        detail = MathUtils.sanitize(detail, 0.05, 16, 1.0);
        minStep = MathUtils.sanitize(minStep, 1e-14, 1e-2, 1e-8);
        stepScale = MathUtils.sanitize(stepScale, 0.05, 1.0, 0.85);
        relaxation = MathUtils.sanitize(relaxation, 1.0, 1.9, 1.25);
        normalEpsilon = MathUtils.sanitize(normalEpsilon, 1e-12, 1e-1, 1e-7);
        aoStrength = MathUtils.sanitize(aoStrength, 0, 4, 1);
        aoRadius = MathUtils.sanitize(aoRadius, 0.001, 0.5, 0.06);
        aoSamples = MathUtils.clamp(aoSamples, 1, 32);
        shadowStrength = MathUtils.sanitize(shadowStrength, 0, 1, 0.85);
        shadowSoftness = MathUtils.sanitize(shadowSoftness, 0.0, 1.0, 0.08);
        shadowSteps = MathUtils.clamp(shadowSteps, 4, 1000);
    }
}

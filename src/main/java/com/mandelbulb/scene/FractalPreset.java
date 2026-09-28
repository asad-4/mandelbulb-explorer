package com.mandelbulb.scene;

import com.mandelbulb.camera.Camera;
import com.mandelbulb.fractal.FractalParameters;
import com.mandelbulb.fractal.Mandelbulb;
import com.mandelbulb.fractal.TrapType;
import com.mandelbulb.math.Vector3;
import com.mandelbulb.rendering.ColorMode;
import com.mandelbulb.rendering.Palette;

import java.util.List;
import java.util.function.Consumer;

/**
 * Built-in parameter sets. A preset starts from defaults and configures fractal, quality,
 * colouring, lighting and camera; the user's output settings (resolution, supersampling,
 * threads) are preserved.
 */
public record FractalPreset(String name, String description, Consumer<Scene> configurer) {

    public void applyTo(Scene target) {
        Scene s = new Scene();
        s.render.resolutionMode = target.render.resolutionMode;
        s.render.fixedResolution = target.render.fixedResolution;
        s.render.viewportScale = target.render.viewportScale;
        s.render.supersampling = target.render.supersampling;
        s.render.autoThreads = target.render.autoThreads;
        s.render.threadCount = target.render.threadCount;
        s.render.progressive = target.render.progressive;
        configurer.accept(s);
        s.validate();
        target.copyFrom(s);
    }

    @Override
    public String toString() {
        return name;
    }

    public static List<FractalPreset> builtIns() {
        return List.of(
                new FractalPreset("Classic Mandelbulb", "The White-Nylander power-8 bulb from a three-quarter view.", s -> {
                    LightingPreset.STUDIO.apply(s.lighting);
                    s.color.palette = Palette.GOLD;
                    s.color.mode = ColorMode.COMBINED;
                }),
                new FractalPreset("High Power", "n = 16: many more, finer lobes.", s -> {
                    s.fractal.power = 16;
                    s.fractal.maxIterations = 12;
                    s.render.stepScale = 0.7;
                    LightingPreset.CINEMATIC.apply(s.lighting);
                    s.color.palette = Palette.ICE;
                    s.color.mode = ColorMode.ORBIT_TRAP;
                    s.color.trapType = TrapType.ORIGIN;
                }),
                new FractalPreset("Low Power", "n = 3: a large, heart-shaped body with few lobes.", s -> {
                    s.fractal.power = 3;
                    s.fractal.maxIterations = 24;
                    s.camera = Camera.orbitCamera(Vector3.ZERO, Math.toRadians(-60), Math.toRadians(-18), 3.6, 42);
                    LightingPreset.SOFT.apply(s.lighting);
                    s.color.palette = Palette.EMERALD;
                    s.color.mode = ColorMode.ITERATION;
                    s.color.paletteScale = 1.5;
                }),
                new FractalPreset("Smooth Bulb", "Very few iterations smooth the surface into soft blobs.", s -> {
                    s.fractal.maxIterations = 4;
                    LightingPreset.SOFT.apply(s.lighting);
                    s.lighting.specularStrength = 0.8;
                    s.lighting.roughness = 0.25;
                    s.color.palette = Palette.OCEAN;
                    s.color.mode = ColorMode.HEIGHT;
                    s.color.trapBlend = 0.25;
                }),
                new FractalPreset("Spiky Bulb", "n = 11 with extra iterations and axis traps for sharp spikes.", s -> {
                    s.fractal.power = 11;
                    s.fractal.maxIterations = 24;
                    s.render.detail = 1.5;
                    s.render.stepScale = 0.7;
                    LightingPreset.HIGH_CONTRAST.apply(s.lighting);
                    s.lighting.ambientIntensity = 0.12;
                    s.color.palette = Palette.LAVA;
                    s.color.mode = ColorMode.ORBIT_TRAP;
                    s.color.trapType = TrapType.AXES;
                    s.color.trapScale = 2.0;
                }),
                new FractalPreset("Deep Zoom", "Close to the surface: detail repeats at every scale.", s -> {
                    s.fractal.maxIterations = 30;
                    s.render.maxSteps = 400;
                    s.render.detail = 1.5;
                    s.camera = deepZoomCamera(s.fractal);
                    LightingPreset.STUDIO.apply(s.lighting);
                    // Deep inside the crevices the sun is occluded by the bulb itself, so carry
                    // a warm "headlamp" point light just above and left of the camera.
                    Vector3 lamp = s.camera.position().add(s.camera.up().mul(0.01)).add(s.camera.right().mul(-0.01));
                    s.lighting.point.enabled = true;
                    s.lighting.point.posX = lamp.x();
                    s.lighting.point.posY = lamp.y();
                    s.lighting.point.posZ = lamp.z();
                    s.lighting.point.intensity = 2.5;
                    s.lighting.point.color = com.mandelbulb.math.Color3.fromHex("#FFE2C0");
                    s.lighting.skyColor = com.mandelbulb.math.Color3.fromHex("#B0A89A");
                    s.lighting.ambientIntensity = 0.35;
                    s.lighting.glowStrength = 0;
                    s.color.palette = Palette.COPPER;
                    s.color.mode = ColorMode.COMBINED;
                }),
                new FractalPreset("Dense Detail", "High iteration count and precision; orbit-trap RGB colouring.", s -> {
                    s.fractal.maxIterations = 40;
                    s.render.maxSteps = 500;
                    s.render.detail = 2.5;
                    s.render.aoStrength = 1.4;
                    s.render.aoSamples = 6;
                    LightingPreset.STUDIO.apply(s.lighting);
                    s.color.palette = Palette.PLASMA;
                    s.color.mode = ColorMode.TRAP_RGB;
                }),
                new FractalPreset("Symmetric", "Looking down the polar axis reveals the n-fold symmetry.", s -> {
                    s.camera = Camera.orbitCamera(Vector3.ZERO, 0, Math.toRadians(-89.9), 3.6, 36);
                    LightingPreset.STUDIO.apply(s.lighting);
                    s.lighting.key.azimuth = 90;
                    s.lighting.key.elevation = 70;
                    s.color.palette = Palette.PURPLE;
                    s.color.mode = ColorMode.RADIAL;
                    s.color.paletteScale = 1.6;
                }),
                new FractalPreset("Experimental", "Julia Mandelbulb with a fractional power (n = 7.5).", s -> {
                    s.fractal.power = 7.5;
                    s.fractal.juliaMode = true;
                    s.fractal.juliaX = 0.45;
                    s.fractal.juliaY = -0.35;
                    s.fractal.juliaZ = 0.3;
                    s.fractal.maxIterations = 14;
                    s.render.stepScale = 0.7;
                    s.camera = Camera.orbitCamera(Vector3.ZERO, Math.toRadians(-30), Math.toRadians(-20), 3.2, 42);
                    LightingPreset.NEON.apply(s.lighting);
                    s.color.palette = Palette.ELECTRIC_BLUE;
                    s.color.mode = ColorMode.ORBIT_TRAP;
                    s.color.trapType = TrapType.POINT;
                }),
                new FractalPreset("Quad Bulb", "n = 4 seen from the side, lit like a space object.", s -> {
                    s.fractal.power = 4;
                    s.fractal.maxIterations = 18;
                    s.camera = Camera.orbitCamera(Vector3.ZERO, Math.toRadians(-90), Math.toRadians(-5), 3.4, 42);
                    LightingPreset.SPACE.apply(s.lighting);
                    s.color.palette = Palette.FIRE;
                    s.color.mode = ColorMode.COMBINED;
                    s.color.trapType = TrapType.SHELL;
                }));
    }

    public static FractalPreset find(String name) {
        for (FractalPreset p : builtIns()) {
            if (p.name.equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    /**
     * Finds a surface point by sphere tracing from a far-away eye and places the camera just in
     * front of it, looking at it - so the deep-zoom view is correct for any parameter changes
     * made before the preset is applied.
     */
    static Camera deepZoomCamera(FractalParameters params) {
        Mandelbulb mb = new Mandelbulb(params);
        // Aim at a crevice between the equatorial bulbs (away from the polar axis, where the
        // spherical formula produces stretched "whipped cream" striations).
        Vector3 aim = new Vector3(0.45, -0.55, 0.10);
        Vector3 eye = aim.normalize().mul(2.6).add(new Vector3(0, 0, 0.5));
        Vector3 dir = aim.sub(eye).normalize();
        double t = 0;
        Vector3 hit = null;
        for (int i = 0; i < 3000 && t < 10; i++) {
            Vector3 p = eye.addScaled(dir, t);
            double d = mb.distance(p.x(), p.y(), p.z()) * 0.8;
            if (d < 1e-6) {
                hit = p;
                break;
            }
            t += d;
        }
        if (hit == null) {
            return Camera.defaultCamera();
        }
        double back = 0.025;
        Vector3 camPos = hit.addScaled(dir, -back).add(new Vector3(0, 0, back * 0.4));
        return Camera.lookAt(camPos, hit, Vector3.UNIT_Z, 55).withClipping(1e-7, 10);
    }
}

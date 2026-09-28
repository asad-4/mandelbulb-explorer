package com.mandelbulb.scene;

import com.mandelbulb.math.Color3;
import com.mandelbulb.math.MathUtils;
import com.mandelbulb.util.Settings;

/** Lights, material response and environment. */
public final class LightingSettings extends Settings {

    /** Main shadow-casting sun. */
    public Light key = Light.directional(195, 42, Color3.fromHex("#FFF1DC"), 3.4, true);
    /** Secondary directional light that lifts the shadow side. */
    public Light fill = Light.directional(320, 12, Color3.fromHex("#9DB8FF"), 0.5, false);
    /** Optional local light. */
    public Light point = pointDefault();

    // Ambient: hemispherical sky/ground light, modulated by ambient occlusion.
    public double ambientIntensity = 0.45;
    public Color3 skyColor = Color3.fromHex("#9FB6D9");
    public Color3 groundColor = Color3.fromHex("#4A3B32");

    // Material.
    public double specularStrength = 0.6;
    /** Microfacet roughness in [0.02, 1]; low = sharp highlights. */
    public double roughness = 0.35;
    /** Scales the Schlick Fresnel term (0 disables grazing-angle brightening). */
    public double fresnelStrength = 1.0;
    /** Mirror-like reflection of the environment, weighted by Fresnel. */
    public double reflectivity = 0.12;
    public double rimStrength = 0.25;
    public double rimPower = 3.0;
    public Color3 rimColor = Color3.fromHex("#BFD4FF");

    // Environment / camera response.
    public double exposure = 1.0;
    public double fogDensity = 0.0;
    public Color3 backgroundTop = Color3.fromHex("#0B1020");
    public Color3 backgroundHorizon = Color3.fromHex("#2A3450");
    public Color3 backgroundBottom = Color3.fromHex("#07080C");
    public boolean stars = false;
    public double sunGlow = 0.25;
    /** Halo around the silhouette from rays that passed close to the surface. */
    public double glowStrength = 0.2;
    public Color3 glowColor = Color3.fromHex("#6FA8FF");

    public String presetName = "Studio";

    private static Light pointDefault() {
        Light l = Light.point(new com.mandelbulb.math.Vector3(1.5, -1.3, 1.1), Color3.fromHex("#FFB070"), 2.0, true);
        l.enabled = false;
        return l;
    }

    public LightingSettings copy() {
        return deepCopy(this);
    }

    @Override
    public void validate() {
        key.type = LightType.DIRECTIONAL;
        fill.type = LightType.DIRECTIONAL;
        point.type = LightType.POINT;
        key.validate();
        fill.validate();
        point.validate();
        ambientIntensity = MathUtils.sanitize(ambientIntensity, 0, 5, 0.45);
        specularStrength = MathUtils.sanitize(specularStrength, 0, 5, 0.6);
        roughness = MathUtils.sanitize(roughness, 0.02, 1, 0.35);
        fresnelStrength = MathUtils.sanitize(fresnelStrength, 0, 2, 1);
        reflectivity = MathUtils.sanitize(reflectivity, 0, 1, 0.12);
        rimStrength = MathUtils.sanitize(rimStrength, 0, 3, 0.25);
        rimPower = MathUtils.sanitize(rimPower, 0.5, 16, 3);
        exposure = MathUtils.sanitize(exposure, 0.05, 8, 1);
        fogDensity = MathUtils.sanitize(fogDensity, 0, 5, 0);
        sunGlow = MathUtils.sanitize(sunGlow, 0, 3, 0.25);
        glowStrength = MathUtils.sanitize(glowStrength, 0, 3, 0.35);
        skyColor = nonNull(skyColor, Color3.WHITE);
        groundColor = nonNull(groundColor, Color3.BLACK);
        rimColor = nonNull(rimColor, Color3.WHITE);
        backgroundTop = nonNull(backgroundTop, Color3.BLACK);
        backgroundHorizon = nonNull(backgroundHorizon, Color3.BLACK);
        backgroundBottom = nonNull(backgroundBottom, Color3.BLACK);
        glowColor = nonNull(glowColor, Color3.WHITE);
        if (presetName == null) presetName = "Custom";
    }

    private static Color3 nonNull(Color3 c, Color3 fallback) {
        return c == null ? fallback : c.sanitized();
    }
}

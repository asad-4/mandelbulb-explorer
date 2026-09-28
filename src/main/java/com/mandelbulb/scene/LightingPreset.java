package com.mandelbulb.scene;

import com.mandelbulb.math.Color3;

/** Named lighting set-ups. Each one rewrites the lights, material and environment. */
public enum LightingPreset {
    STUDIO("Studio") {
        @Override
        void configure(LightingSettings l) {
            key(l, 195, 42, "#FFF1DC", 3.4);
            fill(l, 320, 12, "#9DB8FF", 0.5);
            env(l, "#0B1020", "#2A3450", "#07080C", "#9FB6D9", "#4A3B32", 0.45);
            material(l, 0.6, 0.35, 0.12, 0.25, "#BFD4FF");
            l.glowStrength = 0.2;
            l.glowColor = c("#6FA8FF");
            l.sunGlow = 0.25;
            l.fogDensity = 0;
            l.exposure = 1.0;
        }
    },
    CINEMATIC("Cinematic") {
        @Override
        void configure(LightingSettings l) {
            key(l, 140, 22, "#FFC98A", 4.4);
            fill(l, 320, 8, "#3E7BFF", 0.9);
            env(l, "#05070F", "#1B2338", "#020203", "#3A5A8A", "#2B1A10", 0.25);
            material(l, 0.9, 0.25, 0.18, 0.55, "#5E8BFF");
            l.glowStrength = 0.5;
            l.glowColor = c("#FF9A4A");
            l.sunGlow = 0.6;
            l.fogDensity = 0.04;
            l.exposure = 1.1;
        }
    },
    SOFT("Soft") {
        @Override
        void configure(LightingSettings l) {
            key(l, 215, 60, "#FFFFFF", 2.2);
            fill(l, 330, 25, "#FFFFFF", 0.8);
            env(l, "#D8DEE8", "#EEF1F5", "#B8BEC8", "#E8EEF8", "#8A8680", 1.0);
            material(l, 0.25, 0.6, 0.05, 0.1, "#FFFFFF");
            l.glowStrength = 0.0;
            l.sunGlow = 0.0;
            l.fogDensity = 0;
            l.exposure = 1.0;
        }
    },
    HIGH_CONTRAST("High Contrast") {
        @Override
        void configure(LightingSettings l) {
            key(l, 165, 35, "#FFFFFF", 5.0);
            fill(l, 330, 0, "#FFFFFF", 0.0);
            env(l, "#000000", "#000000", "#000000", "#FFFFFF", "#000000", 0.06);
            material(l, 1.2, 0.2, 0.05, 0.0, "#FFFFFF");
            l.glowStrength = 0.0;
            l.sunGlow = 0.0;
            l.fogDensity = 0;
            l.exposure = 1.0;
        }
    },
    SPACE("Space") {
        @Override
        void configure(LightingSettings l) {
            key(l, 150, 15, "#FFF6E8", 4.5);
            fill(l, 330, -20, "#4060FF", 0.35);
            env(l, "#000000", "#03040A", "#000000", "#202840", "#000000", 0.12);
            material(l, 0.8, 0.3, 0.08, 0.6, "#6F8CFF");
            l.stars = true;
            l.glowStrength = 0.8;
            l.glowColor = c("#4F7BFF");
            l.sunGlow = 0.9;
            l.fogDensity = 0;
            l.exposure = 1.0;
        }
    },
    NEON("Neon") {
        @Override
        void configure(LightingSettings l) {
            key(l, 150, 30, "#FF2BD6", 3.2);
            fill(l, 330, 20, "#00E5FF", 2.4);
            env(l, "#07000F", "#1A0030", "#000000", "#7A00FF", "#00303A", 0.3);
            material(l, 1.4, 0.18, 0.25, 1.2, "#00FFE0");
            l.glowStrength = 1.2;
            l.glowColor = c("#B000FF");
            l.sunGlow = 0.0;
            l.fogDensity = 0.0;
            l.exposure = 1.0;
        }
    },
    SUNSET("Sunset") {
        @Override
        void configure(LightingSettings l) {
            key(l, 120, 10, "#FF8A3D", 4.2);
            fill(l, 290, 40, "#6A7DFF", 0.5);
            env(l, "#1B1F4B", "#F2875A", "#2A1420", "#FFB27A", "#3A2030", 0.4);
            material(l, 0.7, 0.3, 0.2, 0.4, "#FFC090");
            l.glowStrength = 0.4;
            l.glowColor = c("#FF7040");
            l.sunGlow = 1.2;
            l.fogDensity = 0.03;
            l.exposure = 1.05;
        }
    };

    private final String label;

    LightingPreset(String label) {
        this.label = label;
    }

    abstract void configure(LightingSettings l);

    public void apply(LightingSettings l) {
        boolean pointEnabled = l.point.enabled;
        l.stars = false;
        configure(l);
        l.point.enabled = pointEnabled;
        l.presetName = label;
        l.validate();
    }

    @Override
    public String toString() {
        return label;
    }

    private static Color3 c(String hex) {
        return Color3.fromHex(hex);
    }

    private static void key(LightingSettings l, double az, double el, String color, double intensity) {
        l.key.azimuth = az;
        l.key.elevation = el;
        l.key.color = c(color);
        l.key.intensity = intensity;
        l.key.enabled = true;
        l.key.castShadows = true;
    }

    private static void fill(LightingSettings l, double az, double el, String color, double intensity) {
        l.fill.azimuth = az;
        l.fill.elevation = el;
        l.fill.color = c(color);
        l.fill.intensity = intensity;
        l.fill.enabled = intensity > 0;
    }

    private static void env(LightingSettings l, String top, String horizon, String bottom, String sky, String ground, double ambient) {
        l.backgroundTop = c(top);
        l.backgroundHorizon = c(horizon);
        l.backgroundBottom = c(bottom);
        l.skyColor = c(sky);
        l.groundColor = c(ground);
        l.ambientIntensity = ambient;
    }

    private static void material(LightingSettings l, double spec, double rough, double refl, double rim, String rimColor) {
        l.specularStrength = spec;
        l.roughness = rough;
        l.reflectivity = refl;
        l.rimStrength = rim;
        l.rimColor = c(rimColor);
    }
}

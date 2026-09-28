package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.scene.Light;
import com.mandelbulb.scene.LightingPreset;
import com.mandelbulb.scene.LightingSettings;
import javafx.scene.control.ComboBox;

import static com.mandelbulb.ui.BoundControls.row;
import static com.mandelbulb.ui.BoundControls.section;

/** Lights, material and environment. */
final class LightingPanel extends SettingsPanel {

    LightingPanel(ApplicationState state, Runnable onChange) {
        super(state, onChange);
        LightingSettings l = state.scene().lighting;

        ComboBox<LightingPreset> presets = new ComboBox<>();
        presets.getItems().setAll(LightingPreset.values());
        presets.setPromptText("Apply lighting preset…");
        presets.setMaxWidth(Double.MAX_VALUE);
        presets.valueProperty().addListener((o, a, p) -> {
            if (p != null) {
                p.apply(state.scene().lighting);
                onChange.run();
            }
        });
        content.getChildren().add(section("Preset", presets));

        content.getChildren().add(section("Key light (directional, sun)",
                controls.check("Enabled", null, () -> l.key.enabled, v -> l.key.enabled = v),
                controls.check("Casts shadows", null, () -> l.key.castShadows, v -> l.key.castShadows = v),
                controls.slider("Azimuth (°)", -180, 360, 1, "Direction around the vertical axis. Tip: Shift+drag in the viewport.",
                        () -> l.key.azimuth, v -> l.key.azimuth = v),
                controls.slider("Elevation (°)", -89, 89, 1, "Height above the horizon", () -> l.key.elevation, v -> l.key.elevation = v),
                controls.slider("Intensity", 0, 12, 2, "Radiance multiplier", () -> l.key.intensity, v -> l.key.intensity = v),
                row("Colour", controls.color(() -> l.key.color, v -> l.key.color = v))));

        content.getChildren().add(section("Fill light (directional)",
                controls.check("Enabled", null, () -> l.fill.enabled, v -> l.fill.enabled = v),
                controls.slider("Azimuth (°)", -180, 360, 1, null, () -> l.fill.azimuth, v -> l.fill.azimuth = v),
                controls.slider("Elevation (°)", -89, 89, 1, null, () -> l.fill.elevation, v -> l.fill.elevation = v),
                controls.slider("Intensity", 0, 12, 2, null, () -> l.fill.intensity, v -> l.fill.intensity = v),
                row("Colour", controls.color(() -> l.fill.color, v -> l.fill.color = v))));

        content.getChildren().add(section("Point light",
                controls.check("Enabled", null, () -> l.point.enabled, v -> l.point.enabled = v),
                controls.check("Casts shadows", null, () -> l.point.castShadows, v -> l.point.castShadows = v),
                positionSlider("Position x", () -> l.point.posX, v -> l.point.posX = v),
                positionSlider("Position y", () -> l.point.posY, v -> l.point.posY = v),
                positionSlider("Position z", () -> l.point.posZ, v -> l.point.posZ = v),
                controls.slider("Intensity", 0, 30, 2, "Inverse-square falloff: 1 / (1 + d²)", () -> l.point.intensity, v -> l.point.intensity = v),
                row("Colour", controls.color(() -> l.point.color, v -> l.point.color = v))));

        content.getChildren().add(section("Ambient (sky / ground hemisphere)",
                controls.slider("Intensity", 0, 3, 2, "Ambient light, modulated by ambient occlusion", () -> l.ambientIntensity, v -> l.ambientIntensity = v),
                row("Sky colour", controls.color(() -> l.skyColor, v -> l.skyColor = v)),
                row("Ground colour", controls.color(() -> l.groundColor, v -> l.groundColor = v))));

        content.getChildren().add(section("Material",
                controls.slider("Specular", 0, 5, 2, "Cook-Torrance GGX specular strength", () -> l.specularStrength, v -> l.specularStrength = v),
                controls.slider("Roughness", 0.02, 1, 2, "Microfacet roughness", () -> l.roughness, v -> l.roughness = v),
                controls.slider("Fresnel", 0, 2, 2, "Schlick Fresnel strength (grazing-angle reflectance)", () -> l.fresnelStrength, v -> l.fresnelStrength = v),
                controls.slider("Reflectivity", 0, 1, 2, "Environment reflection", () -> l.reflectivity, v -> l.reflectivity = v),
                controls.slider("Rim light", 0, 3, 2, "Silhouette highlight", () -> l.rimStrength, v -> l.rimStrength = v),
                controls.slider("Rim power", 0.5, 16, 1, "Rim falloff exponent", () -> l.rimPower, v -> l.rimPower = v),
                row("Rim colour", controls.color(() -> l.rimColor, v -> l.rimColor = v))));

        content.getChildren().add(section("Environment",
                controls.slider("Exposure", 0.05, 8, 2, "Brightness before ACES tone mapping", () -> l.exposure, v -> l.exposure = v),
                controls.slider("Fog density", 0, 2, 3, "Exponential depth fog towards the horizon colour", () -> l.fogDensity, v -> l.fogDensity = v),
                controls.slider("Sun glow", 0, 3, 2, "Glow around the key light in the sky", () -> l.sunGlow, v -> l.sunGlow = v),
                controls.slider("Edge glow", 0, 3, 2, "Halo from rays passing close to the surface", () -> l.glowStrength, v -> l.glowStrength = v),
                row("Glow colour", controls.color(() -> l.glowColor, v -> l.glowColor = v)),
                row("Sky top", controls.color(() -> l.backgroundTop, v -> l.backgroundTop = v)),
                row("Horizon", controls.color(() -> l.backgroundHorizon, v -> l.backgroundHorizon = v)),
                row("Sky bottom", controls.color(() -> l.backgroundBottom, v -> l.backgroundBottom = v)),
                controls.check("Stars", "Procedural star field", () -> l.stars, v -> l.stars = v)));
    }

    private ParameterSlider positionSlider(String name, java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
        return controls.slider(name, -5, 5, 3, "Point light position (world units)", get, set);
    }

    static void moveKeyLight(Light key, double dAzimuth, double dElevation) {
        double az = key.azimuth + dAzimuth;
        az = ((az % 360) + 360) % 360;
        key.azimuth = az;
        key.elevation = Math.max(-89, Math.min(89, key.elevation + dElevation));
    }
}

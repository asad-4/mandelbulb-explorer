package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.fractal.FractalParameters;
import com.mandelbulb.rendering.NormalMethod;
import com.mandelbulb.scene.FractalPreset;
import com.mandelbulb.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.io.IOException;

import static com.mandelbulb.ui.BoundControls.hint;
import static com.mandelbulb.ui.BoundControls.row;
import static com.mandelbulb.ui.BoundControls.section;

/** Fractal and ray-marching parameters, presets. */
final class ParameterPanel extends SettingsPanel {

    private final ComboBox<String> customPresets = new ComboBox<>();

    ParameterPanel(ApplicationState state, Runnable onChange) {
        super(state, onChange);
        Scene s = state.scene();

        // --- Presets ------------------------------------------------------------------------
        ComboBox<FractalPreset> presetBox = new ComboBox<>();
        presetBox.getItems().setAll(FractalPreset.builtIns());
        presetBox.setMaxWidth(Double.MAX_VALUE);
        presetBox.setPromptText("Choose a preset…");
        Label description = hint("");
        presetBox.valueProperty().addListener((o, a, p) -> {
            if (p != null) {
                description.setText(p.description());
                state.applyPreset(p);
            }
        });
        HBox.setHgrow(presetBox, Priority.ALWAYS);

        TextField name = new TextField();
        name.setPromptText("Custom preset name");
        HBox.setHgrow(name, Priority.ALWAYS);
        Button save = new Button("Save");
        save.setOnAction(e -> {
            try {
                state.presets().save(name.getText(), state.sceneWithCamera());
                reloadCustom();
                customPresets.setValue(name.getText().trim());
            } catch (IOException | IllegalArgumentException ex) {
                Dialogs.error(getScene(), "Could not save preset", ex.getMessage());
            }
        });
        customPresets.setMaxWidth(Double.MAX_VALUE);
        customPresets.setPromptText("Saved presets");
        HBox.setHgrow(customPresets, Priority.ALWAYS);
        Button load = new Button("Load");
        load.setOnAction(e -> {
            String n = customPresets.getValue();
            if (n == null) return;
            try {
                state.applyScene(state.presets().load(n));
            } catch (IOException | RuntimeException ex) {
                Dialogs.error(getScene(), "Could not load preset", ex.getMessage());
            }
        });
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            String n = customPresets.getValue();
            if (n == null) return;
            try {
                state.presets().delete(n);
                reloadCustom();
            } catch (IOException ex) {
                Dialogs.error(getScene(), "Could not delete preset", ex.getMessage());
            }
        });
        reloadCustom();

        content.getChildren().add(section("Presets",
                presetBox, description,
                new HBox(6, name, save),
                new HBox(6, customPresets, load, delete),
                hint("Custom presets are stored in " + state.presets().directory())));

        // --- Mandelbulb ------------------------------------------------------------------------
        content.getChildren().add(section("Mandelbulb  z → zⁿ + c",
                controls.slider("Power n", FractalParameters.MIN_POWER, FractalParameters.MAX_POWER, 2,
                        "Exponent of the spherical power map. Integer powers give n-fold symmetry; fractional powers are allowed.",
                        () -> s.fractal.power, v -> s.fractal.power = v),
                controls.intSlider("Iterations", FractalParameters.MIN_ITERATIONS, FractalParameters.MAX_ITERATIONS, true,
                        "Maximum iterations. 10–20 suffice for normal views; deep zooms need more.",
                        () -> s.fractal.maxIterations, v -> s.fractal.maxIterations = v),
                controls.logSlider("Bailout R", FractalParameters.MIN_BAILOUT, FractalParameters.MAX_BAILOUT, 2,
                        "Escape radius: iteration stops once |z| > R.",
                        () -> s.fractal.bailout, v -> s.fractal.bailout = v),
                controls.check("Julia mode (fixed c)", "Iterate z → zⁿ + k with constant k; the sample point is z₀.",
                        () -> s.fractal.juliaMode, v -> s.fractal.juliaMode = v),
                controls.slider("Julia kₓ", -1.5, 1.5, 3, "Julia constant, x component", () -> s.fractal.juliaX, v -> s.fractal.juliaX = v),
                controls.slider("Julia kᵧ", -1.5, 1.5, 3, "Julia constant, y component", () -> s.fractal.juliaY, v -> s.fractal.juliaY = v),
                controls.slider("Julia k₂", -1.5, 1.5, 3, "Julia constant, z component", () -> s.fractal.juliaZ, v -> s.fractal.juliaZ = v),
                controls.slider("Rotate Z (°)", -180, 180, 1, "Object rotation about the vertical (polar) axis",
                        () -> s.fractal.rotationZ, v -> s.fractal.rotationZ = v),
                controls.slider("Rotate X (°)", -180, 180, 1, "Object tilt about the X axis",
                        () -> s.fractal.rotationX, v -> s.fractal.rotationX = v)));

        // --- Ray marching ----------------------------------------------------------------------
        content.getChildren().add(section("Ray marching",
                controls.intSlider("Ray steps", 16, 2000, true, "Maximum sphere-tracing steps per primary ray.",
                        () -> s.render.maxSteps, v -> s.render.maxSteps = v),
                controls.logSlider("Max distance", 1, 200, 1, "Rays are abandoned beyond this distance (also limited by the far plane).",
                        () -> s.render.maxDistance, v -> s.render.maxDistance = v),
                controls.logSlider("Hit epsilon", 1e-10, 1e-2, 3, "Absolute minimum surface threshold (world units). The effective threshold also follows the pixel footprint.",
                        () -> s.render.hitEpsilon, v -> s.render.hitEpsilon = v),
                controls.logSlider("DE precision", 0.1, 8, 2, "Detail: the hit threshold is one pixel footprint divided by this. Higher = finer detail, more steps.",
                        () -> s.render.detail, v -> s.render.detail = v),
                controls.slider("DE scale", 0.1, 1.0, 2, "Step = DE × scale. Lower values prevent overshooting where the estimate is too optimistic (high powers, Julia).",
                        () -> s.render.stepScale, v -> s.render.stepScale = v),
                controls.slider("Relaxation", 1.0, 1.9, 2, "Adaptive over-relaxation: steps are lengthened by this factor and fall back automatically when unsafe. 1 = plain sphere tracing.",
                        () -> s.render.relaxation, v -> s.render.relaxation = v),
                controls.logSlider("Min step", 1e-12, 1e-3, 3, "Minimum ray step, guarantees progress where the DE underflows.",
                        () -> s.render.minStep, v -> s.render.minStep = v),
                row("Normals", controls.combo(() -> s.render.normalMethod, v -> s.render.normalMethod = v, NormalMethod.values())),
                controls.logSlider("Normal ε", 1e-10, 1e-2, 3, "Minimum finite-difference offset for the DE gradient (it also scales with pixel size).",
                        () -> s.render.normalEpsilon, v -> s.render.normalEpsilon = v)));

        // --- Quick access to the most used shading parameters ----------------------------------
        content.getChildren().add(section("Shading & view (quick access)",
                controls.slider("AO strength", 0, 4, 2, "Ambient occlusion strength", () -> s.render.aoStrength, v -> s.render.aoStrength = v),
                controls.intSlider("AO samples", 1, 32, false, "Distance-field samples along the normal", () -> s.render.aoSamples, v -> s.render.aoSamples = v),
                controls.slider("Shadow strength", 0, 1, 2, "Darkness of shadows", () -> s.render.shadowStrength, v -> s.render.shadowStrength = v),
                controls.slider("Light intensity", 0, 12, 2, "Key light intensity", () -> s.lighting.key.intensity, v -> s.lighting.key.intensity = v),
                controls.slider("Specular", 0, 5, 2, "Specular highlight strength", () -> s.lighting.specularStrength, v -> s.lighting.specularStrength = v),
                controls.slider("Roughness", 0.02, 1, 2, "Microfacet roughness (highlight size)", () -> s.lighting.roughness, v -> s.lighting.roughness = v),
                controls.slider("FOV (°)", 5, 140, 1, "Vertical field of view",
                        () -> state.camera().goal().fovDegrees(), v -> state.camera().setFov(v))));
    }

    private void reloadCustom() {
        String selected = customPresets.getValue();
        customPresets.getItems().setAll(state.presets().list());
        if (selected != null && customPresets.getItems().contains(selected)) customPresets.setValue(selected);
    }
}

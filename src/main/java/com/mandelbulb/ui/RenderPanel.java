package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.rendering.ResolutionMode;
import com.mandelbulb.rendering.ResolutionPreset;
import com.mandelbulb.scene.Scene;
import javafx.scene.control.Label;

import java.util.List;
import java.util.Locale;

import static com.mandelbulb.ui.BoundControls.hint;
import static com.mandelbulb.ui.BoundControls.row;
import static com.mandelbulb.ui.BoundControls.section;

/** Resolution, sampling, threads, ambient occlusion and shadows. */
final class RenderPanel extends SettingsPanel {

    private final Label resolutionInfo = hint("");
    private final Label threadInfo = hint("");

    RenderPanel(ApplicationState state, Runnable onChange) {
        super(state, onChange);
        Scene s = state.scene();
        int cores = Runtime.getRuntime().availableProcessors();

        content.getChildren().add(section("Resolution",
                row("Mode", controls.combo(() -> s.render.resolutionMode, v -> s.render.resolutionMode = v, ResolutionMode.values())),
                row("Fixed size", controls.combo(() -> s.render.fixedResolution, v -> s.render.fixedResolution = v, ResolutionPreset.values())),
                controls.slider("Viewport scale", 0.1, 2.0, 2, "Render resolution relative to the viewport (Match viewport mode). Use < 1 for speed, 2 for HiDPI sharpness.",
                        () -> s.render.viewportScale, v -> s.render.viewportScale = v),
                row("Supersampling", controls.combo(List.of(1, 2, 3, 4), () -> s.render.supersampling, v -> s.render.supersampling = v)),
                hint("Supersampling s renders s×s stratified samples per pixel in the final pass (1×, 4×, 9× or 16× the cost)."),
                controls.check("Progressive refinement", "Show coarse previews first, then refine to full quality",
                        () -> s.render.progressive, v -> s.render.progressive = v),
                resolutionInfo));

        content.getChildren().add(section("CPU threads",
                controls.check("Automatic (" + cores + " cores)", "Use one worker per available processor",
                        () -> s.render.autoThreads, v -> s.render.autoThreads = v),
                controls.intSlider("Threads", 1, Math.max(64, cores * 2), false, "Manual worker thread count",
                        () -> s.render.threadCount, v -> s.render.threadCount = v),
                threadInfo));

        content.getChildren().add(section("Ambient occlusion",
                controls.check("Enabled", "Distance-field ambient occlusion", () -> s.render.aoEnabled, v -> s.render.aoEnabled = v),
                controls.slider("Strength", 0, 4, 2, "How dark occluded crevices become", () -> s.render.aoStrength, v -> s.render.aoStrength = v),
                controls.logSlider("Radius", 0.001, 0.5, 3, "Sampling radius as a fraction of the hit distance", () -> s.render.aoRadius, v -> s.render.aoRadius = v),
                controls.intSlider("Samples", 1, 32, false, "Samples along the normal (each costs one DE evaluation)", () -> s.render.aoSamples, v -> s.render.aoSamples = v)));

        content.getChildren().add(section("Shadows",
                controls.check("Enabled", "Secondary shadow rays towards shadow-casting lights", () -> s.render.shadowsEnabled, v -> s.render.shadowsEnabled = v),
                controls.slider("Strength", 0, 1, 2, "0 = no darkening, 1 = black shadows", () -> s.render.shadowStrength, v -> s.render.shadowStrength = v),
                controls.slider("Softness", 0, 1, 3, "Penumbra width (0 = hard shadows)", () -> s.render.shadowSoftness, v -> s.render.shadowSoftness = v),
                controls.intSlider("Steps", 4, 1000, true, "Maximum march steps per shadow ray", () -> s.render.shadowSteps, v -> s.render.shadowSteps = v)));
    }

    @Override
    public void pulse() {
        int[] size = state.renderSize();
        int ss = state.scene().render.supersampling;
        resolutionInfo.setText(String.format(Locale.ROOT, "Viewport %d × %d  →  render %d × %d  (%d samples/pixel, %,d rays)",
                state.viewportWidth(), state.viewportHeight(), size[0], size[1], ss * ss, (long) size[0] * size[1] * ss * ss));
        threadInfo.setText(String.format(Locale.ROOT, "CPU threads: %d    Active workers: %d",
                state.engine().threadCount(), state.engine().activeWorkers()));
    }
}

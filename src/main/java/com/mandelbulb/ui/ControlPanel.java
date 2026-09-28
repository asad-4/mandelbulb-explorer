package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.List;

/** Tabbed parameter area to the right of the viewport. */
final class ControlPanel extends TabPane {

    private final List<SettingsPanel> panels;
    private final MathematicsPanel mathematics;
    private final Tab mathTab;
    private final Tab benchmarkTab;

    ControlPanel(ApplicationState state, Runnable onChange) {
        getStyleClass().add("control-panel");
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        setPrefWidth(430);
        setMinWidth(340);

        ParameterPanel parameters = new ParameterPanel(state, onChange);
        RenderPanel render = new RenderPanel(state, onChange);
        CameraPanel camera = new CameraPanel(state, onChange);
        LightingPanel lighting = new LightingPanel(state, onChange);
        ColoringPanel coloring = new ColoringPanel(state, onChange);
        AnimationPanel animation = new AnimationPanel(state, onChange);
        BenchmarkPanel benchmark = new BenchmarkPanel(state, onChange);
        mathematics = new MathematicsPanel(state, onChange);
        panels = List.of(parameters, render, camera, lighting, coloring, animation, benchmark, mathematics);

        mathTab = new Tab("Math", mathematics);
        benchmarkTab = new Tab("Bench", benchmark);
        getTabs().addAll(
                new Tab("Fractal", parameters),
                new Tab("Render", render),
                new Tab("Camera", camera),
                new Tab("Light", lighting),
                new Tab("Color", coloring),
                new Tab("Anim", animation),
                benchmarkTab,
                mathTab);
    }

    void refreshAll() {
        for (SettingsPanel p : panels) p.refresh();
    }

    /** Periodic update of live read-outs in the visible tab. */
    void pulse() {
        Tab selected = getSelectionModel().getSelectedItem();
        if (selected != null && selected.getContent() instanceof SettingsPanel p) {
            p.pulse();
        }
    }

    void showMathematics() {
        getSelectionModel().select(mathTab);
    }

    void selectTab(String title) {
        getTabs().stream().filter(t -> t.getText().equalsIgnoreCase(title)).findFirst()
                .ifPresent(t -> getSelectionModel().select(t));
    }

    void showBenchmark() {
        getSelectionModel().select(benchmarkTab);
    }
}

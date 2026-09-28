package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.benchmark.Benchmark;
import com.mandelbulb.rendering.ResolutionPreset;
import com.mandelbulb.scene.Scene;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static com.mandelbulb.ui.BoundControls.hint;
import static com.mandelbulb.ui.BoundControls.row;
import static com.mandelbulb.ui.BoundControls.section;

/** Runs benchmarks and compares configurations side by side. */
final class BenchmarkPanel extends SettingsPanel {

    private final TableView<Benchmark.Result> table = new TableView<>();
    private final TextArea details = new TextArea();
    private final ComboBox<ResolutionPreset> size = new ComboBox<>();
    private final ComboBox<Integer> ss = new ComboBox<>();
    private final CheckBox warmUp = new CheckBox("JIT warm-up render first");

    BenchmarkPanel(ApplicationState state, Runnable onChange) {
        super(state, onChange);
        size.getItems().setAll(ResolutionPreset.values());
        size.setValue(ResolutionPreset.VGA);
        size.setMaxWidth(Double.MAX_VALUE);
        ss.getItems().setAll(1, 2, 3, 4);
        ss.setValue(1);
        warmUp.setSelected(true);

        Button runCurrent = new Button("Run (current settings)");
        runCurrent.setOnAction(e -> run(List.of(new Benchmark.Config("Current settings",
                size.getValue().width, size.getValue().height, ss.getValue(), s -> { }))));
        Button runSuite = new Button("Run standard suite");
        runSuite.setOnAction(e -> run(Benchmark.standardSuite()));
        Button copy = new Button("Copy results");
        copy.setOnAction(e -> {
            StringBuilder sb = new StringBuilder();
            for (Benchmark.Result r : table.getItems()) sb.append(r.format()).append('\n');
            ClipboardContent c = new ClipboardContent();
            c.putString(sb.toString());
            Clipboard.getSystemClipboard().setContent(c);
        });
        Button clear = new Button("Clear");
        clear.setOnAction(e -> {
            table.getItems().clear();
            details.clear();
        });

        table.getColumns().add(column("Config", 130, r -> r.name()));
        table.getColumns().add(column("Time (s)", 64, r -> String.format(Locale.ROOT, "%.2f", r.stats().seconds())));
        table.getColumns().add(column("Size", 80, r -> r.stats().width() + "×" + r.stats().height()));
        table.getColumns().add(column("SS", 32, r -> r.stats().supersampling() + "×"));
        table.getColumns().add(column("Pixels", 80, r -> String.format(Locale.ROOT, "%,d", r.stats().pixels())));
        table.getColumns().add(column("DE evals", 100, r -> String.format(Locale.ROOT, "%,d", r.stats().deEvaluations())));
        table.getColumns().add(column("Avg steps", 64, r -> String.format(Locale.ROOT, "%.1f", r.stats().averageSteps())));
        table.getColumns().add(column("Max steps", 64, r -> Long.toString(r.stats().maxMarchSteps())));
        table.getColumns().add(column("MRays/s", 64, r -> String.format(Locale.ROOT, "%.2f", r.stats().raysPerSecond() / 1e6)));
        table.getColumns().add(column("CPU", 48, r -> r.cpuUtilization() < 0 ? "n/a" : String.format(Locale.ROOT, "%.0f%%", r.cpuUtilization() * 100)));
        table.setPrefHeight(200);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, r) -> {
            if (r != null) details.setText(r.format());
        });
        details.setEditable(false);
        details.setPrefRowCount(12);
        details.getStyleClass().add("mono");

        content.getChildren().add(section("Benchmark",
                row("Resolution", size), row("Supersampling", ss), warmUp,
                new FlowPane(6, 6, runCurrent, runSuite, copy, clear),
                hint("Benchmarks render without progressive passes using the current scene (camera, fractal and "
                        + "quality settings). The standard suite compares fast / balanced / quality / Full HD configurations."),
                table, details));
    }

    private static TableColumn<Benchmark.Result, String> column(String title, double width, Function<Benchmark.Result, String> f) {
        TableColumn<Benchmark.Result, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(f.apply(cd.getValue())));
        c.setSortable(false);
        return c;
    }

    private void run(List<Benchmark.Config> configs) {
        Scene base = state.snapshot();
        boolean wasPaused = state.isPaused();
        state.setPaused(true);
        boolean warm = warmUp.isSelected();
        Dialogs.runWithProgress(getScene().getWindow(), "Benchmark", progress -> {
            List<Benchmark.Result> results = new ArrayList<>();
            for (int i = 0; i < configs.size(); i++) {
                Benchmark.Config cfg = configs.get(i);
                final int index = i;
                progress.update((double) i / configs.size(), "Warming up / rendering: " + cfg.name());
                results.add(Benchmark.run(state.engine(), base, cfg, warm && i == 0,
                        f -> progress.update((index + f) / configs.size(),
                                String.format(Locale.ROOT, "%s  %.0f%%", cfg.name(), f * 100)),
                        progress::isCancelled));
            }
            return results;
        }, results -> {
            table.getItems().addAll(results);
            if (!results.isEmpty()) {
                table.getSelectionModel().select(results.get(results.size() - 1));
            }
        }, () -> state.setPaused(wasPaused));
    }
}

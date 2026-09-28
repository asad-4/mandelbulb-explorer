package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.rendering.RenderProgress;
import com.mandelbulb.rendering.RenderStats;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.Locale;

/** Bottom bar: render progress, thread utilisation and timing. */
final class StatusBar extends HBox {

    private final ApplicationState state;
    private final Label message = new Label("Ready");
    private final ProgressBar bar = new ProgressBar(0);
    private final Label threads = new Label();
    private final Label timing = new Label();

    StatusBar(ApplicationState state) {
        this.state = state;
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(14);
        setPadding(new Insets(4, 10, 4, 10));
        bar.setPrefWidth(180);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(message, spacer, threads, bar, timing);
    }

    void update() {
        RenderProgress p = state.engine().progress();
        RenderStats stats = state.engine().lastStats();
        if (state.isPaused()) {
            message.setText("Paused");
        } else if (p.running()) {
            message.setText(String.format(Locale.ROOT, "Rendering %d × %d — pass %d of %d (%s)", p.width(), p.height(),
                    p.pass(), p.passCount(), p.block() > 1 ? "1/" + p.block() + " resolution preview"
                            : p.supersampling() > 1 ? p.supersampling() + "×" + p.supersampling() + " supersampled" : "full resolution"));
        } else {
            message.setText("Ready");
        }
        bar.setProgress(p.running() ? p.fraction() : (stats != null ? 1 : 0));
        threads.setText(String.format(Locale.ROOT, "CPU threads: %d   Active workers: %d   Progress: %3.0f%%",
                state.engine().threadCount(), state.engine().activeWorkers(), (p.running() ? p.fraction() : stats != null ? 1 : 0) * 100));
        if (p.running()) {
            timing.setText(String.format(Locale.ROOT, "%.2f s", p.elapsedSeconds()));
        } else if (stats != null) {
            timing.setText(String.format(Locale.ROOT, "Render time %.2f s   %,d DE evals   %.1f avg steps",
                    stats.seconds(), stats.deEvaluations(), stats.averageSteps()));
        }
    }
}

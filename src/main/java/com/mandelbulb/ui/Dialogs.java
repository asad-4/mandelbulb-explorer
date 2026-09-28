package com.mandelbulb.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Message boxes and a cancellable progress dialog for long-running background work. */
final class Dialogs {

    private static final Logger LOG = Logger.getLogger(Dialogs.class.getName());

    private Dialogs() {
    }

    static Window window(Scene scene) {
        return scene == null ? null : scene.getWindow();
    }

    static void error(Scene owner, String title, String message) {
        error(window(owner), title, message);
    }

    static void error(Window owner, String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message == null ? "Unknown error" : message, ButtonType.OK);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(title);
        a.showAndWait();
    }

    static void info(Window owner, String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(title);
        a.showAndWait();
    }

    static boolean confirm(Window owner, String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(title);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    /** Work executed on a background thread with access to its progress dialog. */
    interface Work<T> {
        T run(Progress progress) throws Exception;
    }

    /** Thread-safe progress sink handed to background work. */
    static final class Progress {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicLong lastUpdate = new AtomicLong();
        private final ProgressBar bar = new ProgressBar(0);
        private final Label label = new Label("Starting…");

        public boolean isCancelled() {
            return cancelled.get();
        }

        /** May be called from any thread; UI updates are throttled to ~30 Hz. */
        public void update(double fraction, String message) {
            long now = System.nanoTime();
            long last = lastUpdate.get();
            if (fraction < 1 && now - last < 33_000_000L) return;
            if (!lastUpdate.compareAndSet(last, now)) return;
            Platform.runLater(() -> {
                bar.setProgress(fraction);
                if (message != null) label.setText(message);
            });
        }
    }

    /**
     * Runs {@code work} on a background thread while showing a modal progress dialog with a
     * Cancel button. Errors are reported in an alert; cancellation is silent.
     */
    static <T> void runWithProgress(Window owner, String title, Work<T> work, Consumer<T> onSuccess, Runnable onFinally) {
        Progress progress = new Progress();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);
        progress.bar.setPrefWidth(380);
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> {
            progress.cancelled.set(true);
            cancel.setDisable(true);
            progress.label.setText("Cancelling…");
        });
        stage.setOnCloseRequest(e -> progress.cancelled.set(true));
        VBox box = new VBox(10, progress.label, progress.bar, cancel);
        box.setPadding(new Insets(16));
        Scene scene = new Scene(box);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().setAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.show();

        Thread t = new Thread(() -> {
            try {
                T result = work.run(progress);
                Platform.runLater(() -> {
                    stage.close();
                    onSuccess.accept(result);
                    onFinally.run();
                });
            } catch (CancellationException e) {
                Platform.runLater(() -> {
                    stage.close();
                    onFinally.run();
                });
            } catch (Throwable e) {
                LOG.log(Level.SEVERE, title + " failed", e);
                String msg = e instanceof OutOfMemoryError
                        ? "Out of memory. Choose a smaller resolution or supersampling, or start Java with a larger -Xmx."
                        : e.getMessage() != null ? e.getMessage() : e.toString();
                Platform.runLater(() -> {
                    stage.close();
                    onFinally.run();
                    error(owner, title + " failed", msg);
                });
            }
        }, "mandelbulb-" + title.toLowerCase().replace(' ', '-'));
        t.setDaemon(true);
        t.start();
    }
}

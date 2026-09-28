package com.mandelbulb.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import java.io.File;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Developer hook for automated UI checks (e.g. in CI or headless environments without
 * screen-capture permission). Enabled with system properties:
 * <pre>
 *   -Dmandelbulb.snapshot=out.png        write a snapshot of the whole window
 *   -Dmandelbulb.snapshotDelay=8         seconds to wait first (default 8)
 *   -Dmandelbulb.snapshotTab=Math        select a control tab by title
 *   -Dmandelbulb.snapshotPreset=NAME     apply a built-in preset at start-up
 *   -Dmandelbulb.snapshotPlay=true       start animation playback at start-up
 *   -Dmandelbulb.snapshotExit=true       quit afterwards
 * </pre>
 */
final class SnapshotHook {

    private static final Logger LOG = Logger.getLogger(SnapshotHook.class.getName());

    private SnapshotHook() {
    }

    static void install(javafx.scene.Scene scene, Consumer<String> selectTab, Consumer<String> applyPreset, Runnable play) {
        String path = System.getProperty("mandelbulb.snapshot");
        if (path == null) return;
        String tab = System.getProperty("mandelbulb.snapshotTab");
        if (tab != null) selectTab.accept(tab);
        String preset = System.getProperty("mandelbulb.snapshotPreset");
        if (preset != null) applyPreset.accept(preset);
        if (Boolean.getBoolean("mandelbulb.snapshotPlay")) play.run();
        double delay = Double.parseDouble(System.getProperty("mandelbulb.snapshotDelay", "8"));
        PauseTransition wait = new PauseTransition(Duration.seconds(delay));
        wait.setOnFinished(e -> {
            try {
                WritableImage img = scene.snapshot(null);
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                int[] argb = new int[w * h];
                img.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
                com.mandelbulb.export.ImageExporter.write(argb, w, h, new File(path),
                        com.mandelbulb.export.ImageExporter.Format.PNG, 1f);
                LOG.info("UI snapshot written to " + path);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Snapshot failed", ex);
            }
            if (Boolean.getBoolean("mandelbulb.snapshotExit")) Platform.exit();
        });
        wait.play();
    }
}

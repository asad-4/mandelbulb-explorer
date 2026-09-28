/**
 * Mandelbulb Explorer module.
 *
 * <p>Only the {@code app} and {@code ui} packages depend on JavaFX; the mathematical core,
 * camera, scene description and CPU renderer are toolkit-independent.
 */
module com.mandelbulb {
    requires javafx.controls;
    requires java.desktop;     // ImageIO / BufferedImage for PNG + JPEG export
    requires jdk.management;   // process CPU load for the benchmark
    requires java.logging;

    exports com.mandelbulb;
    exports com.mandelbulb.app;
    exports com.mandelbulb.math;
    exports com.mandelbulb.fractal;
    exports com.mandelbulb.rendering;
    exports com.mandelbulb.camera;
    exports com.mandelbulb.scene;
    exports com.mandelbulb.ui;
    exports com.mandelbulb.export;
    exports com.mandelbulb.animation;
    exports com.mandelbulb.benchmark;
    exports com.mandelbulb.util;
}

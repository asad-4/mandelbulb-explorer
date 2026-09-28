package com.mandelbulb;

import com.mandelbulb.app.HeadlessRunner;
import com.mandelbulb.app.MandelbulbApplication;

/**
 * Entry point.
 *
 * <p>Without arguments the JavaFX explorer starts. With {@code --render} or {@code --benchmark}
 * the renderer runs headless from the command line (see {@link HeadlessRunner}); this is handy for
 * batch rendering and verifies that the renderer does not depend on the UI toolkit.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // Export uses ImageIO/BufferedImage only; keep AWT from starting a second GUI toolkit.
        System.setProperty("java.awt.headless", "true");
        if (HeadlessRunner.handles(args)) {
            System.exit(HeadlessRunner.run(args));
        }
        MandelbulbApplication.launchApp(args);
    }
}

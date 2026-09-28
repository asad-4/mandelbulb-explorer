package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.camera.Camera;
import com.mandelbulb.camera.CameraController;
import com.mandelbulb.math.Vector3;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.util.Locale;

import static com.mandelbulb.ui.BoundControls.hint;
import static com.mandelbulb.ui.BoundControls.section;

/** Camera optics, smoothing, read-outs and saved views. */
final class CameraPanel extends SettingsPanel {

    private final Label readout = new Label();
    private final Button[] goButtons = new Button[9];

    CameraPanel(ApplicationState state, Runnable onChange) {
        super(state, onChange);
        CameraController cam = state.camera();
        readout.getStyleClass().add("readout");

        content.getChildren().add(section("Optics",
                controls.slider("FOV (°)", Camera.MIN_FOV, Camera.MAX_FOV, 1, "Vertical field of view",
                        () -> cam.goal().fovDegrees(), cam::setFov),
                controls.logSlider("Near clip", 1e-9, 1, 3, "Rays start this far from the eye",
                        () -> Math.max(1e-9, cam.goal().near()), v -> cam.setClipping(v, cam.goal().far())),
                controls.logSlider("Far clip", 1, 200, 1, "Rays end this far from the eye",
                        () -> cam.goal().far(), v -> cam.setClipping(cam.goal().near(), v))));

        content.getChildren().add(section("Motion",
                controls.check("Smooth camera", "Interpolate towards the target camera instead of jumping",
                        cam::isSmoothing, cam::setSmoothing),
                controls.slider("Smoothing speed", 1, 60, 1, "Higher = snappier response", cam::smoothingSpeed, cam::setSmoothingSpeed)));

        Button reset = new Button("Reset camera");
        reset.setOnAction(e -> cam.reset());
        Button focus = new Button("Focus screen centre");
        focus.setOnAction(e -> {
            int[] size = state.renderSize();
            Vector3 p = state.pick(size[0] * 0.5, size[1] * 0.5);
            if (p != null) cam.focus(p);
        });
        content.getChildren().add(section("State", readout, new HBox(6, reset, focus)));

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        for (int i = 0; i < 9; i++) {
            int slot = i + 1;
            Button save = new Button("Save " + slot);
            save.setOnAction(e -> {
                cam.saveBookmark(slot);
                state.presets().saveBookmarks(cam);
                pulse();
            });
            Button go = new Button("Go " + slot);
            go.setOnAction(e -> cam.restoreBookmark(slot));
            goButtons[i] = go;
            grid.add(save, (i % 3) * 2, i / 3);
            grid.add(go, (i % 3) * 2 + 1, i / 3);
        }
        content.getChildren().add(section("Saved views", grid,
                hint("Keyboard: Ctrl+1…9 saves, 1…9 restores. Views persist between sessions.")));

        content.getChildren().add(section("Controls",
                hint("""
                        Left drag: orbit    Right drag: pan    Scroll: zoom
                        Middle drag: fly (dolly / strafe)
                        Alt/Ctrl + left drag: free look
                        Shift + left drag: move key light
                        Ctrl + scroll: field of view    Shift + scroll: dolly
                        Double-click: orbit around clicked surface point
                        W A S D: fly    R / F: up / down    Q / E: roll
                        Arrow keys: orbit    + / −: zoom    Shift: faster
                        Home: reset camera    O: overlay    P: pause""")));
    }

    @Override
    public void pulse() {
        Camera c = state.camera().current();
        Vector3 eye = c.position();
        double de = state.worldDistance(eye);
        readout.setText(String.format(Locale.ROOT,
                "Eye       %s%nTarget    %s%nDistance  %.6g%nDE at eye %.6g%s",
                fmt(eye), fmt(c.target()), c.distance(), de, de <= 0 ? "  (inside fractal!)" : ""));
        for (int i = 0; i < 9; i++) {
            goButtons[i].setDisable(state.camera().bookmark(i + 1) == null);
        }
    }

    private static String fmt(Vector3 v) {
        return String.format(Locale.ROOT, "(%.5f, %.5f, %.5f)", v.x(), v.y(), v.z());
    }
}

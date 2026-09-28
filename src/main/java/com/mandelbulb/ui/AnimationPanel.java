package com.mandelbulb.ui;

import com.mandelbulb.animation.AnimationController;
import com.mandelbulb.animation.AnimationSettings;
import com.mandelbulb.animation.CameraPath;
import com.mandelbulb.animation.Easing;
import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.camera.Camera;
import com.mandelbulb.math.Vector3;
import com.mandelbulb.rendering.ResolutionPreset;
import com.mandelbulb.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.List;
import java.util.Locale;

import static com.mandelbulb.ui.BoundControls.hint;
import static com.mandelbulb.ui.BoundControls.row;
import static com.mandelbulb.ui.BoundControls.section;

/** Animation playback, fly-through waypoints and PNG-sequence recording. */
final class AnimationPanel extends SettingsPanel {

    private final Label timeLabel = new Label();
    private final ListView<String> waypointList = new ListView<>();
    private final ComboBox<ResolutionPreset> recordSize = new ComboBox<>();
    private final ComboBox<Integer> recordSs = new ComboBox<>();

    AnimationPanel(ApplicationState state, Runnable onChange) {
        super(state, state::requestRender);
        AnimationController anim = state.animation();
        AnimationSettings a = anim.settings();
        CameraPath path = anim.path();

        Button play = new Button("▶ Play");
        play.setOnAction(e -> {
            anim.play();
            state.requestRender();
        });
        Button pause = new Button("❚❚ Pause");
        pause.setOnAction(e -> anim.pause());
        Button stop = new Button("■ Stop");
        stop.setOnAction(e -> {
            anim.stop();
            state.requestRender();
        });
        timeLabel.getStyleClass().add("readout");
        timeLabel.setWrapText(true);
        content.getChildren().add(section("Transport", new HBox(6, play, pause, stop), timeLabel,
                row("Playback quality", controls.combo(List.of(1, 2, 4, 8), () -> a.playbackBlock, v -> a.playbackBlock = v)),
                hint("Playback renders as fast as the CPU allows: 1 = full resolution, 2 = half, 4 = quarter, 8 = eighth. "
                        + "Stopping returns to the un-animated scene.")));

        content.getChildren().add(section("Animated parameters",
                controls.check("Rotate Mandelbulb", null, () -> a.rotateObject, v -> a.rotateObject = v),
                controls.slider("Rotation °/s", -180, 180, 1, null, () -> a.rotationSpeed, v -> a.rotationSpeed = v),
                controls.check("Orbit camera", null, () -> a.orbitCamera, v -> a.orbitCamera = v),
                controls.slider("Orbit °/s", -180, 180, 1, null, () -> a.orbitSpeed, v -> a.orbitSpeed = v),
                controls.check("Animate power", null, () -> a.animatePower, v -> a.animatePower = v),
                controls.slider("Power min", 2, 20, 2, null, () -> a.powerMin, v -> a.powerMin = v),
                controls.slider("Power max", 2, 20, 2, null, () -> a.powerMax, v -> a.powerMax = v),
                controls.slider("Period (s)", 1, 120, 1, "Duration of one min → max → min cycle", () -> a.powerPeriod, v -> a.powerPeriod = v),
                controls.check("Animate key light", null, () -> a.animateLight, v -> a.animateLight = v),
                controls.slider("Light °/s", -360, 360, 1, null, () -> a.lightSpeed, v -> a.lightSpeed = v),
                controls.check("Animate palette", null, () -> a.animatePalette, v -> a.animatePalette = v),
                controls.slider("Palette speed", -1, 1, 3, "Palette offset change per second", () -> a.paletteSpeed, v -> a.paletteSpeed = v)));

        waypointList.setPrefHeight(130);
        Button add = new Button("Add view");
        add.setOnAction(e -> {
            int i = waypointList.getSelectionModel().getSelectedIndex();
            if (i >= 0) path.insert(i + 1, state.camera().goal());
            else path.add(state.camera().goal());
            refreshWaypoints(i >= 0 ? i + 1 : path.size() - 1);
        });
        Button replace = new Button("Replace");
        replace.setOnAction(e -> {
            int i = waypointList.getSelectionModel().getSelectedIndex();
            if (i >= 0) {
                path.set(i, state.camera().goal());
                refreshWaypoints(i);
            }
        });
        Button remove = new Button("Remove");
        remove.setOnAction(e -> {
            int i = waypointList.getSelectionModel().getSelectedIndex();
            path.remove(i);
            refreshWaypoints(Math.min(i, path.size() - 1));
        });
        Button up = new Button("↑");
        up.setOnAction(e -> {
            int i = waypointList.getSelectionModel().getSelectedIndex();
            path.move(i, -1);
            refreshWaypoints(Math.max(0, i - 1));
        });
        Button down = new Button("↓");
        down.setOnAction(e -> {
            int i = waypointList.getSelectionModel().getSelectedIndex();
            path.move(i, 1);
            refreshWaypoints(Math.min(path.size() - 1, i + 1));
        });
        Button go = new Button("Go to");
        go.setOnAction(e -> {
            int i = waypointList.getSelectionModel().getSelectedIndex();
            if (i >= 0) state.camera().setCamera(path.waypoints().get(i), false);
        });
        Button clear = new Button("Clear");
        clear.setOnAction(e -> {
            path.clear();
            refreshWaypoints(-1);
        });
        FlowPane buttons = new FlowPane(4, 4, add, replace, remove, up, down, go, clear);
        content.getChildren().add(section("Fly-through",
                controls.check("Fly along waypoints", "When playing (or recording), the camera follows the waypoint path",
                        () -> a.flyThrough, v -> a.flyThrough = v),
                waypointList, buttons,
                controls.slider("Flight time (s)", 1, 120, 1, null, () -> a.flyDuration, v -> a.flyDuration = v),
                row("Easing", controls.combo(() -> a.easing, v -> a.easing = v, Easing.values())),
                controls.check("Loop", null, () -> a.loop, v -> a.loop = v),
                hint("Navigate to a view, press 'Add view'; repeat. Positions follow a Catmull-Rom spline, "
                        + "orientations a quaternion spline, so the flight passes smoothly through every waypoint.")));

        recordSize.getItems().setAll(ResolutionPreset.values());
        recordSize.setValue(ResolutionPreset.HD_720);
        recordSize.setMaxWidth(Double.MAX_VALUE);
        recordSs.getItems().setAll(1, 2, 3, 4);
        recordSs.setValue(1);
        Button record = new Button("● Record PNG sequence…");
        record.setOnAction(e -> record());
        content.getChildren().add(section("Record / export",
                controls.intSlider("FPS", 1, 120, false, "Frames per second of the recording", () -> a.fps, v -> a.fps = v),
                controls.slider("Duration (s)", 0.5, 120, 1, "Length of the recording (a fly-through records one flight)",
                        () -> a.duration, v -> a.duration = v),
                row("Resolution", recordSize),
                row("Supersampling", recordSs),
                record,
                hint("Frames are rendered off-line at full quality with exact 1/fps time steps, as frame_00000.png, "
                        + "frame_00001.png, …  Encode with: ffmpeg -framerate 30 -i frame_%05d.png -pix_fmt yuv420p mandelbulb.mp4")));
        refreshWaypoints(-1);
    }

    private void refreshWaypoints(int select) {
        List<Camera> wps = state.animation().path().waypoints();
        waypointList.getItems().clear();
        for (int i = 0; i < wps.size(); i++) {
            Vector3 p = wps.get(i).position();
            waypointList.getItems().add(String.format(Locale.ROOT, "Waypoint %d   eye (%.3f, %.3f, %.3f)", i + 1, p.x(), p.y(), p.z()));
        }
        if (select >= 0 && select < wps.size()) waypointList.getSelectionModel().select(select);
    }

    private void record() {
        AnimationController anim = state.animation();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose an (empty) output folder for the frames");
        File dir = chooser.showDialog(getScene().getWindow());
        if (dir == null) return;
        ResolutionPreset res = recordSize.getValue();
        int ss = recordSs.getValue();
        Scene base = state.scene().copy();
        base.camera = state.camera().current();
        boolean wasPaused = state.isPaused();
        state.setPaused(true);
        Dialogs.runWithProgress(getScene().getWindow(), "Recording", progress -> {
            final int[] current = {0, 0};
            return anim.record(state.engine(), base, dir, res.width, res.height, ss, new AnimationController.RecordListener() {
                @Override
                public void frameStarted(int frame, int frameCount) {
                    current[0] = frame;
                    current[1] = frameCount;
                }

                @Override
                public void progress(double f) {
                    progress.update(f, String.format(Locale.ROOT, "Frame %d of %d  (%.0f%%)", current[0], current[1], f * 100));
                }
            }, progress::isCancelled);
        }, frames -> Dialogs.info(getScene().getWindow(), "Recording complete",
                frames + " frames written to " + dir.getAbsolutePath()), () -> state.setPaused(wasPaused));
    }

    @Override
    public void pulse() {
        AnimationController anim = state.animation();
        timeLabel.setText(String.format(Locale.ROOT, "%s   t = %.2f s%nRecording: %.1f s = %d frames",
                anim.state(), anim.time(), anim.recordingDuration(),
                Math.round(anim.recordingDuration() * anim.settings().fps)));
    }
}

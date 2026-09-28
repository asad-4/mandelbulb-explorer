package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.rendering.RenderListener;
import com.mandelbulb.scene.FractalPreset;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * Main window: menu + tool bar on top, the 3D viewport in the centre, the tabbed parameter panel
 * on the right and a status bar at the bottom. A single {@link AnimationTimer} drives camera
 * smoothing, animation, render submission and display updates once per screen refresh.
 */
public final class MainWindow {

    private final Stage stage;
    private final ApplicationState state;
    private final ViewportPane viewport;
    private final ControlPanel controls;
    private final StatusBar status;
    private final ToggleButton pauseButton = new ToggleButton("Pause");
    private final CheckMenuItem overlayItem = new CheckMenuItem("Information overlay");
    private final CheckMenuItem pauseItem = new CheckMenuItem("Pause rendering");

    public MainWindow(Stage stage, ApplicationState state) {
        this.stage = stage;
        this.state = state;
        Runnable onChange = this::settingsChanged;
        this.viewport = new ViewportPane(state, onChange, this::togglePause);
        this.controls = new ControlPanel(state, onChange);
        this.status = new StatusBar(state);
        state.addSceneReplacedListener(controls::refreshAll);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("main-root");
        root.setTop(new VBox(menuBar(), toolBar()));
        root.setCenter(viewport);
        root.setRight(controls);
        root.setBottom(status);

        Scene scene = new Scene(root, 1480, 920);
        scene.getStylesheets().add(MainWindow.class.getResource("dark.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Mandelbulb Explorer");
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F5) {
                renderNow();
                e.consume();
            } else if (e.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                e.consume();
            }
        });
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        state.engine().addListener(new RenderListener() {
            @Override
            public void renderFailed(Throwable error) {
                Platform.runLater(() -> {
                    state.setPaused(true);
                    Dialogs.error(stage, "Rendering failed", error.getMessage());
                });
            }
        });

        SnapshotHook.install(scene, controls::selectTab, name -> {
            FractalPreset p = FractalPreset.find(name);
            if (p != null) state.applyPreset(p);
        }, () -> state.animation().play());

        new AnimationTimer() {
            private long last;
            private long lastPanelPulse;

            @Override
            public void handle(long now) {
                double dt = last == 0 ? 1 / 60.0 : (now - last) / 1e9;
                last = now;
                state.pulse(dt);
                viewport.update(dt);
                status.update();
                if (now - lastPanelPulse > 200_000_000L) {
                    lastPanelPulse = now;
                    controls.pulse();
                    pauseButton.setSelected(state.isPaused());
                    pauseItem.setSelected(state.isPaused());
                    overlayItem.setSelected(state.isOverlayVisible());
                }
            }
        }.start();
    }

    public void show() {
        stage.show();
        viewport.requestFocus();
    }

    private void settingsChanged() {
        state.changed();
        controls.refreshAll();
    }

    private void togglePause() {
        state.setPaused(!state.isPaused());
        pauseButton.setSelected(state.isPaused());
    }

    private void renderNow() {
        state.setPaused(false);
        state.requestRender();
    }

    private ToolBar toolBar() {
        Button render = new Button("Render");
        render.setTooltip(new Tooltip("Restart rendering (F5)"));
        render.setOnAction(e -> renderNow());
        pauseButton.setTooltip(new Tooltip("Stop refining and keep the current image (P)"));
        pauseButton.setOnAction(e -> state.setPaused(pauseButton.isSelected()));
        Button reset = new Button("Reset Camera");
        reset.setOnAction(e -> state.camera().reset());
        Button save = new Button("Save Image");
        save.setTooltip(new Tooltip("Render off-screen at any resolution and save as PNG/JPEG (Ctrl+S)"));
        save.setOnAction(e -> ExportDialog.show(stage, state));

        ComboBox<FractalPreset> presets = new ComboBox<>();
        presets.getItems().setAll(FractalPreset.builtIns());
        presets.setPromptText("Preset…");
        presets.setPrefWidth(190);
        presets.valueProperty().addListener((o, a, p) -> {
            if (p != null) state.applyPreset(p);
        });

        ComboBox<Integer> ss = new ComboBox<>();
        ss.getItems().setAll(List.of(1, 2, 3, 4));
        ss.setValue(state.scene().render.supersampling);
        ss.setTooltip(new Tooltip("Supersampling of the final pass (s × s samples per pixel)"));
        ss.valueProperty().addListener((o, a, v) -> {
            if (v != null && v != state.scene().render.supersampling) {
                state.scene().render.supersampling = v;
                settingsChanged();
            }
        });
        state.addSceneReplacedListener(() -> ss.setValue(state.scene().render.supersampling));

        ToggleButton overlay = new ToggleButton("Overlay");
        overlay.setSelected(state.isOverlayVisible());
        overlay.setOnAction(e -> state.setOverlayVisible(overlay.isSelected()));
        Button math = new Button("Mathematics");
        math.setOnAction(e -> controls.showMathematics());

        return new ToolBar(render, pauseButton, reset, save, new Separator(),
                new Label("Preset"), presets, new Label("Supersampling"), ss, new Separator(), overlay, math);
    }

    private MenuBar menuBar() {
        MenuItem export = new MenuItem("Export Image…");
        export.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        export.setOnAction(e -> ExportDialog.show(stage, state));
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> stage.close());
        Menu file = new Menu("File", null, export, new SeparatorMenuItem(), exit);

        overlayItem.setSelected(state.isOverlayVisible());
        overlayItem.setOnAction(e -> state.setOverlayVisible(overlayItem.isSelected()));
        CheckMenuItem lightItem = new CheckMenuItem("Light indicator");
        lightItem.setSelected(state.isLightIndicatorVisible());
        lightItem.setOnAction(e -> state.setLightIndicatorVisible(lightItem.isSelected()));
        // F-key shortcuts are handled by a scene filter: macOS menus cannot use bare F-keys.
        MenuItem fullScreen = new MenuItem("Full Screen  (F11)");
        fullScreen.setOnAction(e -> stage.setFullScreen(!stage.isFullScreen()));
        MenuItem mathItem = new MenuItem("Mathematics Panel");
        mathItem.setOnAction(e -> controls.showMathematics());
        Menu view = new Menu("View", null, overlayItem, lightItem, mathItem, new SeparatorMenuItem(), fullScreen);

        MenuItem renderItem = new MenuItem("Render Now  (F5)");
        renderItem.setOnAction(e -> renderNow());
        pauseItem.setOnAction(e -> state.setPaused(pauseItem.isSelected()));
        MenuItem bench = new MenuItem("Benchmark…");
        bench.setOnAction(e -> controls.showBenchmark());
        Menu render = new Menu("Render", null, renderItem, pauseItem, new SeparatorMenuItem(), bench);

        MenuItem reset = new MenuItem("Reset Camera");
        reset.setOnAction(e -> state.camera().reset());
        Menu camera = new Menu("Camera", null, reset);
        for (int i = 1; i <= 9; i++) {
            int slot = i;
            MenuItem go = new MenuItem("Go to View " + i);
            go.setOnAction(e -> state.camera().restoreBookmark(slot));
            camera.getItems().add(go);
        }

        Menu presets = new Menu("Presets");
        for (FractalPreset p : FractalPreset.builtIns()) {
            MenuItem item = new MenuItem(p.name());
            item.setOnAction(e -> state.applyPreset(p));
            presets.getItems().add(item);
        }

        MenuItem controlsHelp = new MenuItem("Controls");
        controlsHelp.setOnAction(e -> Dialogs.info(stage, "Controls", """
                Mouse
                  Left drag            orbit around the pivot
                  Right drag           pan
                  Middle drag          fly (dolly / strafe)
                  Scroll               zoom (slows down near the surface)
                  Alt/Ctrl + left drag free look
                  Shift + left drag    move the key light
                  Ctrl + scroll        field of view
                  Double-click         orbit around the clicked surface point

                Keyboard (click the view first)
                  W A S D              fly forward / left / back / right
                  R / F                up / down
                  Q / E                roll
                  Arrow keys           orbit
                  + / -                zoom
                  Shift                move faster
                  1-9 / Ctrl+1-9       restore / save camera view
                  Home                 reset camera
                  O                    toggle overlay
                  P                    pause / resume rendering
                  F5                   render now
                  Ctrl+S               export image"""));
        MenuItem about = new MenuItem("About");
        about.setOnAction(e -> Dialogs.info(stage, "Mandelbulb Explorer", """
                An interactive CPU ray-marching renderer for the Mandelbulb fractal.

                Every pixel is computed from the escape-time iteration z → zⁿ + c in spherical
                coordinates, using a derivative-based distance estimator for sphere tracing.

                Java %s, %d CPU threads.""".formatted(System.getProperty("java.version"), Runtime.getRuntime().availableProcessors())));
        Menu help = new Menu("Help", null, controlsHelp, about);

        MenuBar bar = new MenuBar(file, view, render, camera, presets, help);
        bar.setUseSystemMenuBar(true);
        return bar;
    }
}

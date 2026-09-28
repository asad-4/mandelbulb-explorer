package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.export.ImageExporter;
import com.mandelbulb.rendering.RenderResult;
import com.mandelbulb.rendering.ResolutionPreset;
import com.mandelbulb.scene.Scene;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Export image" workflow: choose resolution / supersampling / format, choose a file, then
 * re-render the current view off-screen at that quality. The UI itself is never captured.
 */
final class ExportDialog {

    private static File lastDirectory;

    private ExportDialog() {
    }

    static void show(Window owner, ApplicationState state) {
        int[] current = state.renderSize();
        List<String> sizes = new ArrayList<>();
        sizes.add("Current render (" + current[0] + " × " + current[1] + ")");
        for (ResolutionPreset p : ResolutionPreset.values()) sizes.add(p.toString());
        sizes.add("Custom");

        ComboBox<String> size = new ComboBox<>();
        size.getItems().setAll(sizes);
        size.setValue(ResolutionPreset.FULL_HD_1080.toString());
        TextField width = new TextField("1920");
        TextField height = new TextField("1080");
        width.setPrefColumnCount(6);
        height.setPrefColumnCount(6);
        size.valueProperty().addListener((o, a, v) -> {
            int idx = sizes.indexOf(v);
            boolean custom = idx == sizes.size() - 1;
            width.setDisable(!custom);
            height.setDisable(!custom);
            if (idx == 0) {
                width.setText(Integer.toString(current[0]));
                height.setText(Integer.toString(current[1]));
            } else if (!custom) {
                ResolutionPreset p = ResolutionPreset.values()[idx - 1];
                width.setText(Integer.toString(p.width));
                height.setText(Integer.toString(p.height));
            }
        });
        width.setDisable(true);
        height.setDisable(true);
        ComboBox<Integer> ss = new ComboBox<>();
        ss.getItems().setAll(1, 2, 3, 4);
        ss.setValue(2);
        ComboBox<ImageExporter.Format> format = new ComboBox<>();
        format.getItems().setAll(ImageExporter.Format.values());
        format.setValue(ImageExporter.Format.PNG);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Resolution"), size);
        grid.addRow(1, new Label("Width"), width);
        grid.addRow(2, new Label("Height"), height);
        grid.addRow(3, new Label("Supersampling"), ss);
        grid.addRow(4, new Label("Format"), format);
        grid.add(BoundControls.hint("The image is re-rendered off-screen at full quality with the current camera and settings."), 0, 5, 2, 1);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Export image");
        dialog.setHeaderText("Export the rendered fractal");
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        if (owner != null && owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
        }
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        int w, h;
        try {
            w = Integer.parseInt(width.getText().trim());
            h = Integer.parseInt(height.getText().trim());
            if (w < 16 || h < 16 || w > 16384 || h > 16384) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            Dialogs.error(owner, "Invalid resolution", "Width and height must be whole numbers between 16 and 16384.");
            return;
        }
        exportTo(owner, state, w, h, ss.getValue(), format.getValue());
    }

    /** Asks for a file name and renders. */
    static void exportTo(Window owner, ApplicationState state, int w, int h, int ss, ImageExporter.Format fmt) {
        long samples = (long) w * h * ss * ss;
        if (samples > 60_000_000L && !Dialogs.confirm(owner, "Expensive render",
                String.format(Locale.ROOT, "%,d samples (%d × %d at %d×%d supersampling) may take several minutes. Continue?",
                        samples, w, h, ss, ss))) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save image");
        chooser.setInitialFileName(ImageExporter.defaultFileName(w, h, fmt));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(fmt.description, "*." + fmt.extension));
        if (lastDirectory != null && lastDirectory.isDirectory()) chooser.setInitialDirectory(lastDirectory);
        File chosen = chooser.showSaveDialog(owner);
        if (chosen == null) return;
        File file = ImageExporter.withExtension(chosen, fmt);
        lastDirectory = file.getParentFile();

        Scene snapshot = state.snapshot();
        boolean wasPaused = state.isPaused();
        state.setPaused(true);
        Dialogs.runWithProgress(owner, "Export", progress ->
                ImageExporter.renderAndSave(state.engine(), snapshot, w, h, ss, file, fmt,
                        f -> progress.update(f, String.format(Locale.ROOT, "Rendering %d × %d … %.0f%%", w, h, f * 100)),
                        progress::isCancelled),
                (RenderResult r) -> Dialogs.info(owner, "Export complete", String.format(Locale.ROOT,
                        "Saved %s%n%d × %d, %d×%d supersampling, rendered in %.2f s.",
                        file.getAbsolutePath(), w, h, ss, ss, r.stats().seconds())),
                () -> state.setPaused(wasPaused));
    }
}

package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.fractal.TrapType;
import com.mandelbulb.math.Color3;
import com.mandelbulb.rendering.ColorMode;
import com.mandelbulb.rendering.Palette;
import com.mandelbulb.scene.ColorSettings;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

import static com.mandelbulb.ui.BoundControls.hint;
import static com.mandelbulb.ui.BoundControls.row;
import static com.mandelbulb.ui.BoundControls.section;

/** Palettes, colour modes and orbit traps. */
final class ColoringPanel extends SettingsPanel {

    private final Canvas preview = new Canvas(320, 18);

    ColoringPanel(ApplicationState state, Runnable onChange) {
        super(state, onChange);
        ColorSettings c = state.scene().color;

        ComboBox<Palette> palette = controls.combo(() -> c.palette, v -> c.palette = v, Palette.values());
        palette.setCellFactory(list -> new PaletteCell());
        palette.setButtonCell(new PaletteCell());
        controls.addRefresher(this::drawPreview);

        content.getChildren().add(section("Palette",
                row("Palette", palette),
                preview,
                row("Colour mode", controls.combo(() -> c.mode, v -> c.mode = v, ColorMode.values())),
                controls.slider("Offset", 0, 2, 3, "Shifts the palette (animatable)", () -> c.paletteOffset, v -> c.paletteOffset = v),
                controls.logSlider("Frequency", 0.05, 20, 2, "How many times the palette repeats over the value range", () -> c.paletteScale, v -> c.paletteScale = v),
                controls.check("Reverse palette", null, () -> c.reversePalette, v -> c.reversePalette = v),
                controls.slider("Saturation", 0, 2, 2, null, () -> c.saturation, v -> c.saturation = v),
                controls.slider("Brightness", 0.1, 3, 2, "Albedo multiplier", () -> c.brightness, v -> c.brightness = v)));

        content.getChildren().add(section("Orbit traps",
                controls.check("Orbit-trap colouring", "Blend in colour from the orbit's closest approach to a primitive",
                        () -> c.orbitTrapEnabled, v -> c.orbitTrapEnabled = v),
                row("Trap", controls.combo(() -> c.trapType, v -> c.trapType = v, TrapType.values())),
                controls.slider("Blend", 0, 1, 2, "Mix of trap colour over the colour mode", () -> c.trapBlend, v -> c.trapBlend = v),
                controls.logSlider("Trap scale", 0.05, 20, 2, "Sensitivity of the trap distance → palette mapping", () -> c.trapScale, v -> c.trapScale = v),
                hint("An orbit trap records min distance(z[k], primitive) over the orbit z[0], z[1], z[2], … "
                        + "Neighbouring surface points have similar orbits, so the minima vary smoothly and reveal the "
                        + "internal structure of the iteration. Colour modes 'Orbit trap' and 'Orbit trap RGB' use traps directly.")));
        drawPreview();
    }

    private void drawPreview() {
        Palette p = state.scene().color.palette;
        boolean reverse = state.scene().color.reversePalette;
        GraphicsContext g = preview.getGraphicsContext2D();
        double w = preview.getWidth();
        for (int x = 0; x < w; x++) {
            double t = x / (w - 1);
            Color3 col = p.sample(reverse ? 1 - t : t);
            g.setFill(BoundControls.toFx(col));
            g.fillRect(x, 0, 1, preview.getHeight());
        }
    }

    /** Combo cell showing a small gradient swatch next to the palette name. */
    private static final class PaletteCell extends ListCell<Palette> {
        @Override
        protected void updateItem(Palette item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Canvas swatch = new Canvas(48, 12);
            GraphicsContext g = swatch.getGraphicsContext2D();
            for (int x = 0; x < 48; x++) {
                g.setFill(BoundControls.toFx(item.sample(x / 47.0)));
                g.fillRect(x, 0, 1, 12);
            }
            setGraphic(swatch);
            setText(item.toString());
        }
    }
}
